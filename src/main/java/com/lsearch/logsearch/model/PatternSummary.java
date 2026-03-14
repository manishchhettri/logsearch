package com.lsearch.logsearch.model;

/**
 * Represents a log pattern with occurrence count and percentage.
 * Used for log fingerprinting and pattern analytics.
 */
public class PatternSummary {
    private String pattern;
    private long count;
    private double percentage;
    private String sampleMessage;  // Example of this pattern
    private String level;           // Most common log level for this pattern

    public PatternSummary() {
    }

    public PatternSummary(String pattern, long count) {
        this.pattern = pattern;
        this.count = count;
    }

    public PatternSummary(String pattern, long count, double percentage, String sampleMessage, String level) {
        this.pattern = pattern;
        this.count = count;
        this.percentage = percentage;
        this.sampleMessage = sampleMessage;
        this.level = level;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    public String getSampleMessage() {
        return sampleMessage;
    }

    public void setSampleMessage(String sampleMessage) {
        this.sampleMessage = sampleMessage;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String pattern;
        private long count;
        private double percentage;
        private String sampleMessage;
        private String level;

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder percentage(double percentage) {
            this.percentage = percentage;
            return this;
        }

        public Builder sampleMessage(String sampleMessage) {
            this.sampleMessage = sampleMessage;
            return this;
        }

        public Builder level(String level) {
            this.level = level;
            return this;
        }

        public PatternSummary build() {
            return new PatternSummary(pattern, count, percentage, sampleMessage, level);
        }
    }
}
