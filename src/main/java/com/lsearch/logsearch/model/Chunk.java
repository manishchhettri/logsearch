package com.lsearch.logsearch.model;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chunk of log entries.
 * Used during indexing to group log entries together.
 */
public class Chunk {

    private ChunkIdentifier identifier;
    private List<LogEntry> entries;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;

    public Chunk() {
        this.entries = new ArrayList<>();
    }

    public Chunk(ChunkIdentifier identifier) {
        this.identifier = identifier;
        this.entries = new ArrayList<>();
    }

    public void add(LogEntry entry) {
        entries.add(entry);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public ChunkIdentifier getIdentifier() {
        return identifier;
    }

    public void setIdentifier(ChunkIdentifier identifier) {
        this.identifier = identifier;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<LogEntry> entries) {
        this.entries = entries;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(ZonedDateTime startTime) {
        this.startTime = startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(ZonedDateTime endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "identifier=" + identifier +
                ", entries=" + entries.size() +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
