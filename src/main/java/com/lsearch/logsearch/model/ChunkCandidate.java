package com.lsearch.logsearch.model;

import org.apache.lucene.document.Document;

/**
 * Represents a candidate chunk that might contain search results.
 * Returned from metadata index queries.
 */
public class ChunkCandidate {

    private String chunkId;
    private String service;
    private long startTimeMillis;
    private long endTimeMillis;
    private int logCount;
    private int errorCount;
    private byte[] bloomFilterBytes;

    public ChunkCandidate() {
    }

    /**
     * Create ChunkCandidate from Lucene Document
     */
    public static ChunkCandidate fromDocument(Document doc) {
        ChunkCandidate candidate = new ChunkCandidate();
        candidate.setChunkId(doc.get("chunkId"));
        candidate.setService(doc.get("service"));

        if (doc.getField("startTimeMillis") != null) {
            candidate.setStartTimeMillis(doc.getField("startTimeMillis").numericValue().longValue());
        }
        if (doc.getField("endTimeMillis") != null) {
            candidate.setEndTimeMillis(doc.getField("endTimeMillis").numericValue().longValue());
        }
        if (doc.getField("logCount") != null) {
            candidate.setLogCount(doc.getField("logCount").numericValue().intValue());
        }
        if (doc.getField("errorCount") != null) {
            candidate.setErrorCount(doc.getField("errorCount").numericValue().intValue());
        }

        // Extract Bloom filter if present
        if (doc.getBinaryValue("bloomFilter") != null) {
            candidate.setBloomFilterBytes(doc.getBinaryValue("bloomFilter").bytes);
        }

        return candidate;
    }

    // Getters and setters
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public long getStartTimeMillis() { return startTimeMillis; }
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }

    public long getEndTimeMillis() { return endTimeMillis; }
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }

    public int getLogCount() { return logCount; }
    public void setLogCount(int logCount) { this.logCount = logCount; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public byte[] getBloomFilterBytes() { return bloomFilterBytes; }
    public void setBloomFilterBytes(byte[] bloomFilterBytes) { this.bloomFilterBytes = bloomFilterBytes; }

    @Override
    public String toString() {
        return "ChunkCandidate{" +
                "chunkId='" + chunkId + '\'' +
                ", service='" + service + '\'' +
                ", logCount=" + logCount +
                '}';
    }
}
