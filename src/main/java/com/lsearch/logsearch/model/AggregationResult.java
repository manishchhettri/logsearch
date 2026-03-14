package com.lsearch.logsearch.model;

import java.util.List;
import java.util.Map;

/**
 * Aggregation results for log searches.
 * Provides analytics and grouping by various dimensions.
 */
public class AggregationResult {

    /**
     * Total number of results
     */
    private long totalHits;

    /**
     * Aggregation by log level (ERROR, WARN, INFO, DEBUG)
     * e.g., [{"value": "ERROR", "count": 234, "percentage": 45.2}]
     */
    private List<Facet> levelFacets;

    /**
     * Aggregation by exception type
     * e.g., [{"value": "NullPointerException", "count": 456, "percentage": 36.6}]
     */
    private List<Facet> exceptionFacets;

    /**
     * Aggregation by logger/component
     * e.g., [{"value": "com.example.DataValidator", "count": 234, "percentage": 18.8}]
     */
    private List<Facet> loggerFacets;

    /**
     * Aggregation by user
     * e.g., [{"value": "admin", "count": 89, "percentage": 7.1}]
     */
    private List<Facet> userFacets;

    /**
     * Aggregation by source file
     * e.g., [{"value": "server-20260313.log", "count": 567, "percentage": 45.5}]
     */
    private List<Facet> fileFacets;

    /**
     * Timeline histogram - hourly distribution
     * Map<timestamp, count> where timestamp is start of hour
     * e.g., {"2026-03-13T15:00:00Z": 120, "2026-03-13T16:00:00Z": 234}
     */
    private Map<String, Long> timelineHourly;

    /**
     * Timeline histogram by log level - flexible intervals
     * Map<timestamp, Map<level, count>>
     * e.g., {"2026-03-13T15:00:00Z": {"ERROR": 10, "WARN": 20, "INFO": 90}}
     */
    private Map<String, Map<String, Long>> timelineByLevel;

    /**
     * Detected patterns (spikes, trends, anomalies)
     * e.g., ["Memory leak suspected", "Spike detected at 17:03"]
     */
    private List<String> detectedPatterns;

    public AggregationResult() {
    }

    public AggregationResult(long totalHits, List<Facet> levelFacets, List<Facet> exceptionFacets,
                           List<Facet> loggerFacets, List<Facet> userFacets, List<Facet> fileFacets,
                           Map<String, Long> timelineHourly, Map<String, Map<String, Long>> timelineByLevel,
                           List<String> detectedPatterns) {
        this.totalHits = totalHits;
        this.levelFacets = levelFacets;
        this.exceptionFacets = exceptionFacets;
        this.loggerFacets = loggerFacets;
        this.userFacets = userFacets;
        this.fileFacets = fileFacets;
        this.timelineHourly = timelineHourly;
        this.timelineByLevel = timelineByLevel;
        this.detectedPatterns = detectedPatterns;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    public List<Facet> getLevelFacets() {
        return levelFacets;
    }

    public void setLevelFacets(List<Facet> levelFacets) {
        this.levelFacets = levelFacets;
    }

    public List<Facet> getExceptionFacets() {
        return exceptionFacets;
    }

    public void setExceptionFacets(List<Facet> exceptionFacets) {
        this.exceptionFacets = exceptionFacets;
    }

    public List<Facet> getLoggerFacets() {
        return loggerFacets;
    }

    public void setLoggerFacets(List<Facet> loggerFacets) {
        this.loggerFacets = loggerFacets;
    }

    public List<Facet> getUserFacets() {
        return userFacets;
    }

    public void setUserFacets(List<Facet> userFacets) {
        this.userFacets = userFacets;
    }

    public List<Facet> getFileFacets() {
        return fileFacets;
    }

    public void setFileFacets(List<Facet> fileFacets) {
        this.fileFacets = fileFacets;
    }

    public Map<String, Long> getTimelineHourly() {
        return timelineHourly;
    }

    public void setTimelineHourly(Map<String, Long> timelineHourly) {
        this.timelineHourly = timelineHourly;
    }

    public Map<String, Map<String, Long>> getTimelineByLevel() {
        return timelineByLevel;
    }

    public void setTimelineByLevel(Map<String, Map<String, Long>> timelineByLevel) {
        this.timelineByLevel = timelineByLevel;
    }

    public List<String> getDetectedPatterns() {
        return detectedPatterns;
    }

    public void setDetectedPatterns(List<String> detectedPatterns) {
        this.detectedPatterns = detectedPatterns;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long totalHits;
        private List<Facet> levelFacets;
        private List<Facet> exceptionFacets;
        private List<Facet> loggerFacets;
        private List<Facet> userFacets;
        private List<Facet> fileFacets;
        private Map<String, Long> timelineHourly;
        private Map<String, Map<String, Long>> timelineByLevel;
        private List<String> detectedPatterns;

        public Builder totalHits(long totalHits) {
            this.totalHits = totalHits;
            return this;
        }

        public Builder levelFacets(List<Facet> levelFacets) {
            this.levelFacets = levelFacets;
            return this;
        }

        public Builder exceptionFacets(List<Facet> exceptionFacets) {
            this.exceptionFacets = exceptionFacets;
            return this;
        }

        public Builder loggerFacets(List<Facet> loggerFacets) {
            this.loggerFacets = loggerFacets;
            return this;
        }

        public Builder userFacets(List<Facet> userFacets) {
            this.userFacets = userFacets;
            return this;
        }

        public Builder fileFacets(List<Facet> fileFacets) {
            this.fileFacets = fileFacets;
            return this;
        }

        public Builder timelineHourly(Map<String, Long> timelineHourly) {
            this.timelineHourly = timelineHourly;
            return this;
        }

        public Builder timelineByLevel(Map<String, Map<String, Long>> timelineByLevel) {
            this.timelineByLevel = timelineByLevel;
            return this;
        }

        public Builder detectedPatterns(List<String> detectedPatterns) {
            this.detectedPatterns = detectedPatterns;
            return this;
        }

        public AggregationResult build() {
            return new AggregationResult(totalHits, levelFacets, exceptionFacets, loggerFacets,
                    userFacets, fileFacets, timelineHourly, timelineByLevel, detectedPatterns);
        }
    }
}
