package com.lsearch.logsearch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for an environment (e.g., d1, d2, uat1, prod1).
 * Contains multiple systems within the environment.
 */
public class EnvironmentConfig {

    private String name;                // Environment identifier (e.g., d1, d2, uat1)
    private String displayName;         // User-friendly name (e.g., "Development 1")
    private boolean enabled = true;     // Environment enabled/disabled
    private List<SystemConfig> systems = new ArrayList<>();

    public EnvironmentConfig() {
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<SystemConfig> getSystems() {
        return systems;
    }

    public void setSystems(List<SystemConfig> systems) {
        this.systems = systems;
    }

    /**
     * Convert all systems in this environment to DownloadLocation objects.
     * Handles both single-server and multi-server systems.
     */
    public List<DownloadLocation> toDownloadLocations() {
        List<DownloadLocation> locations = new ArrayList<>();

        if (!enabled || systems == null) {
            return locations;
        }

        for (SystemConfig system : systems) {
            if (system.isEnabled()) {
                locations.addAll(system.toDownloadLocations(name));
            }
        }

        return locations;
    }

    /**
     * Get total server count across all systems.
     */
    public int getTotalServerCount() {
        if (systems == null) {
            return 0;
        }
        return systems.stream()
                .filter(SystemConfig::isEnabled)
                .mapToInt(SystemConfig::getServerCount)
                .sum();
    }

    @Override
    public String toString() {
        return "EnvironmentConfig{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", enabled=" + enabled +
                ", systems=" + systems.size() +
                '}';
    }
}
