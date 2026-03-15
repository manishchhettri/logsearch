package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LuceneIndexService {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexService.class);

    private final LogSearchProperties properties;
    private final Map<String, IndexWriter> indexWriters = new ConcurrentHashMap<>();
    private final CodeAnalyzer analyzer = new CodeAnalyzer();

    // Locks per date to prevent concurrent IndexWriter creation for the same date
    private final Map<String, Object> dateLocks = new ConcurrentHashMap<>();

    public LuceneIndexService(LogSearchProperties properties) {
        this.properties = properties;
        initializeIndexDirectory();
    }

    private void initializeIndexDirectory() {
        try {
            Path indexPath = Paths.get(properties.getIndexDir());
            if (!Files.exists(indexPath)) {
                Files.createDirectories(indexPath);
                log.info("Created index directory: {}", indexPath);
            }
        } catch (IOException e) {
            log.error("Failed to create index directory", e);
            throw new RuntimeException("Failed to initialize index directory", e);
        }
    }

    public void indexLogEntry(LogEntry entry) throws IOException {
        String date = entry.getTimestamp().toLocalDate().format(DateTimeFormatter.ISO_DATE);
        IndexWriter writer = getOrCreateIndexWriter(date);

        Document doc = new Document();

        // Store timestamp as both stored field and point for range queries
        doc.add(new LongPoint("timestamp", entry.getTimestamp().toInstant().toEpochMilli()));
        doc.add(new StoredField("timestamp", entry.getTimestamp().toInstant().toEpochMilli()));

        // Store date for easier retrieval
        doc.add(new StringField("date", date, Field.Store.YES));

        // Level field - searchable and stored (INFO, WARN, ERROR, etc.)
        if (entry.getLevel() != null && !entry.getLevel().isEmpty()) {
            doc.add(new TextField("level", entry.getLevel(), Field.Store.YES));
        }

        // Thread field - searchable and stored
        if (entry.getThread() != null && !entry.getThread().isEmpty()) {
            doc.add(new TextField("thread", entry.getThread(), Field.Store.YES));
        }

        // Logger field - searchable and stored (class/component name)
        if (entry.getLogger() != null && !entry.getLogger().isEmpty()) {
            doc.add(new TextField("logger", entry.getLogger(), Field.Store.YES));
        }

        // User field - searchable and stored
        if (entry.getUser() != null && !entry.getUser().isEmpty()) {
            doc.add(new TextField("user", entry.getUser(), Field.Store.YES));
        }

        // Message - main searchable content
        doc.add(new TextField("message", entry.getMessage(), Field.Store.YES));

        // Extract and index pattern for fingerprinting/analytics
        String pattern = PatternExtractor.extractPattern(entry.getMessage());
        if (PatternExtractor.isMeaningfulPattern(pattern)) {
            doc.add(new StringField("pattern", pattern, Field.Store.YES));
            doc.add(new TextField("patternText", pattern, Field.Store.NO)); // For searching
        }

        // Source file and line number for reference
        doc.add(new StringField("sourceFile", entry.getSourceFile(), Field.Store.YES));
        doc.add(new LongPoint("lineNumber", entry.getLineNumber()));
        doc.add(new StoredField("lineNumber", entry.getLineNumber()));

        // Multi-index support fields
        if (entry.getIndexName() != null && !entry.getIndexName().isEmpty()) {
            doc.add(new StringField("indexName", entry.getIndexName(), Field.Store.YES));
        }

        if (entry.getEnvironment() != null && !entry.getEnvironment().isEmpty()) {
            doc.add(new StringField("environment", entry.getEnvironment(), Field.Store.YES));
        }

        // Integration platform support fields
        if (entry.getIntegrationPlatform() != null && !entry.getIntegrationPlatform().isEmpty()) {
            doc.add(new StringField("integrationPlatform", entry.getIntegrationPlatform(), Field.Store.YES));
        }

        if (entry.getCorrelationId() != null && !entry.getCorrelationId().isEmpty()) {
            doc.add(new StringField("correlationId", entry.getCorrelationId(), Field.Store.YES));
            doc.add(new TextField("correlationIdText", entry.getCorrelationId(), Field.Store.NO)); // For searching
        }

        if (entry.getMessageId() != null && !entry.getMessageId().isEmpty()) {
            doc.add(new StringField("messageId", entry.getMessageId(), Field.Store.YES));
            doc.add(new TextField("messageIdText", entry.getMessageId(), Field.Store.NO)); // For searching
        }

        if (entry.getFlowName() != null && !entry.getFlowName().isEmpty()) {
            doc.add(new StringField("flowName", entry.getFlowName(), Field.Store.YES));
            doc.add(new TextField("flowNameText", entry.getFlowName(), Field.Store.NO)); // For searching
        }

        if (entry.getEndpoint() != null && !entry.getEndpoint().isEmpty()) {
            doc.add(new StringField("endpoint", entry.getEndpoint(), Field.Store.YES));
            doc.add(new TextField("endpointText", entry.getEndpoint(), Field.Store.NO)); // For searching
        }

        // Chunk ID field (for metadata-first search architecture)
        if (entry.getChunkId() != null && !entry.getChunkId().isEmpty()) {
            doc.add(new StringField("chunkId", entry.getChunkId(), Field.Store.YES));
        }

        writer.addDocument(doc);
    }

    /**
     * Search a specific chunk by ID (for metadata-first search architecture)
     */
    public List<LogEntry> searchChunk(String chunkId, String queryString, int limit)
            throws IOException, ParseException {

        List<LogEntry> results = new ArrayList<>();

        // Extract date from chunkId (format: service::date::chunk-HH)
        String[] parts = chunkId.split("::");
        if (parts.length < 2) {
            log.warn("Invalid chunkId format: {}", chunkId);
            return results;
        }

        String date = parts[1];  // Extract date part
        Path indexPath = Paths.get(properties.getIndexDir(), date);

        if (!Files.exists(indexPath)) {
            log.debug("Index not found for chunk {}", chunkId);
            return results;
        }

        try (Directory directory = FSDirectory.open(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);

            // Build query: combine chunk filter with user query
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

            // Chunk ID filter (required)
            queryBuilder.add(new TermQuery(new Term("chunkId", chunkId)), BooleanClause.Occur.MUST);

            // Text search query (if provided)
            if (queryString != null && !queryString.trim().isEmpty()) {
                String[] fields = {"message", "user", "level", "thread", "logger", "patternText",
                                   "correlationIdText", "messageIdText", "flowNameText", "endpointText"};
                MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);
                Query textQuery = parser.parse(queryString);
                queryBuilder.add(textQuery, BooleanClause.Occur.MUST);
            }

            Query finalQuery = queryBuilder.build();

            // Execute search
            TopDocs topDocs = searcher.search(finalQuery, limit);

            // Collect results
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                results.add(documentToLogEntry(doc));
            }

        } catch (Exception e) {
            log.error("Error searching chunk: {}", chunkId, e);
        }

        return results;
    }

    private LogEntry documentToLogEntry(Document doc) {
        long timestampMs = doc.getField("timestamp").numericValue().longValue();
        ZonedDateTime timestamp = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestampMs),
                ZoneId.of(properties.getTimezone())
        );

        return LogEntry.builder()
                .timestamp(timestamp)
                .level(doc.get("level"))
                .thread(doc.get("thread"))
                .logger(doc.get("logger"))
                .user(doc.get("user"))
                .message(doc.get("message"))
                .sourceFile(doc.get("sourceFile"))
                .lineNumber(doc.getField("lineNumber").numericValue().longValue())
                .indexName(doc.get("indexName"))
                .environment(doc.get("environment"))
                .chunkId(doc.get("chunkId"))
                .correlationId(doc.get("correlationId"))
                .messageId(doc.get("messageId"))
                .flowName(doc.get("flowName"))
                .endpoint(doc.get("endpoint"))
                .integrationPlatform(doc.get("integrationPlatform"))
                .build();
    }

    private IndexWriter getOrCreateIndexWriter(String date) throws IOException {
        // Get or create a lock object for this specific date
        Object dateLock = dateLocks.computeIfAbsent(date, k -> new Object());

        // Synchronize on the date-specific lock to prevent concurrent IndexWriter creation
        synchronized (dateLock) {
            // Check if IndexWriter already exists (double-checked locking pattern)
            IndexWriter existingWriter = indexWriters.get(date);
            if (existingWriter != null) {
                return existingWriter;
            }

            // Create new IndexWriter
            try {
                Path indexPath = Paths.get(properties.getIndexDir(), date);
                Files.createDirectories(indexPath);

                Directory directory = FSDirectory.open(indexPath);
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

                IndexWriter writer = new IndexWriter(directory, config);
                indexWriters.put(date, writer);
                log.info("Created/opened index writer for date: {}", date);
                return writer;

            } catch (IOException e) {
                log.error("Failed to create index writer for date: {}", date, e);
                throw new RuntimeException("Failed to create index writer", e);
            }
        }
    }

    public void commit() throws IOException {
        for (Map.Entry<String, IndexWriter> entry : indexWriters.entrySet()) {
            entry.getValue().commit();
            log.debug("Committed index for date: {}", entry.getKey());
        }
    }

    public void closeAll() {
        for (Map.Entry<String, IndexWriter> entry : indexWriters.entrySet()) {
            try {
                entry.getValue().close();
                log.info("Closed index writer for date: {}", entry.getKey());
            } catch (IOException e) {
                log.error("Failed to close index writer for date: {}", entry.getKey(), e);
            }
        }
        indexWriters.clear();
    }

    public void deleteOldIndexes() throws IOException {
        if (properties.getRetentionDays() <= 0) {
            return;
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(properties.getRetentionDays());
        Path indexDir = Paths.get(properties.getIndexDir());

        if (!Files.exists(indexDir)) {
            return;
        }

        Files.list(indexDir)
                .filter(Files::isDirectory)
                .forEach(path -> {
                    try {
                        String dirName = path.getFileName().toString();

                        // Skip non-date directories (like "metadata")
                        if (!dirName.matches("\\d{4}-\\d{2}-\\d{2}")) {
                            log.debug("Skipping non-date directory: {}", dirName);
                            return;
                        }

                        LocalDate indexDate = LocalDate.parse(dirName, DateTimeFormatter.ISO_DATE);

                        if (indexDate.isBefore(cutoffDate)) {
                            // Close writer if open
                            IndexWriter writer = indexWriters.remove(dirName);
                            if (writer != null) {
                                writer.close();
                            }

                            // Delete directory
                            deleteDirectory(path);
                            log.info("Deleted old index: {}", dirName);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process/delete index directory: {}", path, e);
                    }
                });
    }

    public void deleteAllIndexes() throws IOException {
        log.info("Deleting ALL indexes...");

        // Close all index writers first
        closeAll();

        Path indexDir = Paths.get(properties.getIndexDir());

        if (!Files.exists(indexDir)) {
            log.warn("Index directory does not exist: {}", indexDir);
            return;
        }

        // Delete all subdirectories (each date's index) BUT skip metadata directory
        Files.list(indexDir)
                .filter(Files::isDirectory)
                .filter(path -> !path.getFileName().toString().equals("metadata")) // Skip metadata directory
                .forEach(path -> {
                    try {
                        deleteDirectory(path);
                        log.info("Deleted index: {}", path.getFileName());
                    } catch (IOException e) {
                        log.error("Failed to delete index directory: {}", path, e);
                    }
                });

        log.info("All indexes deleted (metadata preserved)");
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete: {}", p, e);
                    }
                });
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Lucene index service");
        closeAll();
    }
}
