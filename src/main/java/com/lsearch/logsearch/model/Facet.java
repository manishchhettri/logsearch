package com.lsearch.logsearch.model;

/**
 * Represents a single facet value and its count.
 * Used for aggregations like "NullPointerException: 234"
 */
public class Facet {

    private String value;
    private long count;
    private double percentage;

    public Facet() {
    }

    public Facet(String value, long count, double percentage) {
        this.value = value;
        this.count = count;
        this.percentage = percentage;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String value;
        private long count;
        private double percentage;

        public Builder value(String value) {
            this.value = value;
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

        public Facet build() {
            return new Facet(value, count, percentage);
        }
    }
}
