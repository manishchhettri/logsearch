package com.lsearch.logsearch.model;

/**
 * Configuration for a single index (log source).
 * Supports Splunk-style index management with environment tagging.
 */
public class IndexConfig {

    private String name;              // Index name (unique identifier, e.g., payment-prod1)
    private String displayName;       // User-friendly name
    private String path;              // Log directory path
    private String filePattern;       // Regex pattern for log files
    private boolean enabled = true;   // Index enabled/disabled
    private boolean autoWatch = false; // Auto-watch for new files
    private int retentionDays = 30;   // How long to keep indexed data
    private ServiceType serviceType = ServiceType.APPLICATION; // Type of service
    private String environment;       // Environment tag (d1, d2, uat1, prod1, etc.)

    public enum ServiceType {
        APPLICATION,
        INTEGRATION,
        DATABASE,
        ARCHIVE,
        CUSTOM
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoWatch() {
        return autoWatch;
    }

    public void setAutoWatch(boolean autoWatch) {
        this.autoWatch = autoWatch;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    /**
     * Extract application/service name from index name.
     * e.g., "payment-prod1" → "payment"
     */
    public String getServiceName() {
        if (name == null) {
            return null;
        }
        int dashIndex = name.lastIndexOf('-');
        return dashIndex > 0 ? name.substring(0, dashIndex) : name;
    }

    @Override
    public String toString() {
        return "IndexConfig{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", path='" + path + '\'' +
                ", environment='" + environment + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
