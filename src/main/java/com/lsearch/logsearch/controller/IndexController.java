package com.lsearch.logsearch.controller;

import com.lsearch.logsearch.model.IndexConfig;
import com.lsearch.logsearch.service.IndexConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for index management.
 * Provides endpoints for listing, filtering, and managing log indexes.
 */
@RestController
@RequestMapping("/api/indexes")
public class IndexController {

    private static final Logger log = LoggerFactory.getLogger(IndexController.class);

    private final IndexConfigService indexConfigService;

    public IndexController(IndexConfigService indexConfigService) {
        this.indexConfigService = indexConfigService;
    }

    /**
     * Get all indexes (for UI dropdown)
     * GET /api/indexes
     */
    @GetMapping
    public ResponseEntity<List<IndexSummary>> getIndexes(
            @RequestParam(required = false) String environment) {

        List<IndexConfig> configs;

        if (environment != null && !environment.isEmpty()) {
            configs = indexConfigService.getIndexesByEnvironment(environment);
        } else {
            configs = indexConfigService.getEnabledIndexes();
        }

        List<IndexSummary> summaries = configs.stream()
                .map(this::toIndexSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    /**
     * Get index names only (for quick lookup)
     * GET /api/indexes/names
     */
    @GetMapping("/names")
    public ResponseEntity<List<String>> getIndexNames() {
        return ResponseEntity.ok(indexConfigService.getIndexNames());
    }

    /**
     * Get index display names (for UI)
     * GET /api/indexes/display-names
     */
    @GetMapping("/display-names")
    public ResponseEntity<Map<String, String>> getIndexDisplayNames() {
        return ResponseEntity.ok(indexConfigService.getIndexDisplayNames());
    }

    /**
     * Get specific index details
     * GET /api/indexes/{indexName}
     */
    @GetMapping("/{indexName}")
    public ResponseEntity<IndexDetails> getIndexDetails(@PathVariable String indexName) {
        Optional<IndexConfig> configOpt = indexConfigService.getIndex(indexName);

        if (!configOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        IndexConfig config = configOpt.get();
        IndexDetails details = toIndexDetails(config);

        return ResponseEntity.ok(details);
    }

    /**
     * Get all environments
     * GET /api/environments
     */
    @GetMapping("/environments")
    public ResponseEntity<List<EnvironmentSummary>> getEnvironments() {
        Set<String> envs = indexConfigService.getEnvironments();

        List<EnvironmentSummary> summaries = envs.stream()
                .map(env -> {
                    List<IndexConfig> envIndexes = indexConfigService.getIndexesByEnvironment(env);
                    return new EnvironmentSummary(env, env.toUpperCase(), envIndexes.size());
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    /**
     * Get indexes for a specific environment
     * GET /api/environments/{environment}/indexes
     */
    @GetMapping("/environments/{environment}/indexes")
    public ResponseEntity<List<IndexSummary>> getIndexesByEnvironment(@PathVariable String environment) {
        List<IndexConfig> configs = indexConfigService.getIndexesByEnvironment(environment);

        List<IndexSummary> summaries = configs.stream()
                .map(this::toIndexSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    // Helper methods to convert models

    private IndexSummary toIndexSummary(IndexConfig config) {
        IndexSummary summary = new IndexSummary();
        summary.setName(config.getName());
        summary.setDisplayName(config.getDisplayName());
        summary.setServiceType(config.getServiceType().toString());
        summary.setEnvironment(config.getEnvironment());
        summary.setEnabled(config.isEnabled());
        summary.setPath(config.getPath());
        return summary;
    }

    private IndexDetails toIndexDetails(IndexConfig config) {
        IndexDetails details = new IndexDetails();
        details.setName(config.getName());
        details.setDisplayName(config.getDisplayName());
        details.setPath(config.getPath());
        details.setFilePattern(config.getFilePattern());
        details.setServiceType(config.getServiceType().toString());
        details.setRetentionDays(config.getRetentionDays());
        details.setAutoWatch(config.isAutoWatch());
        details.setEnabled(config.isEnabled());
        details.setEnvironment(config.getEnvironment());
        details.setServiceName(config.getServiceName());
        return details;
    }

    // Response DTOs

    public static class IndexSummary {
        private String name;
        private String displayName;
        private String serviceType;
        private String environment;
        private boolean enabled;
        private String path;

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

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class IndexDetails {
        private String name;
        private String displayName;
        private String path;
        private String filePattern;
        private String serviceType;
        private int retentionDays;
        private boolean autoWatch;
        private boolean enabled;
        private String environment;
        private String serviceName;

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

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    public static class EnvironmentSummary {
        private String name;
        private String displayName;
        private int indexCount;

        public EnvironmentSummary() {
        }

        public EnvironmentSummary(String name, String displayName, int indexCount) {
            this.name = name;
            this.displayName = displayName;
            this.indexCount = indexCount;
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

        public int getIndexCount() {
            return indexCount;
        }

        public void setIndexCount(int indexCount) {
            this.indexCount = indexCount;
        }
    }
}
