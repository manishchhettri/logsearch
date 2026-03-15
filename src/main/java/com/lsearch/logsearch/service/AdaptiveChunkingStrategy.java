package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.Chunk;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive chunking strategy that creates chunks based on size and time constraints.
 * Target: 150-250 MB chunks with 15 min - 6 hour duration.
 */
@Service
@Primary
public class AdaptiveChunkingStrategy implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveChunkingStrategy.class);

    // Target chunk size: 150-250 MB
    private static final long TARGET_SIZE_BYTES = 200_000_000L;
    private static final long MIN_SIZE_BYTES = 150_000_000L;
    private static final long MAX_SIZE_BYTES = 250_000_000L;

    // Time constraints
    private static final long MIN_DURATION_MINUTES = 15;
    private static final long MAX_DURATION_HOURS = 6;

    private final LogSearchProperties properties;

    public AdaptiveChunkingStrategy(LogSearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Chunk> createChunks(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        List<Chunk> chunks = new ArrayList<>();
        Chunk currentChunk = new Chunk();
        long currentSize = 0;
        ZonedDateTime chunkStartTime = entries.get(0).getTimestamp();

        for (LogEntry entry : entries) {
            long entrySize = estimateEntrySize(entry);
            ZonedDateTime entryTime = entry.getTimestamp();

            // Calculate current chunk duration
            Duration chunkDuration = Duration.between(chunkStartTime, entryTime);
            long durationMinutes = chunkDuration.toMinutes();
            long durationHours = chunkDuration.toHours();

            // Decision: Should we finalize current chunk?
            boolean shouldFinalize = false;

            // Rule 1: Exceeded maximum duration (hard limit)
            if (durationHours >= MAX_DURATION_HOURS) {
                shouldFinalize = true;
                log.debug("Finalizing chunk due to max duration: {} hours", durationHours);
            }

            // Rule 2: Exceeded target size AND met minimum duration
            else if (currentSize + entrySize > TARGET_SIZE_BYTES &&
                     durationMinutes >= MIN_DURATION_MINUTES) {
                shouldFinalize = true;
                log.debug("Finalizing chunk due to size: {} MB (duration: {} min)",
                    currentSize / 1_000_000, durationMinutes);
            }

            // Rule 3: Way over max size (emergency finalize)
            else if (currentSize + entrySize > MAX_SIZE_BYTES) {
                shouldFinalize = true;
                log.warn("Emergency finalize chunk due to max size: {} MB",
                    currentSize / 1_000_000);
            }

            if (shouldFinalize && !currentChunk.isEmpty()) {
                // Finalize current chunk
                currentChunk.setEndTime(entryTime);
                chunks.add(currentChunk);

                log.info("Created adaptive chunk: {} entries, {} MB, {} minutes",
                    currentChunk.size(),
                    currentSize / 1_000_000,
                    durationMinutes);

                // Start new chunk
                currentChunk = new Chunk();
                currentSize = 0;
                chunkStartTime = entryTime;
            }

            // Add entry to current chunk
            currentChunk.add(entry);
            currentSize += entrySize;
        }

        // Finalize last chunk
        if (!currentChunk.isEmpty()) {
            currentChunk.setEndTime(entries.get(entries.size() - 1).getTimestamp());
            chunks.add(currentChunk);

            log.info("Created final adaptive chunk: {} entries, {} MB",
                currentChunk.size(), currentSize / 1_000_000);
        }

        log.info("Adaptive chunking created {} chunks from {} entries",
            chunks.size(), entries.size());

        return chunks;
    }

    /**
     * Estimate size of a log entry in bytes
     * (message + metadata overhead)
     */
    private long estimateEntrySize(LogEntry entry) {
        // Rough estimate:
        // - Message text
        // - Timestamp (8 bytes)
        // - Metadata (~100 bytes for level, logger, user, etc.)
        // - Index overhead (~50% of raw size)

        int messageBytes = entry.getMessage() != null ?
            entry.getMessage().length() * 2 : 0; // 2 bytes per char (UTF-16)

        int metadataBytes = 100;
        int indexOverhead = (messageBytes + metadataBytes) / 2;

        return messageBytes + metadataBytes + indexOverhead;
    }

    @Override
    public String getStrategyName() {
        return "ADAPTIVE";
    }
}
