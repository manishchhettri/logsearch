package com.lsearch.logsearch.model;

import java.util.List;

public class SearchResult {
    private List<LogEntry> entries;
    private long totalHits;
    private int page;
    private int pageSize;
    private long searchTimeMs;

    public SearchResult() {
    }

    public SearchResult(List<LogEntry> entries, long totalHits, int page, int pageSize, long searchTimeMs) {
        this.entries = entries;
        this.totalHits = totalHits;
        this.page = page;
        this.pageSize = pageSize;
        this.searchTimeMs = searchTimeMs;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<LogEntry> entries) {
        this.entries = entries;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getSearchTimeMs() {
        return searchTimeMs;
    }

    public void setSearchTimeMs(long searchTimeMs) {
        this.searchTimeMs = searchTimeMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<LogEntry> entries;
        private long totalHits;
        private int page;
        private int pageSize;
        private long searchTimeMs;

        public Builder entries(List<LogEntry> entries) {
            this.entries = entries;
            return this;
        }

        public Builder totalHits(long totalHits) {
            this.totalHits = totalHits;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder searchTimeMs(long searchTimeMs) {
            this.searchTimeMs = searchTimeMs;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(entries, totalHits, page, pageSize, searchTimeMs);
        }
    }
}
