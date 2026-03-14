package com.lsearch.logsearch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a system within an environment.
 * Represents a single application/service (e.g., CMS, Payment, IIB).
 * Supports both single-server and multi-server configurations.
 */
public class SystemConfig {

    private String name;                // System identifier (e.g., cms, payment, iib)
    private String displayName;         // User-friendly name

    // Single-server configuration (backward compatible)
    private String url;                 // Download URL for logs

    // Multi-server configuration (new)
    private List<ServerConfig> servers; // Multiple servers for this system

    private String targetPath;          // Where to save downloaded logs
    private String filePattern;         // Regex pattern for log files
    private boolean enabled = true;     // System enabled/disabled
    private boolean requiresAuth = false;
    private String defaultUsername;
    private int retentionDays = 30;
    private boolean autoWatch = false;
    private String serviceType = "APPLICATION";

    public SystemConfig() {
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public List<ServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }

    /**
     * Get all URLs for this system (single or multiple servers).
     */
    public List<String> getAllUrls() {
        List<String> urls = new ArrayList<>();

        // Multi-server configuration
        if (servers != null && !servers.isEmpty()) {
            for (ServerConfig server : servers) {
                if (server.isEnabled() && server.getUrl() != null) {
                    urls.add(server.getUrl());
                }
            }
        }
        // Single-server configuration (backward compatible)
        else if (url != null && !url.trim().isEmpty()) {
            urls.add(url);
        }

        return urls;
    }

    /**
     * Get server count for display.
     */
    public int getServerCount() {
        if (servers != null && !servers.isEmpty()) {
            return (int) servers.stream().filter(ServerConfig::isEnabled).count();
        }
        return url != null && !url.trim().isEmpty() ? 1 : 0;
    }

    /**
     * Convert this SystemConfig to DownloadLocation(s).
     * Returns one location per server if multi-server, or one location if single-server.
     */
    public List<DownloadLocation> toDownloadLocations(String environment) {
        List<DownloadLocation> locations = new ArrayList<>();

        // Multi-server configuration
        if (servers != null && !servers.isEmpty()) {
            for (ServerConfig server : servers) {
                if (server.isEnabled()) {
                    DownloadLocation location = createDownloadLocation(environment, server.getUrl(), server.getName());
                    locations.add(location);
                }
            }
        }
        // Single-server configuration (backward compatible)
        else if (url != null && !url.trim().isEmpty()) {
            DownloadLocation location = createDownloadLocation(environment, url, null);
            locations.add(location);
        }

        return locations;
    }

    private DownloadLocation createDownloadLocation(String environment, String serverUrl, String serverName) {
        DownloadLocation location = new DownloadLocation();

        String displayNameWithServer = displayName != null ? displayName : name;
        if (serverName != null) {
            displayNameWithServer += " - " + serverName;
        }

        location.setName(displayNameWithServer);
        location.setUrl(serverUrl);
        location.setDescription(displayNameWithServer + " (" + environment.toUpperCase() + ")");
        location.setEnabled(enabled);
        location.setRequiresAuth(requiresAuth);
        location.setDefaultUsername(defaultUsername);
        location.setTargetPath(targetPath);
        location.setFilePattern(filePattern);
        location.setIndexName(name + "-" + environment);
        location.setEnvironment(environment);
        location.setRetentionDays(retentionDays);
        location.setAutoWatch(autoWatch);
        location.setServiceType(serviceType);

        return location;
    }

    @Override
    public String toString() {
        return "SystemConfig{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", servers=" + getServerCount() +
                ", enabled=" + enabled +
                '}';
    }
}
