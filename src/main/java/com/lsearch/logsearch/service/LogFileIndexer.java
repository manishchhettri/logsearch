package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.IndexConfig;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class LogFileIndexer {

    private static final Logger log = LoggerFactory.getLogger(LogFileIndexer.class);

    private final LogSearchProperties properties;
    private final LogParser logParser;
    private final LuceneIndexService indexService;
    private final IndexConfigService indexConfigService;
    private final IntegrationMetadataExtractor integrationExtractor;

    // Thread-safe set for parallel indexing
    private final Set<String> indexedFiles = ConcurrentHashMap.newKeySet();

    public LogFileIndexer(LogSearchProperties properties, LogParser logParser,
                          LuceneIndexService indexService, IndexConfigService indexConfigService,
                          IntegrationMetadataExtractor integrationExtractor) {
        this.properties = properties;
        this.logParser = logParser;
        this.indexService = indexService;
        this.indexConfigService = indexConfigService;
        this.integrationExtractor = integrationExtractor;
    }

    @PostConstruct
    public void initialize() {
        log.info("Log File Indexer initialized");
        log.info("Logs directory: {}", properties.getLogsDir());
        log.info("Index directory: {}", properties.getIndexDir());
    }

    public void indexAllLogs() throws IOException {
        log.info("Starting multi-index indexing...");

        for (IndexConfig indexConfig : indexConfigService.getEnabledIndexes()) {
            log.info("Indexing: {} ({}) [environment: {}]",
                    indexConfig.getDisplayName(), indexConfig.getName(), indexConfig.getEnvironment());
            indexIndex(indexConfig);
        }

        // Commit all index writers
        indexService.commit();
        indexService.deleteOldIndexes();

        log.info("Multi-index indexing completed. Total files indexed: {}", indexedFiles.size());
    }

    /**
     * Index a single index (log source)
     */
    private void indexIndex(IndexConfig indexConfig) throws IOException {
        Path logsDir = Paths.get(indexConfig.getPath());

        if (!Files.exists(logsDir)) {
            log.warn("Index path does not exist, skipping: {} ({})", indexConfig.getName(), indexConfig.getPath());
            return;
        }

        Pattern filePattern = Pattern.compile(indexConfig.getFilePattern());
        AtomicInteger processedFiles = new AtomicInteger(0);

        // Parallel indexing - process multiple files concurrently
        // Uses Files.walk() for recursive subdirectory scanning
        try (Stream<Path> paths = Files.walk(logsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> filePattern.matcher(path.getFileName().toString()).matches())
                    .parallel()  // Enable parallel processing
                    .forEach(path -> {
                        indexLogFile(path, indexConfig);
                        processedFiles.incrementAndGet();
                    });
        }

        log.info("Indexed {} files for index: {}", processedFiles.get(), indexConfig.getName());
    }

    @Scheduled(fixedDelayString = "${log-search.watch-interval}000", initialDelay = 60000)
    public void watchForNewLogs() {
        try {
            log.debug("Checking for new log files in all indexes (recursive)...");

            for (IndexConfig indexConfig : indexConfigService.getEnabledIndexes()) {
                if (!indexConfig.isAutoWatch()) {
                    continue;
                }

                watchIndex(indexConfig);
            }

            indexService.commit();
            indexService.deleteOldIndexes();

        } catch (Exception e) {
            log.error("Error during auto-watch", e);
        }
    }

    /**
     * Watch a specific index for new files
     */
    private void watchIndex(IndexConfig indexConfig) throws IOException {
        Path logsDir = Paths.get(indexConfig.getPath());
        if (!Files.exists(logsDir)) {
            return;
        }

        Pattern filePattern = Pattern.compile(indexConfig.getFilePattern());

        // Recursive scanning of subdirectories
        try (Stream<Path> paths = Files.walk(logsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> filePattern.matcher(path.getFileName().toString()).matches())
                    .filter(path -> !indexedFiles.contains(path.toString()))
                    .forEach(path -> indexLogFile(path, indexConfig));
        }
    }

    /**
     * Index a single log file with index context
     */
    private void indexLogFile(Path logFile, IndexConfig indexConfig) {
        String filename = logFile.getFileName().toString();
        String absolutePath = logFile.toAbsolutePath().toString();

        if (indexedFiles.contains(absolutePath)) {
            log.debug("File already indexed: {}", filename);
            return;
        }

        log.info("Indexing log file: {} (index: {}, environment: {})",
                filename, indexConfig.getName(), indexConfig.getEnvironment());

        AtomicLong lineNumber = new AtomicLong(0);
        AtomicLong indexedLines = new AtomicLong(0);
        AtomicLong skippedLines = new AtomicLong(0);

        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            LogEntry currentEntry = null;
            StringBuilder continuationLines = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                lineNumber.incrementAndGet();

                // Smart multi-line detection: check primary pattern first
                boolean isNewEntry = logParser.matchesPrimaryPattern(line);
                boolean isContinuation = logParser.looksLikeContinuationLine(line);

                LogEntry parsedEntry = null;

                if (isNewEntry) {
                    // This line matches the primary pattern - it's definitely a new entry
                    parsedEntry = logParser.parseLine(line, filename, lineNumber.get(), logFile);
                } else if (isContinuation && currentEntry != null) {
                    // This looks like a continuation line and we have a current entry
                    // Append to current entry instead of parsing
                    if (continuationLines.length() > 0) {
                        continuationLines.append("\n");
                    }
                    continuationLines.append(line);
                    // Continue to next line without creating new entry
                    continue;
                } else if (properties.isEnableFallback()) {
                    // Not a primary pattern, not a continuation (or no current entry)
                    // Try fallback parsing for truly standalone lines
                    parsedEntry = logParser.parseLine(line, filename, lineNumber.get(), logFile);
                } else {
                    // No match, no fallback - skip this line
                    skippedLines.incrementAndGet();
                    continue;
                }

                // If we have a parsed entry, it's a new log entry
                if (parsedEntry != null) {
                    // Index the previous entry first (if it exists)
                    if (currentEntry != null) {
                        try {
                            // Append any continuation lines to the message
                            if (continuationLines.length() > 0) {
                                String fullMessage = currentEntry.getMessage() + "\n" + continuationLines.toString();
                                currentEntry.setMessage(fullMessage);
                                continuationLines.setLength(0);
                            }
                            // Set index metadata
                            currentEntry.setIndexName(indexConfig.getName());
                            currentEntry.setEnvironment(indexConfig.getEnvironment());
                            // Extract integration platform metadata
                            integrationExtractor.enrichLogEntry(currentEntry);
                            indexService.indexLogEntry(currentEntry);
                            indexedLines.incrementAndGet();
                        } catch (IOException e) {
                            log.error("Failed to index line {} in {}: {}", currentEntry.getLineNumber(), filename, e.getMessage());
                            skippedLines.incrementAndGet();
                        }
                    }

                    // Start tracking this new entry
                    currentEntry = parsedEntry;
                }

                // Commit every 10000 lines to avoid memory issues
                if (indexedLines.get() % 10000 == 0 && indexedLines.get() > 0) {
                    indexService.commit();
                }
            }

            // Index the last entry if it exists
            if (currentEntry != null) {
                try {
                    if (continuationLines.length() > 0) {
                        String fullMessage = currentEntry.getMessage() + "\n" + continuationLines.toString();
                        currentEntry.setMessage(fullMessage);
                    }
                    // Set index metadata
                    currentEntry.setIndexName(indexConfig.getName());
                    currentEntry.setEnvironment(indexConfig.getEnvironment());
                    // Extract integration platform metadata
                    integrationExtractor.enrichLogEntry(currentEntry);
                    indexService.indexLogEntry(currentEntry);
                    indexedLines.incrementAndGet();
                } catch (IOException e) {
                    log.error("Failed to index last line in {}: {}", filename, e.getMessage());
                    skippedLines.incrementAndGet();
                }
            }

            indexService.commit();
            indexedFiles.add(absolutePath);

            log.info("Completed indexing {}: {} total lines, {} indexed entries, {} skipped lines (multi-line messages included)",
                    filename, lineNumber.get(), indexedLines.get(), skippedLines.get());

        } catch (IOException e) {
            log.error("Failed to index file: {}", filename, e);
        }
    }

    public void fullReindex() throws IOException {
        log.info("Starting FULL re-index (deleting existing indexes)...");

        // Clear tracked files so everything gets re-indexed
        indexedFiles.clear();

        // Delete all existing indexes
        indexService.deleteAllIndexes();

        // Re-index everything
        indexAllLogs();

        log.info("Full re-index completed");
    }

    public int getIndexedFileCount() {
        return indexedFiles.size();
    }
}
