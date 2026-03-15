package com.lsearch.logsearch.service;

import com.lsearch.logsearch.model.Chunk;
import com.lsearch.logsearch.model.LogEntry;

import java.util.List;

/**
 * Strategy for determining how to split log entries into chunks.
 * Different strategies optimize for different tradeoffs (size, time, performance).
 */
public interface ChunkingStrategy {
    /**
     * Determine chunk boundaries for a set of log entries
     */
    List<Chunk> createChunks(List<LogEntry> entries);

    /**
     * Get strategy name
     */
    String getStrategyName();
}
