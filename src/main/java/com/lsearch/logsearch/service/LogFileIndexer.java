package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.Chunk;
import com.lsearch.logsearch.model.ChunkIdentifier;
import com.lsearch.logsearch.model.ChunkMetadata;
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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ChunkingStrategy chunkingStrategy;
    private final ChunkMetadataExtractor chunkMetadataExtractor;
    private final MetadataIndexService metadataIndexService;

    // Thread-safe set for parallel indexing (tracks absolute paths)
    private final Set<String> indexedFiles = ConcurrentHashMap.newKeySet();

    // Content-based deduplication (tracks filename + size to detect duplicates in different directories)
    private final Set<String> indexedFileSignatures = ConcurrentHashMap.newKeySet();

    public LogFileIndexer(LogSearchProperties properties, LogParser logParser,
                          LuceneIndexService indexService, IndexConfigService indexConfigService,
                          IntegrationMetadataExtractor integrationExtractor,
                          ChunkingStrategy chunkingStrategy,
                          ChunkMetadataExtractor chunkMetadataExtractor,
                          MetadataIndexService metadataIndexService) {
        this.properties = properties;
        this.logParser = logParser;
        this.indexService = indexService;
        this.indexConfigService = indexConfigService;
        this.integrationExtractor = integrationExtractor;
        this.chunkingStrategy = chunkingStrategy;
        this.chunkMetadataExtractor = chunkMetadataExtractor;
        this.metadataIndexService = metadataIndexService;
    }

    @PostConstruct
    public void initialize() {
        log.info("Log File Indexer initialized");
        log.info("Logs directory: {}", properties.getLogsDir());
        log.info("Index directory: {}", properties.getIndexDir());
    }

    public void indexAllLogs() throws IOException {
        log.info("Starting multi-index indexing...");
        boolean chunkingEnabled = properties.getChunking() != null && properties.getChunking().isEnabled();

        if (chunkingEnabled) {
            log.info("Chunking ENABLED - metadata-first search architecture active");
        } else {
            log.info("Chunking DISABLED - using standard indexing");
        }

        for (IndexConfig indexConfig : indexConfigService.getEnabledIndexes()) {
            log.info("Indexing: {} ({}) [environment: {}]",
                    indexConfig.getDisplayName(), indexConfig.getName(), indexConfig.getEnvironment());
            indexIndex(indexConfig);
        }

        // Commit all index writers
        indexService.commit();

        // Commit metadata index if chunking is enabled
        if (chunkingEnabled && metadataIndexService != null) {
            metadataIndexService.commit();
            log.info("Metadata index committed. Total chunks: {}", metadataIndexService.getTotalChunkCount());
        }

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
        AtomicInteger skippedFiles = new AtomicInteger(0);

        // Step 1: Collect all matching files
        List<Path> allFiles;
        try (Stream<Path> paths = Files.walk(logsDir)) {
            allFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> filePattern.matcher(path.getFileName().toString()).matches())
                    .collect(java.util.stream.Collectors.toList());
        }

        log.info("Found {} files matching pattern for index: {}", allFiles.size(), indexConfig.getName());

        // Step 2: Deduplicate by file signature (filename + size) BEFORE parallel processing
        // Keep only the first occurrence of each unique file signature
        Map<String, Path> uniqueFiles = new LinkedHashMap<>();
        int alreadyIndexedCount = 0;

        for (Path path : allFiles) {
            String absolutePath = path.toAbsolutePath().normalize().toString();

            // Skip if already indexed (by absolute path)
            if (indexedFiles.contains(absolutePath)) {
                alreadyIndexedCount++;
                log.debug("Skipping already-indexed file: {} at {}", path.getFileName(), absolutePath);
                continue;
            }

            String fileSignature = getFileSignature(path);

            // Keep first occurrence, skip duplicates
            if (!uniqueFiles.containsKey(fileSignature)) {
                uniqueFiles.put(fileSignature, path);
            } else {
                log.info("Skipping duplicate file (same name and size): {} at {}",
                        path.getFileName().toString(), absolutePath);
                skippedFiles.incrementAndGet();
            }
        }

        log.info("After deduplication: {} unique files to index, {} duplicates skipped, {} already indexed",
                uniqueFiles.size(), skippedFiles.get(), alreadyIndexedCount);

        // Step 3: Process only the deduplicated files in parallel
        uniqueFiles.values().parallelStream()
                .forEach(path -> {
                    String absolutePath = path.toAbsolutePath().normalize().toString();
                    String fileSignature = getFileSignature(path);

                    // Mark as indexed (thread-safe) - use normalized paths
                    indexedFiles.add(absolutePath);
                    indexedFileSignatures.add(fileSignature);

                    // Index the file
                    indexLogFile(path, indexConfig);
                    processedFiles.incrementAndGet();
                });

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

            // Commit metadata index if chunking is enabled
            boolean chunkingEnabled = properties.getChunking() != null && properties.getChunking().isEnabled();
            if (chunkingEnabled && metadataIndexService != null) {
                metadataIndexService.commit();
            }

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
     * Generate a unique signature for a file based on filename and size
     * This helps detect duplicate files in different directories (e.g., logs/ and logs/archive/)
     */
    private String getFileSignature(Path logFile) {
        try {
            String filename = logFile.getFileName().toString();
            long size = Files.size(logFile);
            return filename + ":" + size;
        } catch (IOException e) {
            // Fallback to filename only if size cannot be determined
            return logFile.getFileName().toString();
        }
    }

    /**
     * Index a single log file with index context
     * NOTE: Caller must ensure this file is not a duplicate before calling
     */
    private void indexLogFile(Path logFile, IndexConfig indexConfig) {
        String filename = logFile.getFileName().toString();
        // Normalize path to remove ./ and ../ components for clean absolute paths
        String absolutePath = logFile.toAbsolutePath().normalize().toString();
        String fileSignature = getFileSignature(logFile);

        log.info("Indexing log file: {} (index: {}, environment: {})",
                filename, indexConfig.getName(), indexConfig.getEnvironment());

        boolean chunkingEnabled = properties.getChunking() != null && properties.getChunking().isEnabled();

        // Use absolute path as sourceFile to avoid confusion when same file is in multiple locations
        if (chunkingEnabled) {
            indexLogFileWithChunking(logFile, indexConfig, absolutePath, absolutePath, fileSignature);
        } else {
            indexLogFileStandard(logFile, indexConfig, absolutePath, absolutePath, fileSignature);
        }
    }

    /**
     * Standard indexing (without chunking) - entry by entry
     */
    private void indexLogFileStandard(Path logFile, IndexConfig indexConfig, String filename, String absolutePath, String fileSignature) {
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
                    parsedEntry = logParser.parseLine(line, filename, lineNumber.get(), logFile);
                } else if (isContinuation && currentEntry != null) {
                    if (continuationLines.length() > 0) {
                        continuationLines.append("\n");
                    }
                    continuationLines.append(line);
                    continue;
                } else if (properties.isEnableFallback()) {
                    parsedEntry = logParser.parseLine(line, filename, lineNumber.get(), logFile);
                } else {
                    skippedLines.incrementAndGet();
                    continue;
                }

                if (parsedEntry != null) {
                    if (currentEntry != null) {
                        try {
                            if (continuationLines.length() > 0) {
                                String fullMessage = currentEntry.getMessage() + "\n" + continuationLines.toString();
                                currentEntry.setMessage(fullMessage);
                                continuationLines.setLength(0);
                            }
                            currentEntry.setIndexName(indexConfig.getName());
                            currentEntry.setEnvironment(indexConfig.getEnvironment());
                            integrationExtractor.enrichLogEntry(currentEntry);
                            indexService.indexLogEntry(currentEntry);
                            indexedLines.incrementAndGet();
                        } catch (IOException e) {
                            log.error("Failed to index line {} in {}: {}", currentEntry.getLineNumber(), filename, e.getMessage());
                            skippedLines.incrementAndGet();
                        }
                    }
                    currentEntry = parsedEntry;
                }

                if (indexedLines.get() % 10000 == 0 && indexedLines.get() > 0) {
                    indexService.commit();
                }
            }

            // Index the last entry
            if (currentEntry != null) {
                try {
                    if (continuationLines.length() > 0) {
                        String fullMessage = currentEntry.getMessage() + "\n" + continuationLines.toString();
                        currentEntry.setMessage(fullMessage);
                    }
                    currentEntry.setIndexName(indexConfig.getName());
                    currentEntry.setEnvironment(indexConfig.getEnvironment());
                    integrationExtractor.enrichLogEntry(currentEntry);
                    indexService.indexLogEntry(currentEntry);
                    indexedLines.incrementAndGet();
                } catch (IOException e) {
                    log.error("Failed to index last line in {}: {}", filename, e.getMessage());
                    skippedLines.incrementAndGet();
                }
            }

            indexService.commit();

            log.info("Completed indexing {}: {} total lines, {} indexed entries, {} skipped lines",
                    filename, lineNumber.get(), indexedLines.get(), skippedLines.get());

        } catch (IOException e) {
            log.error("Failed to index file: {}", filename, e);
        }
    }

    /**
     * Chunking-aware indexing - groups entries into chunks with metadata
     */
    private void indexLogFileWithChunking(Path logFile, IndexConfig indexConfig, String filename, String absolutePath, String fileSignature) {
        AtomicLong lineNumber = new AtomicLong(0);
        AtomicLong skippedLines = new AtomicLong(0);

        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            LogEntry currentEntry = null;
            StringBuilder continuationLines = new StringBuilder();
            List<LogEntry> allEntries = new ArrayList<>();

            // First pass: parse all log entries from file
            while ((line = reader.readLine()) != null) {
                lineNumber.incrementAndGet();

                boolean isNewEntry = logParser.matchesPrimaryPattern(line);
                boolean isContinuation = logParser.looksLikeContinuationLine(line);

                LogEntry parsedEntry = null;

                if (isNewEntry) {
                    parsedEntry = logParser.parseLine(line, filename, lineNumber.get(), logFile);
                } else if (isContinuation && currentEntry != null) {
                    if (continuationLines.length() > 0) {
                        continuationLines.append("\n");
                    }
                    continuationLines.append(line);
                    continue;
                } else if (properties.isEnableFallback()) {
                    parsedEntry = logParser.parseLine(line, filename, lineNumber.get(), logFile);
                } else {
                    skippedLines.incrementAndGet();
                    continue;
                }

                if (parsedEntry != null) {
                    if (currentEntry != null) {
                        if (continuationLines.length() > 0) {
                            String fullMessage = currentEntry.getMessage() + "\n" + continuationLines.toString();
                            currentEntry.setMessage(fullMessage);
                            continuationLines.setLength(0);
                        }
                        currentEntry.setIndexName(indexConfig.getName());
                        currentEntry.setEnvironment(indexConfig.getEnvironment());
                        integrationExtractor.enrichLogEntry(currentEntry);
                        allEntries.add(currentEntry);
                    }
                    currentEntry = parsedEntry;
                }
            }

            // Add last entry
            if (currentEntry != null) {
                if (continuationLines.length() > 0) {
                    String fullMessage = currentEntry.getMessage() + "\n" + continuationLines.toString();
                    currentEntry.setMessage(fullMessage);
                }
                currentEntry.setIndexName(indexConfig.getName());
                currentEntry.setEnvironment(indexConfig.getEnvironment());
                integrationExtractor.enrichLogEntry(currentEntry);
                allEntries.add(currentEntry);
            }

            log.info("Parsed {} entries from {}, creating chunks...", allEntries.size(), filename);

            // Second pass: create chunks and index with metadata
            String serviceName = extractServiceName(indexConfig.getName());
            List<Chunk> chunks = chunkingStrategy.createChunks(allEntries);

            log.info("Created {} chunks for {}", chunks.size(), filename);

            // Assign identifiers to chunks
            int sequenceNumber = 0;
            for (Chunk chunk : chunks) {
                if (chunk.isEmpty()) {
                    continue;
                }

                // Get first entry timestamp to determine chunk hour
                ZonedDateTime firstTimestamp = chunk.getEntries().get(0).getTimestamp();
                ChunkIdentifier identifier = ChunkIdentifier.adaptive(
                    serviceName,
                    firstTimestamp.toLocalDate(),
                    firstTimestamp.getHour(),
                    sequenceNumber++
                );
                chunk.setIdentifier(identifier);

                // Set start/end times if not already set
                if (chunk.getStartTime() == null) {
                    chunk.setStartTime(firstTimestamp);
                }
                if (chunk.getEndTime() == null) {
                    chunk.setEndTime(chunk.getEntries().get(chunk.getEntries().size() - 1).getTimestamp());
                }
            }

            // Index chunks with metadata
            for (Chunk chunk : chunks) {
                if (chunk.isEmpty() || chunk.getIdentifier() == null) {
                    continue;
                }

                try {
                    String chunkId = chunk.getIdentifier().getChunkId();

                    // Extract chunk metadata
                    ChunkMetadata metadata = chunkMetadataExtractor.extractMetadata(chunkId, chunk.getEntries());

                    // Index metadata in metadata index
                    metadataIndexService.indexChunkMetadata(metadata);

                    // Set chunkId on all entries and index them
                    for (LogEntry entry : chunk.getEntries()) {
                        entry.setChunkId(chunkId);
                        indexService.indexLogEntry(entry);
                    }

                    log.debug("Indexed chunk {} with {} entries", chunkId, chunk.getEntries().size());
                } catch (IOException e) {
                    log.error("Failed to index chunk for {}: {}", filename, e.getMessage());
                }
            }

            indexService.commit();

            log.info("Completed chunked indexing {}: {} total lines, {} entries, {} chunks",
                    filename, lineNumber.get(), allEntries.size(), chunks.size());

        } catch (IOException e) {
            log.error("Failed to index file: {}", filename, e);
        }
    }

    /**
     * Extract service name from index name or use default
     */
    private String extractServiceName(String indexName) {
        if (indexName == null || indexName.isEmpty()) {
            return properties.getServiceName();
        }

        // Try to extract service name using pattern
        if (properties.getServiceNamePattern() != null) {
            Pattern pattern = Pattern.compile(properties.getServiceNamePattern());
            java.util.regex.Matcher matcher = pattern.matcher(indexName);
            if (matcher.find() && matcher.groupCount() >= 1) {
                return matcher.group(1);
            }
        }

        // Default to index name
        return indexName;
    }

    public void fullReindex() throws IOException {
        log.info("Starting FULL re-index (deleting existing indexes)...");

        // Clear tracked files and signatures so everything gets re-indexed
        indexedFiles.clear();
        indexedFileSignatures.clear();

        // Delete all existing indexes
        indexService.deleteAllIndexes();

        // Clear metadata index if chunking is enabled
        boolean chunkingEnabled = properties.getChunking() != null && properties.getChunking().isEnabled();
        if (chunkingEnabled && metadataIndexService != null) {
            metadataIndexService.clearMetadataIndex();
            log.info("Metadata index cleared");
        }

        // Re-index everything
        indexAllLogs();

        log.info("Full re-index completed");
    }

    public int getIndexedFileCount() {
        return indexedFiles.size();
    }
}
