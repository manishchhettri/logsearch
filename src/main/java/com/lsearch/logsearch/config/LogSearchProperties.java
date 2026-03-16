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

    // Chunking configuration
    private ChunkingConfig chunking = new ChunkingConfig();

    // Service name configuration
    private String serviceName = "default-service";
    private String serviceNamePattern = "([^/]+)/.*\\.log";

    // Metadata configuration
    private MetadataConfig metadata = new MetadataConfig();

    // Context view configuration
    private ContextConfig context = new ContextConfig();

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
     * Get all download locations from hierarchical environment configuration.
     * Converts: Environment → System → Servers into flat list of DownloadLocations
     * for runtime processing while preserving metadata.
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

    public ChunkingConfig getChunking() {
        return chunking;
    }

    public void setChunking(ChunkingConfig chunking) {
        this.chunking = chunking;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceNamePattern() {
        return serviceNamePattern;
    }

    public void setServiceNamePattern(String serviceNamePattern) {
        this.serviceNamePattern = serviceNamePattern;
    }

    public MetadataConfig getMetadata() {
        return metadata;
    }

    public void setMetadata(MetadataConfig metadata) {
        this.metadata = metadata;
    }

    public ContextConfig getContext() {
        return context;
    }

    public void setContext(ContextConfig context) {
        this.context = context;
    }

    // Nested configuration classes

    public static class ChunkingConfig {
        private boolean enabled = true;
        private String strategy = "ADAPTIVE";
        private AdaptiveConfig adaptive = new AdaptiveConfig();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public AdaptiveConfig getAdaptive() {
            return adaptive;
        }

        public void setAdaptive(AdaptiveConfig adaptive) {
            this.adaptive = adaptive;
        }
    }

    public static class AdaptiveConfig {
        private int targetSizeMb = 200;
        private int minDurationMinutes = 15;
        private int maxDurationHours = 6;

        public int getTargetSizeMb() {
            return targetSizeMb;
        }

        public void setTargetSizeMb(int targetSizeMb) {
            this.targetSizeMb = targetSizeMb;
        }

        public int getMinDurationMinutes() {
            return minDurationMinutes;
        }

        public void setMinDurationMinutes(int minDurationMinutes) {
            this.minDurationMinutes = minDurationMinutes;
        }

        public int getMaxDurationHours() {
            return maxDurationHours;
        }

        public void setMaxDurationHours(int maxDurationHours) {
            this.maxDurationHours = maxDurationHours;
        }
    }

    public static class MetadataConfig {
        private int topTermsCount = 50;
        private boolean enablePackageExtraction = true;
        private boolean enableExceptionExtraction = true;
        private BloomFilterConfig bloomFilter = new BloomFilterConfig();

        public int getTopTermsCount() {
            return topTermsCount;
        }

        public void setTopTermsCount(int topTermsCount) {
            this.topTermsCount = topTermsCount;
        }

        public boolean isEnablePackageExtraction() {
            return enablePackageExtraction;
        }

        public void setEnablePackageExtraction(boolean enablePackageExtraction) {
            this.enablePackageExtraction = enablePackageExtraction;
        }

        public boolean isEnableExceptionExtraction() {
            return enableExceptionExtraction;
        }

        public void setEnableExceptionExtraction(boolean enableExceptionExtraction) {
            this.enableExceptionExtraction = enableExceptionExtraction;
        }

        public BloomFilterConfig getBloomFilter() {
            return bloomFilter;
        }

        public void setBloomFilter(BloomFilterConfig bloomFilter) {
            this.bloomFilter = bloomFilter;
        }
    }

    public static class BloomFilterConfig {
        private boolean enabled = true;
        private double falsePositiveRate = 0.01;
        private int estimatedTermsPerChunk = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getFalsePositiveRate() {
            return falsePositiveRate;
        }

        public void setFalsePositiveRate(double falsePositiveRate) {
            this.falsePositiveRate = falsePositiveRate;
        }

        public int getEstimatedTermsPerChunk() {
            return estimatedTermsPerChunk;
        }

        public void setEstimatedTermsPerChunk(int estimatedTermsPerChunk) {
            this.estimatedTermsPerChunk = estimatedTermsPerChunk;
        }
    }

    public static class ContextConfig {
        private int defaultLines = 500;
        private int minLines = 10;
        private int maxLines = 1000;
        private int step = 10;

        public int getDefaultLines() {
            return defaultLines;
        }

        public void setDefaultLines(int defaultLines) {
            this.defaultLines = defaultLines;
        }

        public int getMinLines() {
            return minLines;
        }

        public void setMinLines(int minLines) {
            this.minLines = minLines;
        }

        public int getMaxLines() {
            return maxLines;
        }

        public void setMaxLines(int maxLines) {
            this.maxLines = maxLines;
        }

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }
    }
}
