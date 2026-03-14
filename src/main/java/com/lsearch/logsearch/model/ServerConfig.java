package com.lsearch.logsearch.model;

/**
 * Configuration for a single server URL within a system.
 * Allows multiple servers per system (e.g., CMS might have 4 servers).
 */
public class ServerConfig {

    private String name;                // Server name (e.g., "Server 1", "Node 1")
    private String url;                 // Download URL for this server
    private boolean enabled = true;     // Server enabled/disabled

    public ServerConfig() {
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
