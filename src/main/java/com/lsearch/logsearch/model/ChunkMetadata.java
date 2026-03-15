package com.lsearch.logsearch.model;

import org.apache.lucene.document.*;
import org.apache.lucene.util.BytesRef;

import java.util.*;

/**
 * Metadata about a chunk of log data.
 * Used for efficient candidate selection during search.
 */
public class ChunkMetadata {

    // Identity
    private String chunkId;
    private String service;
    private String fileName;

    // Time range
    private long startTimeMillis;
    private long endTimeMillis;

    // Log characteristics
    private boolean hasStackTrace;
    private Set<String> logLevels;

    // Top terms
    private List<String> topTerms;

    // Package names and exceptions
    private Set<String> packages;
    private Set<String> exceptionTypes;

    // Statistics
    private int logCount;
    private int errorCount;
    private int warnCount;
    private long indexSizeBytes;

    // Integration platform support
    private String integrationPlatform; // IIB, MQ, ESB, null
    private boolean hasCorrelationId;
    private boolean hasMessageId;
    private Set<String> correlationIds;
    private Set<String> messageIds;
    private Set<String> flowNames;
    private Set<String> endpoints;

    // Bloom filter
    private byte[] bloomFilterBytes;

    public ChunkMetadata() {
        this.logLevels = new HashSet<>();
        this.topTerms = new ArrayList<>();
        this.packages = new HashSet<>();
        this.exceptionTypes = new HashSet<>();
        this.correlationIds = new HashSet<>();
        this.messageIds = new HashSet<>();
        this.flowNames = new HashSet<>();
        this.endpoints = new HashSet<>();
    }

    /**
     * Convert to Lucene Document for indexing
     */
    public Document toLuceneDocument() {
        Document doc = new Document();

        // Identity fields
        doc.add(new StringField("chunkId", chunkId, Field.Store.YES));
        doc.add(new StringField("service", service, Field.Store.YES));
        if (fileName != null) {
            doc.add(new StringField("fileName", fileName, Field.Store.YES));
        }

        // Time range (critical for pruning)
        doc.add(new LongPoint("startTime", startTimeMillis));
        doc.add(new LongPoint("endTime", endTimeMillis));
        doc.add(new StoredField("startTimeMillis", startTimeMillis));
        doc.add(new StoredField("endTimeMillis", endTimeMillis));

        // Log characteristics
        doc.add(new StringField("hasStackTrace", String.valueOf(hasStackTrace), Field.Store.YES));
        if (!logLevels.isEmpty()) {
            doc.add(new TextField("logLevels", String.join(" ", logLevels), Field.Store.YES));
        }

        // Top terms
        if (!topTerms.isEmpty()) {
            doc.add(new TextField("topTerms", String.join(" ", topTerms), Field.Store.YES));
        }

        // Package names
        if (!packages.isEmpty()) {
            doc.add(new TextField("packages", String.join(" ", packages), Field.Store.YES));
        }

        // Exception types
        if (!exceptionTypes.isEmpty()) {
            doc.add(new TextField("exceptionTypes", String.join(" ", exceptionTypes), Field.Store.YES));
        }

        // Statistics
        doc.add(new IntPoint("logCount", logCount));
        doc.add(new StoredField("logCount", logCount));
        doc.add(new IntPoint("errorCount", errorCount));
        doc.add(new StoredField("errorCount", errorCount));
        doc.add(new IntPoint("warnCount", warnCount));
        doc.add(new StoredField("warnCount", warnCount));

        // Size
        doc.add(new LongPoint("indexSizeBytes", indexSizeBytes));
        doc.add(new StoredField("indexSizeBytes", indexSizeBytes));

        // Integration platform
        if (integrationPlatform != null) {
            doc.add(new StringField("integrationPlatform", integrationPlatform, Field.Store.YES));
        }
        doc.add(new StringField("hasCorrelationId", String.valueOf(hasCorrelationId), Field.Store.YES));
        doc.add(new StringField("hasMessageId", String.valueOf(hasMessageId), Field.Store.YES));

        // Correlation tracking
        if (!correlationIds.isEmpty()) {
            doc.add(new TextField("correlationIds", String.join(" ", correlationIds), Field.Store.YES));
        }
        if (!messageIds.isEmpty()) {
            doc.add(new TextField("messageIds", String.join(" ", messageIds), Field.Store.YES));
        }
        if (!flowNames.isEmpty()) {
            doc.add(new TextField("flowNames", String.join(" ", flowNames), Field.Store.YES));
        }
        if (!endpoints.isEmpty()) {
            doc.add(new TextField("endpoints", String.join(" ", endpoints), Field.Store.YES));
        }

        // Bloom filter
        if (bloomFilterBytes != null) {
            doc.add(new StoredField("bloomFilter", bloomFilterBytes));
        }

        return doc;
    }

