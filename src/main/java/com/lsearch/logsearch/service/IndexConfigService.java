package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.IndexConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing index configurations.
 * Supports both multi-index mode and legacy single-directory mode for backward compatibility.
 */
@Service
public class IndexConfigService {

    private static final Logger log = LoggerFactory.getLogger(IndexConfigService.class);

    private final LogSearchProperties properties;
    private final Map<String, IndexConfig> indexes;

    public IndexConfigService(LogSearchProperties properties) {
        this.properties = properties;
        this.indexes = new LinkedHashMap<>();
    }

    @PostConstruct
    public void initialize() {
        loadIndexConfigurations();
        validateIndexes();
    }

    private void loadIndexConfigurations() {
        List<IndexConfig> indexConfigs = properties.getIndexes();

        if (indexConfigs == null || indexConfigs.isEmpty()) {
            log.info("No indexes configured, using legacy single-directory mode");
            createLegacyIndex();
            return;
        }

        for (IndexConfig config : indexConfigs) {
            if (config.isEnabled()) {
                indexes.put(config.getName(), config);
                log.info("Loaded index: {} -> {} (environment: {})",
                        config.getName(), config.getPath(), config.getEnvironment());
            } else {
                log.debug("Index disabled: {}", config.getName());
            }
        }

        log.info("Loaded {} enabled indexes", indexes.size());
    }

    /**
     * Creates a legacy "main" index from the single logs-dir configuration.
     * Provides backward compatibility for existing deployments.
     */
    private void createLegacyIndex() {
        IndexConfig legacy = new IndexConfig();
        legacy.setName("main");
        legacy.setDisplayName("Main Logs");
        legacy.setPath(properties.getLogsDir());
        legacy.setFilePattern(properties.getFilePattern());
        legacy.setEnabled(true);
        legacy.setAutoWatch(properties.isAutoWatch());
        legacy.setRetentionDays(properties.getRetentionDays());
        legacy.setServiceType(IndexConfig.ServiceType.APPLICATION);
        legacy.setEnvironment("default");

        indexes.put("main", legacy);
        log.info("Created legacy index 'main' from logs-dir: {}", properties.getLogsDir());
    }

    private void validateIndexes() {
        for (IndexConfig config : indexes.values()) {
            Path indexPath = Paths.get(config.getPath());

            if (!Files.exists(indexPath)) {
                log.warn("Index path does not exist: {} ({})", config.getName(), config.getPath());
            } else if (!Files.isDirectory(indexPath)) {
                log.error("Index path is not a directory: {} ({})", config.getName(), config.getPath());
            } else {
                log.debug("Index path validated: {} -> {}", config.getName(), config.getPath());
            }
        }
    }

    /**
     * Get all enabled indexes
     */
    public List<IndexConfig> getEnabledIndexes() {
        return new ArrayList<>(indexes.values());
    }

    /**
     * Get specific index by name
     */
    public Optional<IndexConfig> getIndex(String name) {
        return Optional.ofNullable(indexes.get(name));
    }

    /**
     * Get index names (for UI dropdown)
     */
    public List<String> getIndexNames() {
        return new ArrayList<>(indexes.keySet());
    }

    /**
     * Get index display names (for UI)
     */
    public Map<String, String> getIndexDisplayNames() {
        return indexes.values().stream()
                .collect(Collectors.toMap(
                        IndexConfig::getName,
                        IndexConfig::getDisplayName
                ));
    }

    /**
     * Get all unique environments from all indexes
     */
    public Set<String> getEnvironments() {
        return indexes.values().stream()
                .map(IndexConfig::getEnvironment)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Get indexes for a specific environment
     */
    public List<IndexConfig> getIndexesByEnvironment(String environment) {
        return indexes.values().stream()
                .filter(config -> environment.equals(config.getEnvironment()))
                .collect(Collectors.toList());
    }

    /**
     * Check if multi-index mode is enabled
     */
    public boolean isMultiIndexMode() {
        return indexes.size() > 1 || !indexes.containsKey("main");
    }
}
