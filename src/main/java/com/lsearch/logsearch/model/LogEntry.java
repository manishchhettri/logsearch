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

    // Multi-index support
    private String indexName;
    private String environment;

    // Integration platform support
    private String integrationPlatform;  // IIB, MQ, ESB, etc.
    private String correlationId;
    private String messageId;
    private String flowName;
    private String endpoint;

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

    // New constructor with index support
    public LogEntry(ZonedDateTime timestamp, String level, String thread, String logger, String user,
                    String message, String sourceFile, long lineNumber, String indexName, String environment) {
        this(timestamp, level, thread, logger, user, message, sourceFile, lineNumber);
        this.indexName = indexName;
        this.environment = environment;
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

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getIntegrationPlatform() {
        return integrationPlatform;
    }

    public void setIntegrationPlatform(String integrationPlatform) {
        this.integrationPlatform = integrationPlatform;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
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
        private String indexName;
        private String environment;
        private String integrationPlatform;
        private String correlationId;
        private String messageId;
        private String flowName;
        private String endpoint;

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

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder integrationPlatform(String integrationPlatform) {
            this.integrationPlatform = integrationPlatform;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder flowName(String flowName) {
            this.flowName = flowName;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public LogEntry build() {
            LogEntry entry = new LogEntry(timestamp, level, thread, logger, user, message, sourceFile, lineNumber);
            entry.setIndexName(indexName);
            entry.setEnvironment(environment);
            entry.setIntegrationPlatform(integrationPlatform);
            entry.setCorrelationId(correlationId);
            entry.setMessageId(messageId);
            entry.setFlowName(flowName);
            entry.setEndpoint(endpoint);
            return entry;
        }
    }
}
