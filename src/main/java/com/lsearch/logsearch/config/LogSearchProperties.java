package com.lsearch.logsearch.config;

import com.lsearch.logsearch.model.DownloadLocation;
import com.lsearch.logsearch.model.EnvironmentConfig;
import com.lsearch.logsearch.model.IndexConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "log-search")
public class LogSearchProperties {

    private String logsDir;
    private String indexDir;
    private String filePattern;
    private String filenameDatePattern;
    private String logLinePattern;
    private String logDatetimeFormat;
    private String timezone;
    private int retentionDays;
    private boolean autoWatch;
    private int watchInterval;
    private boolean enableFallback = true;

    // Hierarchical environment-based configuration
    private Map<String, EnvironmentConfig> environments;

    public String getLogsDir() {
        return logsDir;
    }

    public void setLogsDir(String logsDir) {
        this.logsDir = logsDir;
    }

    public String getIndexDir() {
        return indexDir;
    }

    public void setIndexDir(String indexDir) {
        this.indexDir = indexDir;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    public String getFilenameDatePattern() {
        return filenameDatePattern;
    }

    public void setFilenameDatePattern(String filenameDatePattern) {
        this.filenameDatePattern = filenameDatePattern;
    }

    public String getLogLinePattern() {
        return logLinePattern;
    }

    public void setLogLinePattern(String logLinePattern) {
        this.logLinePattern = logLinePattern;
    }

    public String getLogDatetimeFormat() {
        return logDatetimeFormat;
    }

    public void setLogDatetimeFormat(String logDatetimeFormat) {
        this.logDatetimeFormat = logDatetimeFormat;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
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

    public int getWatchInterval() {
        return watchInterval;
    }

    public void setWatchInterval(int watchInterval) {
        this.watchInterval = watchInterval;
    }

    public boolean isEnableFallback() {
        return enableFallback;
    }

    public void setEnableFallback(boolean enableFallback) {
        this.enableFallback = enableFallback;
    }

    public Map<String, EnvironmentConfig> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Map<String, EnvironmentConfig> environments) {
        this.environments = environments;
    }

    /**
     * Convert hierarchical environment structure to flat list of download locations.
     * This maintains compatibility with existing code.
     */
    public List<DownloadLocation> getDownloadLocations() {
        List<DownloadLocation> locations = new ArrayList<>();

        if (environments == null || environments.isEmpty()) {
            return locations;
        }

        // Convert each environment's systems to download locations
        for (Map.Entry<String, EnvironmentConfig> entry : environments.entrySet()) {
            String envKey = entry.getKey();
            EnvironmentConfig envConfig = entry.getValue();

            // Set environment name if not already set
            if (envConfig.getName() == null) {
                envConfig.setName(envKey);
            }

            locations.addAll(envConfig.toDownloadLocations());
        }

        return locations;
    }

    /**
     * Generate IndexConfig list from download locations.
     * Only includes locations that have targetPath configured.
     */
    public List<IndexConfig> getIndexes() {
        List<DownloadLocation> downloadLocations = getDownloadLocations();

        if (downloadLocations.isEmpty()) {
            return new ArrayList<>();
        }

        List<IndexConfig> indexes = new ArrayList<>();
        for (DownloadLocation location : downloadLocations) {
            // Only create index if targetPath is configured
            if (location.getTargetPath() != null && !location.getTargetPath().trim().isEmpty()) {
                indexes.add(location.toIndexConfig());
            }
        }
        return indexes;
    }
}
