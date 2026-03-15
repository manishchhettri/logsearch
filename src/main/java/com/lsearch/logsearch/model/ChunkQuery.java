package com.lsearch.logsearch.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Query for finding candidate chunks in the metadata index.
 */
public class ChunkQuery {

    private Long startTimeMillis;
    private Long endTimeMillis;
    private String service;
    private String logLevel;
    private boolean requireStackTrace;
    private String packageHint;
    private Set<String> searchTerms;
    private String correlationId;
    private String flowName;

    private ChunkQuery() {
        this.searchTerms = new HashSet<>();
    }

    public boolean hasTimeRange() {
        return startTimeMillis != null && endTimeMillis != null;
    }

    public boolean hasService() {
        return service != null && !service.trim().isEmpty();
    }

    public boolean hasLogLevel() {
        return logLevel != null && !logLevel.trim().isEmpty();
    }

    public boolean requiresStackTrace() {
        return requireStackTrace;
    }

    public boolean hasPackageHint() {
        return packageHint != null && !packageHint.trim().isEmpty();
    }

    public boolean hasSearchTerms() {
        return searchTerms != null && !searchTerms.isEmpty();
    }

    // Getters
    public Long getStartTimeMillis() { return startTimeMillis; }
    public Long getEndTimeMillis() { return endTimeMillis; }
    public String getService() { return service; }
    public String getLogLevel() { return logLevel; }
    public String getPackageHint() { return packageHint; }
    public Set<String> getSearchTerms() { return searchTerms; }
    public String getCorrelationId() { return correlationId; }
    public String getFlowName() { return flowName; }

    /**
     * Builder for constructing chunk queries
     */
    public static class Builder {
        private ChunkQuery query = new ChunkQuery();

        public Builder timeRange(long startMillis, long endMillis) {
            query.startTimeMillis = startMillis;
            query.endTimeMillis = endMillis;
            return this;
        }

        public Builder service(String service) {
            query.service = service;
            return this;
        }

        public Builder logLevel(String level) {
            query.logLevel = level;
            return this;
        }

        public Builder requireStackTrace(boolean require) {
            query.requireStackTrace = require;
            return this;
        }

        public Builder packageHint(String hint) {
            query.packageHint = hint;
            return this;
        }

        public Builder searchTerms(Set<String> terms) {
            query.searchTerms = terms;
            return this;
        }

        public Builder correlationId(String corrId) {
            query.correlationId = corrId;
            return this;
        }

        public Builder flowName(String flow) {
            query.flowName = flow;
            return this;
        }

        public ChunkQuery build() {
            return query;
        }
    }

    @Override
    public String toString() {
        return "ChunkQuery{" +
                "timeRange=" + (hasTimeRange() ? startTimeMillis + "-" + endTimeMillis : "any") +
                ", service='" + service + '\'' +
                ", logLevel='" + logLevel + '\'' +
                ", requireStackTrace=" + requireStackTrace +
                ", searchTerms=" + searchTerms.size() +
                '}';
    }
}
