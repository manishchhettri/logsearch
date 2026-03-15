package com.lsearch.logsearch.service;

import com.lsearch.logsearch.model.Chunk;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple hourly chunking strategy that groups log entries by hour.
 * Fallback strategy for consistent, predictable chunks.
 */
@Service
public class HourlyChunkingStrategy implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(HourlyChunkingStrategy.class);

    @Override
    public List<Chunk> createChunks(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        // Group by hour
        Map<String, Chunk> hourlyChunks = new HashMap<>();

        for (LogEntry entry : entries) {
            ZonedDateTime timestamp = entry.getTimestamp();
            String hourKey = String.format("%s-%02d",
                timestamp.toLocalDate(), timestamp.getHour());

            Chunk chunk = hourlyChunks.computeIfAbsent(hourKey, k -> new Chunk());
            chunk.add(entry);
        }

        log.info("Hourly chunking created {} chunks from {} entries",
            hourlyChunks.size(), entries.size());

        return new ArrayList<>(hourlyChunks.values());
    }

    @Override
    public String getStrategyName() {
        return "HOURLY";
    }
}
