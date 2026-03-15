package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.ChunkCandidate;
import com.lsearch.logsearch.model.ChunkMetadata;
import com.lsearch.logsearch.model.ChunkQuery;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages metadata index for efficient chunk candidate selection.
 * Stores and queries chunk metadata to prune search space.
 */
@Service
public class MetadataIndexService {

    private static final Logger log = LoggerFactory.getLogger(MetadataIndexService.class);

    private final LogSearchProperties properties;
    private final BloomFilterManager bloomFilterManager;
    private IndexWriter metadataWriter;
    private IndexReader metadataReader;
    private IndexSearcher metadataSearcher;

    public MetadataIndexService(LogSearchProperties properties,
                                 BloomFilterManager bloomFilterManager) {
        this.properties = properties;
        this.bloomFilterManager = bloomFilterManager;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Path metadataDir = Paths.get(properties.getIndexDir(), "metadata", "chunks");
        Files.createDirectories(metadataDir);

        Directory directory = FSDirectory.open(metadataDir);
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        this.metadataWriter = new IndexWriter(directory, config);
        this.metadataReader = DirectoryReader.open(metadataWriter);
        this.metadataSearcher = new IndexSearcher(metadataReader);

        log.info("Metadata index initialized at: {}", metadataDir);
        log.info("Metadata index contains {} chunks", metadataReader.numDocs());
    }

    /**
     * Index chunk metadata
     */
    public void indexChunkMetadata(ChunkMetadata metadata) throws IOException {
        Document doc = metadata.toLuceneDocument();
        metadataWriter.addDocument(doc);
        log.debug("Indexed metadata for chunk: {}", metadata.getChunkId());
    }

