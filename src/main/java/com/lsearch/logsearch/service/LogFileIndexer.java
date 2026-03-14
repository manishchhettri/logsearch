package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class LogFileIndexer {

    private static final Logger log = LoggerFactory.getLogger(LogFileIndexer.class);

    private final LogSearchProperties properties;
    private final LogParser logParser;
    private final LuceneIndexService indexService;

    private final Set<String> indexedFiles = new HashSet<>();

    public LogFileIndexer(LogSearchProperties properties, LogParser logParser, LuceneIndexService indexService) {
        this.properties = properties;
        this.logParser = logParser;
        this.indexService = indexService;
    }

    @PostConstruct
    public void initialize() {
        log.info("Log File Indexer initialized");
        log.info("Logs directory: {}", properties.getLogsDir());
        log.info("Index directory: {}", properties.getIndexDir());
    }

    public void indexAllLogs() throws IOException {
        log.info("Starting to index all log files...");

        Path logsDir = Paths.get(properties.getLogsDir());
        if (!Files.exists(logsDir)) {
            log.warn("Logs directory does not exist: {}", logsDir);
            Files.createDirectories(logsDir);
            log.info("Created logs directory: {}", logsDir);
            return;
        }

        Pattern filePattern = Pattern.compile(properties.getFilePattern());

        try (Stream<Path> paths = Files.list(logsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> filePattern.matcher(path.getFileName().toString()).matches())
                    .forEach(this::indexLogFile);
        }

        indexService.commit();
        indexService.deleteOldIndexes();

        log.info("Indexing completed. Total files indexed: {}", indexedFiles.size());
    }

    @Scheduled(fixedDelayString = "${log-search.watch-interval}000", initialDelay = 60000)
    public void watchForNewLogs() {
        if (!properties.isAutoWatch()) {
            return;
        }

        try {
            log.debug("Checking for new log files...");

            Path logsDir = Paths.get(properties.getLogsDir());
            if (!Files.exists(logsDir)) {
                return;
            }

            Pattern filePattern = Pattern.compile(properties.getFilePattern());

            try (Stream<Path> paths = Files.list(logsDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> filePattern.matcher(path.getFileName().toString()).matches())
                        .filter(path -> !indexedFiles.contains(path.toString()))
                        .forEach(this::indexLogFile);
            }

            indexService.commit();
            indexService.deleteOldIndexes();

        } catch (Exception e) {
            log.error("Error during auto-watch", e);
        }
    }

    private void indexLogFile(Path logFile) {
        String filename = logFile.getFileName().toString();
        String absolutePath = logFile.toAbsolutePath().toString();

        if (indexedFiles.contains(absolutePath)) {
            log.debug("File already indexed: {}", filename);
            return;
        }

        log.info("Indexing log file: {}", filename);

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
                if (indexedLines.get() % 10000 == 0) {
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
