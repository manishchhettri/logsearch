package com.lsearch.logsearch.model;

/**
 * Unified configuration for log download location and index.
 * Combines download source (URL) with index configuration.
 */
public class DownloadLocation {

    // Download configuration
    private String name;
    private String url;
    private String description;
    private boolean enabled = true;
    private boolean requiresAuth = false;
    private String defaultUsername;

    // Index configuration
    private String targetPath;           // Where to save downloaded logs
    private String filePattern;          // Regex pattern for log files
    private String indexName;            // Index identifier (e.g., payment-uat1)
    private String environment;          // Environment tag (d1, d2, uat1, prod1)
    private int retentionDays = 30;      // How long to keep indexed data
    private boolean autoWatch = false;   // Auto-watch for new files
    private String serviceType = "APPLICATION"; // Type of service

    public DownloadLocation() {
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRequiresAuth() {
        return requiresAuth;
    }

    public void setRequiresAuth(boolean requiresAuth) {
        this.requiresAuth = requiresAuth;
    }

    public String getDefaultUsername() {
        return defaultUsername;
    }

    public void setDefaultUsername(String defaultUsername) {
        this.defaultUsername = defaultUsername;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
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

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isAutoWatch() {
        return autoWatch;
    }

    public void setAutoWatch(boolean autoWatch) {
        this.autoWatch = autoWatch;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    /**
     * Convert this DownloadLocation to an IndexConfig.
     */
    public IndexConfig toIndexConfig() {
        IndexConfig config = new IndexConfig();
        config.setName(indexName != null ? indexName : name);
        config.setDisplayName(name);
        config.setPath(targetPath);
        config.setFilePattern(filePattern);
        config.setEnabled(enabled);
        config.setAutoWatch(autoWatch);
        config.setRetentionDays(retentionDays);
        config.setEnvironment(environment);

        // Parse service type
        if (serviceType != null) {
            try {
                config.setServiceType(IndexConfig.ServiceType.valueOf(serviceType.toUpperCase()));
            } catch (IllegalArgumentException e) {
                config.setServiceType(IndexConfig.ServiceType.APPLICATION);
            }
        }

        return config;
    }

    @Override
    public String toString() {
        return "DownloadLocation{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", indexName='" + indexName + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", environment='" + environment + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