    /**
     * Find candidate chunks matching the given criteria
     */
    public List<ChunkCandidate> findCandidateChunks(ChunkQuery chunkQuery)
            throws IOException {

        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        // Time range pruning (CRITICAL)
        if (chunkQuery.hasTimeRange()) {
            queryBuilder.add(
                LongPoint.newRangeQuery("startTime",
                    chunkQuery.getStartTimeMillis(),
                    chunkQuery.getEndTimeMillis()),
                BooleanClause.Occur.MUST
            );
        }

        // Service pruning
        if (chunkQuery.hasService()) {
            queryBuilder.add(
                new TermQuery(new Term("service", chunkQuery.getService())),
                BooleanClause.Occur.MUST
            );
        }

        // Log level pruning
        if (chunkQuery.hasLogLevel()) {
            queryBuilder.add(
                new TermQuery(new Term("logLevels", chunkQuery.getLogLevel())),
                BooleanClause.Occur.MUST
            );
        }

        // Stack trace requirement
        if (chunkQuery.requiresStackTrace()) {
            queryBuilder.add(
                new TermQuery(new Term("hasStackTrace", "true")),
                BooleanClause.Occur.MUST
            );
        }

        // Package name filtering (optional, for advanced pruning)
        if (chunkQuery.hasPackageHint()) {
            queryBuilder.add(
                new TermQuery(new Term("packages", chunkQuery.getPackageHint())),
                BooleanClause.Occur.SHOULD
            );
        }

        // Correlation ID filtering
        if (chunkQuery.getCorrelationId() != null && !chunkQuery.getCorrelationId().trim().isEmpty()) {
            queryBuilder.add(
                new TermQuery(new Term("correlationIds", chunkQuery.getCorrelationId())),
                BooleanClause.Occur.MUST
            );
        }

        // Flow name filtering
        if (chunkQuery.getFlowName() != null && !chunkQuery.getFlowName().trim().isEmpty()) {
            queryBuilder.add(
                new TermQuery(new Term("flowNames", chunkQuery.getFlowName())),
                BooleanClause.Occur.MUST
            );
        }

        // Search terms filtering (top terms in chunk)
        if (chunkQuery.hasSearchTerms()) {
            BooleanQuery.Builder termsBuilder = new BooleanQuery.Builder();
            for (String term : chunkQuery.getSearchTerms()) {
                termsBuilder.add(
                    new TermQuery(new Term("topTerms", term.toLowerCase())),
                    BooleanClause.Occur.SHOULD
                );
            }
            queryBuilder.add(termsBuilder.build(), BooleanClause.Occur.SHOULD);
        }

        // Refresh reader to see latest changes
        DirectoryReader newReader = DirectoryReader.openIfChanged(
            (DirectoryReader) metadataReader
        );
        if (newReader != null) {
            metadataReader.close();
            metadataReader = newReader;
            metadataSearcher = new IndexSearcher(metadataReader);
        }

        // Search metadata index
        TopDocs results = metadataSearcher.search(queryBuilder.build(), 10000);

        List<ChunkCandidate> bloomFilteredCandidates = new ArrayList<>();
        int bloomFilterPruned = 0;

        for (ScoreDoc scoreDoc : results.scoreDocs) {
            Document doc = metadataSearcher.doc(scoreDoc.doc);
            ChunkCandidate candidate = ChunkCandidate.fromDocument(doc);

            // Apply Bloom filter pruning if search terms are present
            if (chunkQuery.hasSearchTerms() && candidate.getBloomFilterBytes() != null) {
                try {
                    com.google.common.hash.BloomFilter<String> bloomFilter =
                        bloomFilterManager.deserialize(candidate.getBloomFilterBytes());

                    if (bloomFilterManager.mightContainAll(bloomFilter, chunkQuery.getSearchTerms())) {
                        bloomFilteredCandidates.add(candidate);
                    } else {
                        bloomFilterPruned++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to check Bloom filter for chunk: {}", candidate.getChunkId(), e);
                    // Include candidate if Bloom filter check fails
                    bloomFilteredCandidates.add(candidate);
                }
            } else {
                // No search terms or no Bloom filter, include candidate
                bloomFilteredCandidates.add(candidate);
            }
        }

        if (bloomFilterPruned > 0) {
            log.info("Bloom filter pruned {} additional chunks", bloomFilterPruned);
        }

        log.info("Found {} candidate chunks (out of {} total chunks, {} Bloom filter pruned)",
            bloomFilteredCandidates.size(), metadataReader.numDocs(), bloomFilterPruned);

        return bloomFilteredCandidates;
    }

    /**
     * Commit metadata index
     */
    public void commit() throws IOException {
        metadataWriter.commit();
        log.debug("Metadata index committed");
    }

    /**
     * Get total number of chunks in metadata index
     */
    public int getTotalChunkCount() {
        return metadataReader.numDocs();
    }

    /**
     * Clear and reinitialize the metadata index (for full reindex)
     */
    public void clearMetadataIndex() throws IOException {
        log.info("Clearing metadata index...");

        // Close existing writer and reader
        if (metadataReader != null) {
            metadataReader.close();
            metadataReader = null;
        }
        if (metadataWriter != null) {
            metadataWriter.close();
            metadataWriter = null;
        }

        // Delete metadata index directory
        Path metadataDir = Paths.get(properties.getIndexDir(), "metadata", "chunks");
        if (Files.exists(metadataDir)) {
            deleteDirectory(metadataDir);
            log.info("Deleted metadata index directory");
        }

        // Recreate directory and reinitialize
        Files.createDirectories(metadataDir);

        Directory directory = FSDirectory.open(metadataDir);
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        this.metadataWriter = new IndexWriter(directory, config);
        this.metadataReader = DirectoryReader.open(metadataWriter);
        this.metadataSearcher = new IndexSearcher(metadataReader);

        log.info("Metadata index cleared and reinitialized");
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
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
    public void shutdown() throws IOException {
        log.info("Shutting down metadata index service");
        if (metadataReader != null) {
            metadataReader.close();
        }
        if (metadataWriter != null) {
            metadataWriter.commit();
            metadataWriter.close();
        }
    }
}
