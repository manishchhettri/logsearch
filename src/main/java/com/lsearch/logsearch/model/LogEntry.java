package com.lsearch.logsearch.model;

import java.time.ZonedDateTime;

public class LogEntry {
    private ZonedDateTime timestamp;
    private String level;
    private String thread;
    private String logger;
    private String user;
    private String message;
    private String sourceFile;
    private long lineNumber;

    public LogEntry() {
    }

    public LogEntry(ZonedDateTime timestamp, String level, String thread, String logger, String user,
                    String message, String sourceFile, long lineNumber) {
        this.timestamp = timestamp;
        this.level = level;
        this.thread = thread;
        this.logger = logger;
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

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
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
        private String level;
        private String thread;
        private String logger;
        private String user;
        private String message;
        private String sourceFile;
        private long lineNumber;

        public Builder timestamp(ZonedDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder level(String level) {
            this.level = level;
            return this;
        }

        public Builder thread(String thread) {
            this.thread = thread;
            return this;
        }

        public Builder logger(String logger) {
            this.logger = logger;
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
            return new LogEntry(timestamp, level, thread, logger, user, message, sourceFile, lineNumber);
        }
    }
}
