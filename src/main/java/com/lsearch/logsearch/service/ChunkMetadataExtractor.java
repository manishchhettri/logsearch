package com.lsearch.logsearch.service;

import com.lsearch.logsearch.model.ChunkMetadata;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts metadata from a batch of log entries for efficient candidate selection.
 */
@Service
public class ChunkMetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(ChunkMetadataExtractor.class);

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("\\b([a-z][a-z0-9]*\\.)+[a-z][a-z0-9]*\\b");
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*Exception|[A-Z][a-zA-Z0-9]*Error)\\b");
    private static final Pattern STACKTRACE_PATTERN = Pattern.compile("\\s+at \\s+[a-zA-Z0-9_.]+\\(");

    private final IntegrationMetadataExtractor integrationExtractor;
    private final BloomFilterManager bloomFilterManager;

    public ChunkMetadataExtractor(IntegrationMetadataExtractor integrationExtractor,
                                   BloomFilterManager bloomFilterManager) {
        this.integrationExtractor = integrationExtractor;
        this.bloomFilterManager = bloomFilterManager;
    }

    /**
     * Extract metadata from a batch of log entries for a chunk
     */
    public ChunkMetadata extractMetadata(String chunkId, List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            log.warn("No entries to extract metadata from for chunk: {}", chunkId);
            return new ChunkMetadata();
        }

        ChunkMetadata metadata = new ChunkMetadata();
        metadata.setChunkId(chunkId);

        // Extract service name from chunkId (format: service::date::chunk-HH)
        String[] parts = chunkId.split("::");
        if (parts.length >= 1) {
            metadata.setService(parts[0]);
        }

        // Extract time range
        long minTime = entries.stream()
            .mapToLong(e -> e.getTimestamp().toInstant().toEpochMilli())
            .min().orElse(0L);
        long maxTime = entries.stream()
            .mapToLong(e -> e.getTimestamp().toInstant().toEpochMilli())
            .max().orElse(0L);
        metadata.setStartTimeMillis(minTime);
        metadata.setEndTimeMillis(maxTime);

        // Extract log levels
        Set<String> levels = entries.stream()
            .map(LogEntry::getLevel)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        metadata.setLogLevels(levels);

        // Count errors and warnings
        int errors = (int) entries.stream()
            .filter(e -> "ERROR".equalsIgnoreCase(e.getLevel()))
            .count();
        int warnings = (int) entries.stream()
            .filter(e -> "WARN".equalsIgnoreCase(e.getLevel()) || "WARNING".equalsIgnoreCase(e.getLevel()))
            .count();
        metadata.setErrorCount(errors);
        metadata.setWarnCount(warnings);
        metadata.setLogCount(entries.size());

        // Detect stack traces
        boolean hasStackTrace = entries.stream()
            .anyMatch(e -> e.getMessage() != null &&
                (e.getMessage().contains("Exception") ||
                 e.getMessage().contains("Error") ||
                 STACKTRACE_PATTERN.matcher(e.getMessage()).find()));
        metadata.setHasStackTrace(hasStackTrace);

        // Extract top terms
        List<String> topTerms = extractTopTerms(entries, 50);
        metadata.setTopTerms(topTerms);

        // Extract package names
        Set<String> packages = extractPackageNames(entries);
        metadata.setPackages(packages);

        // Extract exception types
        Set<String> exceptions = extractExceptionTypes(entries);
        metadata.setExceptionTypes(exceptions);

        // Estimate index size
        long estimatedSize = entries.stream()
            .mapToLong(this::estimateEntrySize)
            .sum();
        metadata.setIndexSizeBytes(estimatedSize);

        // Extract integration platform metadata (IIB, MQ, ESB)
        if (integrationExtractor != null) {
            integrationExtractor.enrichMetadata(metadata, entries);
        }

        // Create and attach Bloom filter
        if (bloomFilterManager != null) {
            try {
                int estimatedTerms = bloomFilterManager.estimateUniqueTerms(entries);
                com.google.common.hash.BloomFilter<String> bloomFilter =
                    bloomFilterManager.createBloomFilter(entries, estimatedTerms);
                byte[] bloomBytes = bloomFilterManager.serialize(bloomFilter);
                metadata.setBloomFilterBytes(bloomBytes);

                log.debug("Created Bloom filter for chunk {} ({} bytes, {} estimated terms)",
                    chunkId, bloomBytes.length, estimatedTerms);
            } catch (Exception e) {
                log.warn("Failed to create Bloom filter for chunk: {}", chunkId, e);
            }
        }

        log.debug("Extracted metadata for chunk {}: {} entries, {} errors, {} warnings",
            chunkId, entries.size(), errors, warnings);

        return metadata;
    }

    /**
     * Extract top N most frequent terms from log messages
     */
    private List<String> extractTopTerms(List<LogEntry> entries, int limit) {
        Map<String, Integer> termFrequency = new HashMap<>();

        for (LogEntry entry : entries) {
            if (entry.getMessage() == null) continue;

            // Tokenize and count terms
            String[] tokens = tokenize(entry.getMessage());
            for (String token : tokens) {
                if (token.length() > 2 && !isStopWord(token)) {
                    termFrequency.merge(token.toLowerCase(), 1, Integer::sum);
                }
            }
        }

        // Sort by frequency and take top N
        return termFrequency.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * Extract Java package names from log messages and stack traces
     */
    private Set<String> extractPackageNames(List<LogEntry> entries) {
        Set<String> packages = new HashSet<>();

        for (LogEntry entry : entries) {
            if (entry.getMessage() == null) continue;

            Matcher matcher = PACKAGE_PATTERN.matcher(entry.getMessage());
            while (matcher.find()) {
                String pkg = matcher.group();
                // Filter out common false positives
                if (pkg.contains("com.") || pkg.contains("org.") ||
                    pkg.contains("java.") || pkg.contains("javax.")) {
                    packages.add(pkg);
                }
            }

            // Also extract from logger name
            if (entry.getLogger() != null && entry.getLogger().contains(".")) {
                packages.add(entry.getLogger());
            }
        }

        return packages;
    }

    /**
     * Extract exception type names
     */
    private Set<String> extractExceptionTypes(List<LogEntry> entries) {
        Set<String> exceptions = new HashSet<>();

        for (LogEntry entry : entries) {
            if (entry.getMessage() == null) continue;

            Matcher matcher = EXCEPTION_PATTERN.matcher(entry.getMessage());
            while (matcher.find()) {
                exceptions.add(matcher.group());
            }
        }

        return exceptions;
    }

    /**
     * Tokenize text into words
     */
    private String[] tokenize(String text) {
        // Split on whitespace and common delimiters
        return text.split("[\\s\\.,;:()\\[\\]{}\"'<>=]+");
    }

    /**
     * Check if a word is a common stop word
     */
    private boolean isStopWord(String word) {
        // Common English stop words to ignore
        Set<String> stopWords = new HashSet<>(Arrays.asList(
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "her",
            "was", "one", "our", "out", "day", "get", "has", "him", "his", "how",
            "man", "new", "now", "old", "see", "two", "way", "who", "boy", "did",
            "its", "let", "put", "say", "she", "too", "use", "with", "from", "have",
            "this", "that", "will", "your", "been", "call", "find", "had", "like",
            "long", "look", "made", "many", "more", "most", "only", "over", "said",
            "some", "such", "than", "them", "then", "they", "very", "what", "when",
            "where", "which", "while", "would"
        ));
        return stopWords.contains(word.toLowerCase());
    }

    /**
     * Estimate size of a log entry in bytes (for index size estimation)
     */
    private long estimateEntrySize(LogEntry entry) {
        int messageBytes = entry.getMessage() != null ?
            entry.getMessage().length() * 2 : 0; // UTF-16
        int metadataBytes = 100; // Timestamp, level, logger, etc.
        int indexOverhead = (messageBytes + metadataBytes) / 2; // Lucene overhead
        return messageBytes + metadataBytes + indexOverhead;
    }
}
