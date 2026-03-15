package com.lsearch.logsearch.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Manages Bloom filters for efficient term existence checking.
 * Used to prune chunks that definitely don't contain search terms.
 */
@Service
public class BloomFilterManager {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterManager.class);
    private static final double FALSE_POSITIVE_RATE = 0.01; // 1% false positive rate

    /**
     * Create Bloom filter from log entries
     */
    public BloomFilter<String> createBloomFilter(List<LogEntry> entries,
                                                   int estimatedUniqueTerms) {
        BloomFilter<String> bloomFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            estimatedUniqueTerms,
            FALSE_POSITIVE_RATE
        );

        // Add all unique terms from all entries
        for (LogEntry entry : entries) {
            if (entry.getMessage() == null) continue;

            // Tokenize message
            String[] tokens = tokenize(entry.getMessage());
            for (String token : tokens) {
                if (token != null && !token.isEmpty()) {
                    bloomFilter.put(token.toLowerCase());
                }
            }

            // Add structured fields
            if (entry.getLevel() != null) {
                bloomFilter.put(entry.getLevel().toLowerCase());
            }
            if (entry.getLogger() != null) {
                bloomFilter.put(entry.getLogger().toLowerCase());
            }
        }

        return bloomFilter;
    }

    /**
     * Serialize Bloom filter to bytes
     */
    public byte[] serialize(BloomFilter<String> bloomFilter) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bloomFilter.writeTo(baos);
        return baos.toByteArray();
    }

    /**
     * Deserialize Bloom filter from bytes
     */
    public BloomFilter<String> deserialize(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        return BloomFilter.readFrom(bais, Funnels.stringFunnel(StandardCharsets.UTF_8));
    }

    /**
     * Check if Bloom filter might contain all search terms
     */
    public boolean mightContainAll(BloomFilter<String> bloomFilter,
                                     Set<String> searchTerms) {
        if (searchTerms == null || searchTerms.isEmpty()) {
            return true; // No terms to check
        }
        return searchTerms.stream()
            .allMatch(term -> bloomFilter.mightContain(term.toLowerCase()));
    }

    /**
     * Simple tokenizer (can be enhanced with CodeAnalyzer logic)
     */
    private String[] tokenize(String text) {
        if (text == null) return new String[0];
        // Split on whitespace and common delimiters
        return text.split("[\\s\\.,;:()\\[\\]{}\"'<>=]+");
    }

    /**
     * Estimate unique terms in entries (for Bloom filter sizing)
     */
    public int estimateUniqueTerms(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 1000;
        }
        // Conservative estimate: ~100 unique terms per 1000 entries
        return Math.max(1000, entries.size() / 10);
    }
}
