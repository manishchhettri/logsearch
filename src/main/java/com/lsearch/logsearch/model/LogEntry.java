package com.lsearch.logsearch.model;

import java.time.ZonedDateTime;

public class LogEntry {
    private ZonedDateTime timestamp;
    private String user;
    private String message;
    private String sourceFile;
    private long lineNumber;

    public LogEntry() {
    }

    public LogEntry(ZonedDateTime timestamp, String user, String message, String sourceFile, long lineNumber) {
        this.timestamp = timestamp;
        this.user = user;
        this.message = message;
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(long lineNumber) {
        this.lineNumber = lineNumber;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ZonedDateTime timestamp;
        private String user;
        private String message;
        private String sourceFile;
        private long lineNumber;

        public Builder timestamp(ZonedDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder sourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder lineNumber(long lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public LogEntry build() {
            return new LogEntry(timestamp, user, message, sourceFile, lineNumber);
        }
    }
}