    /**
     * Create ChunkMetadata from Lucene Document
     */
    public static ChunkMetadata fromDocument(Document doc) {
        ChunkMetadata metadata = new ChunkMetadata();

        metadata.setChunkId(doc.get("chunkId"));
        metadata.setService(doc.get("service"));
        metadata.setFileName(doc.get("fileName"));

        metadata.setStartTimeMillis(doc.getField("startTimeMillis").numericValue().longValue());
        metadata.setEndTimeMillis(doc.getField("endTimeMillis").numericValue().longValue());

        metadata.setHasStackTrace(Boolean.parseBoolean(doc.get("hasStackTrace")));

        String levels = doc.get("logLevels");
        if (levels != null) {
            metadata.setLogLevels(new HashSet<>(Arrays.asList(levels.split(" "))));
        }

        String terms = doc.get("topTerms");
        if (terms != null) {
            metadata.setTopTerms(Arrays.asList(terms.split(" ")));
        }

        String pkgs = doc.get("packages");
        if (pkgs != null) {
            metadata.setPackages(new HashSet<>(Arrays.asList(pkgs.split(" "))));
        }

        String exceptions = doc.get("exceptionTypes");
        if (exceptions != null) {
            metadata.setExceptionTypes(new HashSet<>(Arrays.asList(exceptions.split(" "))));
        }

        metadata.setLogCount(doc.getField("logCount").numericValue().intValue());
        metadata.setErrorCount(doc.getField("errorCount").numericValue().intValue());
        metadata.setWarnCount(doc.getField("warnCount").numericValue().intValue());
        metadata.setIndexSizeBytes(doc.getField("indexSizeBytes").numericValue().longValue());

        metadata.setIntegrationPlatform(doc.get("integrationPlatform"));
        metadata.setHasCorrelationId(Boolean.parseBoolean(doc.get("hasCorrelationId")));
        metadata.setHasMessageId(Boolean.parseBoolean(doc.get("hasMessageId")));

        String corrIds = doc.get("correlationIds");
        if (corrIds != null) {
            metadata.setCorrelationIds(new HashSet<>(Arrays.asList(corrIds.split(" "))));
        }

        String msgIds = doc.get("messageIds");
        if (msgIds != null) {
            metadata.setMessageIds(new HashSet<>(Arrays.asList(msgIds.split(" "))));
        }

        String flows = doc.get("flowNames");
        if (flows != null) {
            metadata.setFlowNames(new HashSet<>(Arrays.asList(flows.split(" "))));
        }

        String eps = doc.get("endpoints");
        if (eps != null) {
            metadata.setEndpoints(new HashSet<>(Arrays.asList(eps.split(" "))));
        }

        BytesRef bloomRef = doc.getBinaryValue("bloomFilter");
        if (bloomRef != null) {
            metadata.setBloomFilterBytes(bloomRef.bytes);
        }

        return metadata;
    }

    // Getters and setters
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getStartTimeMillis() { return startTimeMillis; }
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }

    public long getEndTimeMillis() { return endTimeMillis; }
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }

    public boolean isHasStackTrace() { return hasStackTrace; }
    public void setHasStackTrace(boolean hasStackTrace) { this.hasStackTrace = hasStackTrace; }

    public Set<String> getLogLevels() { return logLevels; }
    public void setLogLevels(Set<String> logLevels) { this.logLevels = logLevels; }

    public List<String> getTopTerms() { return topTerms; }
    public void setTopTerms(List<String> topTerms) { this.topTerms = topTerms; }

    public Set<String> getPackages() { return packages; }
    public void setPackages(Set<String> packages) { this.packages = packages; }

    public Set<String> getExceptionTypes() { return exceptionTypes; }
    public void setExceptionTypes(Set<String> exceptionTypes) { this.exceptionTypes = exceptionTypes; }

    public int getLogCount() { return logCount; }
    public void setLogCount(int logCount) { this.logCount = logCount; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public int getWarnCount() { return warnCount; }
    public void setWarnCount(int warnCount) { this.warnCount = warnCount; }

    public long getIndexSizeBytes() { return indexSizeBytes; }
    public void setIndexSizeBytes(long indexSizeBytes) { this.indexSizeBytes = indexSizeBytes; }

    public String getIntegrationPlatform() { return integrationPlatform; }
    public void setIntegrationPlatform(String integrationPlatform) { this.integrationPlatform = integrationPlatform; }

    public boolean isHasCorrelationId() { return hasCorrelationId; }
    public void setHasCorrelationId(boolean hasCorrelationId) { this.hasCorrelationId = hasCorrelationId; }

    public boolean isHasMessageId() { return hasMessageId; }
    public void setHasMessageId(boolean hasMessageId) { this.hasMessageId = hasMessageId; }

    public Set<String> getCorrelationIds() { return correlationIds; }
    public void setCorrelationIds(Set<String> correlationIds) { this.correlationIds = correlationIds; }

    public Set<String> getMessageIds() { return messageIds; }
    public void setMessageIds(Set<String> messageIds) { this.messageIds = messageIds; }

    public Set<String> getFlowNames() { return flowNames; }
    public void setFlowNames(Set<String> flowNames) { this.flowNames = flowNames; }

    public Set<String> getEndpoints() { return endpoints; }
    public void setEndpoints(Set<String> endpoints) { this.endpoints = endpoints; }

    public byte[] getBloomFilterBytes() { return bloomFilterBytes; }
    public void setBloomFilterBytes(byte[] bloomFilterBytes) { this.bloomFilterBytes = bloomFilterBytes; }

    @Override
    public String toString() {
        return "ChunkMetadata{" +
                "chunkId='" + chunkId + '\'' +
                ", service='" + service + '\'' +
                ", logCount=" + logCount +
                ", errorCount=" + errorCount +
                '}';
    }
}
