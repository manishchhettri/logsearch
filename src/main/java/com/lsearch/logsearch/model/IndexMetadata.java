package com.lsearch.logsearch.model;

import java.time.ZonedDateTime;

/**
 * Metadata about an indexed log source.
 * Tracks statistics and status information for each index.
 */
public class IndexMetadata {

    private String indexName;
    private long totalDocuments;
    private long sizeBytes;
    private ZonedDateTime lastIndexed;
    private ZonedDateTime oldestLog;
    private ZonedDateTime newestLog;
    private int fileCount;
    private IndexStatus status = IndexStatus.ACTIVE;

    public enum IndexStatus {
        ACTIVE,
        DISABLED,
        ERROR,
        INDEXING
    }

    public IndexMetadata() {
    }

    public IndexMetadata(String indexName) {
        this.indexName = indexName;
    }

    // Getters and setters

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public long getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(long totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public ZonedDateTime getLastIndexed() {
        return lastIndexed;
    }

    public void setLastIndexed(ZonedDateTime lastIndexed) {
        this.lastIndexed = lastIndexed;
    }

    public ZonedDateTime getOldestLog() {
        return oldestLog;
    }

    public void setOldestLog(ZonedDateTime oldestLog) {
        this.oldestLog = oldestLog;
    }

    public ZonedDateTime getNewestLog() {
        return newestLog;
    }

    public void setNewestLog(ZonedDateTime newestLog) {
        this.newestLog = newestLog;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public IndexStatus getStatus() {
        return status;
    }

    public void setStatus(IndexStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "IndexMetadata{" +
                "indexName='" + indexName + '\'' +
                ", totalDocuments=" + totalDocuments +
                ", status=" + status +
                ", lastIndexed=" + lastIndexed +
                '}';
    }
}
