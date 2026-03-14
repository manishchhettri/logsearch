# Multi-Index Architecture (Splunk-Style Index Management)

## Overview

This document outlines the implementation plan for **multi-index support**, enabling LogSearch to manage multiple log sources with index-based filtering similar to Splunk.

## Problem Statement

### Current Limitation

**Single log directory:**
```yaml
log-search:
  logs-dir: ./logs  # All logs in one location
```

Issues:
- Cannot handle logs from different applications/servers
- No organizational structure
- Difficult to filter by source system
- Logs must be copied to single directory

### Target Use Case

**Enterprise deployment with multiple applications:**

```
Logs from different sources:
- Payment Service → /var/log/payment-service/
- Order Service → /var/log/order-service/
- Auth Service → /var/log/auth-service/
- IIB Integration → /opt/ibm/iib/logs/
- Database Logs → /var/log/postgresql/
```

Each source should be:
- Independently configured
- Searchable by index name
- Visible in UI for filtering
- Independently managed (retention, patterns, etc.)

## Solution: Splunk-Style Index Management

### Index Concept

An **index** is a named collection of logs from a specific source location.

**Splunk model:**
```
index="payment"
index="order"
index="auth"
```

**LogSearch equivalent with environment support:**
```
Each index:
  - Has a unique name (e.g., payment-prod1, order-uat1)
  - Points to a specific log directory
  - Tagged with environment (d1, d2, d3, uat1, prod1, etc.)
  - Can have independent configuration
  - Appears in UI dropdown for filtering
```

**Two-Dimensional Filtering:**

LogSearch supports filtering by **both index and environment**:

```
Dimension 1: Application/Service (logical grouping)
  - payment, order, auth, iib, etc.

Dimension 2: Environment (deployment instance)
  - d1, d2, d3 (dev environments)
  - uat1, uat2 (UAT environments)
  - prod1, prod2 (production environments)
```

**Search Matrix:**

```
               | d1   | d2   | d3   | uat1  | prod1 |
---------------|------|------|------|-------|-------|
payment        |  ✓   |  ✓   |  ✓   |   ✓   |   ✓   |
order          |  ✓   |      |      |   ✓   |   ✓   |
auth           |      |      |      |   ✓   |   ✓   |
iib            |      |      |      |   ✓   |   ✓   |
```

**Query Examples:**

```
# All payment errors in prod
index:payment-prod1 level:ERROR

# Payment errors across all environments
index:payment-* level:ERROR

# All prod errors (any application)
env:prod1 level:ERROR

# UAT errors for payment and order
env:uat1 index:(payment-uat1 OR order-uat1) level:ERROR

# Specific environment + specific service
env:d1 AND index:payment-d1 timeout
```

## Architecture Design

### Configuration Model

**application.yml:**

```yaml
log-search:
  # Legacy single-directory mode (backward compatible)
  logs-dir: ./logs  # Used if no indexes configured

  # NEW: Multi-index configuration with environment support
  indexes:
    # Payment Service - Multiple Environments
    - name: payment-d1
      display-name: "Payment Service (D1)"
      path: /var/log/d1/payment-service
      file-pattern: "*.log"
      enabled: true
      auto-watch: false
      retention-days: 7
      service-type: application
      environment: d1        # NEW: Environment tag

    - name: payment-d2
      display-name: "Payment Service (D2)"
      path: /var/log/d2/payment-service
      file-pattern: "*.log"
      enabled: true
      auto-watch: false
      retention-days: 7
      service-type: application
      environment: d2

    - name: payment-uat1
      display-name: "Payment Service (UAT1)"
      path: /var/log/uat1/payment-service
      file-pattern: "*.log"
      enabled: true
      auto-watch: false
      retention-days: 30
      service-type: application
      environment: uat1

    - name: payment-prod1
      display-name: "Payment Service (PROD1)"
      path: /var/log/prod1/payment-service
      file-pattern: "*.log"
      enabled: true
      auto-watch: false
      retention-days: 90     # Keep prod logs longer
      service-type: application
      environment: prod1

    # Order Service - Multiple Environments
    - name: order-d1
      display-name: "Order Service (D1)"
      path: /var/log/d1/order-service
      file-pattern: "order-*.log"
      enabled: true
      auto-watch: false
      retention-days: 7
      service-type: application
      environment: d1

    - name: order-uat1
      display-name: "Order Service (UAT1)"
      path: /var/log/uat1/order-service
      file-pattern: "order-*.log"
      enabled: true
      auto-watch: false
      retention-days: 30
      service-type: application
      environment: uat1

    - name: order-prod1
      display-name: "Order Service (PROD1)"
      path: /var/log/prod1/order-service
      file-pattern: "order-*.log"
      enabled: true
      auto-watch: false
      retention-days: 90
      service-type: application
      environment: prod1

    # Auth Service - Multiple Environments
    - name: auth-uat1
      display-name: "Authentication Service (UAT1)"
      path: /var/log/uat1/auth-service
      file-pattern: "auth-*.log"
      enabled: true
      auto-watch: false
      retention-days: 30
      service-type: application
      environment: uat1

    - name: auth-prod1
      display-name: "Authentication Service (PROD1)"
      path: /var/log/prod1/auth-service
      file-pattern: "auth-*.log"
      enabled: true
      auto-watch: false
      retention-days: 90
      service-type: application
      environment: prod1

    - name: iib
      display-name: "IBM Integration Bus"
      path: /opt/ibm/iib/logs
      file-pattern: "*.log"
      enabled: true
      auto-watch: false
      retention-days: 30
      service-type: integration

    - name: database
      display-name: "PostgreSQL Logs"
      path: /var/log/postgresql
      file-pattern: "postgresql-*.log"
      enabled: true
      auto-watch: false
      retention-days: 7
      service-type: database

    - name: archive
      display-name: "Archived Logs"
      path: /mnt/archives/logs
      file-pattern: "*.log.gz"  # Support compressed logs
      enabled: false  # Disabled until needed
      auto-watch: false
      retention-days: 365
      service-type: archive
```

### Index Storage Structure

```
.log-search/
├── indexes/
│   ├── metadata/
│   │   └── chunks/
│   │
│   └── content/
│       ├── payment/              # Index: payment
│       │   ├── 2026-03-12/
│       │   └── 2026-03-13/
│       │
│       ├── order/                # Index: order
│       │   ├── 2026-03-12/
│       │   └── 2026-03-13/
│       │
│       ├── auth/                 # Index: auth
│       │   └── 2026-03-12/
│       │
│       ├── iib/                  # Index: iib
│       │   └── 2026-03-12/
│       │
│       └── database/             # Index: database
│           └── 2026-03-12/
```

### Data Model

**IndexConfig.java:**

```java
package com.lsearch.logsearch.model;

public class IndexConfig {
    private String name;              // Index name (unique identifier, e.g., payment-prod1)
    private String displayName;       // User-friendly name
    private String path;              // Log directory path
    private String filePattern;       // Regex pattern for log files
    private boolean enabled;          // Index enabled/disabled
    private boolean autoWatch;        // Auto-watch for new files
    private int retentionDays;        // How long to keep indexed data
    private ServiceType serviceType;  // application, integration, database, archive
    private String environment;       // NEW: Environment tag (d1, d2, uat1, prod1, etc.)

    public enum ServiceType {
        APPLICATION,
        INTEGRATION,
        DATABASE,
        ARCHIVE,
        CUSTOM
    }

    // Getters and setters

    /**
     * Extract application/service name from index name
     * e.g., "payment-prod1" → "payment"
     */
    public String getServiceName() {
        int dashIndex = name.lastIndexOf('-');
        return dashIndex > 0 ? name.substring(0, dashIndex) : name;
    }
}
```

**IndexMetadata.java:**

```java
package com.lsearch.logsearch.model;

import java.time.ZonedDateTime;

public class IndexMetadata {
    private String indexName;
    private long totalDocuments;
    private long sizeBytes;
    private ZonedDateTime lastIndexed;
    private ZonedDateTime oldestLog;
    private ZonedDateTime newestLog;
    private int fileCount;
    private String status;  // ACTIVE, DISABLED, ERROR

    // Getters and setters
}
```

## Implementation Plan

### Phase 1: Index Configuration Service

**Goal:** Load and manage index configurations

**IndexConfigService.java:**

```java
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
                log.info("Loaded index: {} -> {}", config.getName(), config.getPath());
            } else {
                log.debug("Index disabled: {}", config.getName());
            }
        }

        log.info("Loaded {} enabled indexes", indexes.size());
    }

    private void createLegacyIndex() {
        // Backward compatibility: create "main" index from logs-dir
        IndexConfig legacy = new IndexConfig();
        legacy.setName("main");
        legacy.setDisplayName("Main Logs");
        legacy.setPath(properties.getLogsDir());
        legacy.setFilePattern(properties.getFilePattern());
        legacy.setEnabled(true);
        legacy.setAutoWatch(properties.isAutoWatch());
        legacy.setRetentionDays(properties.getRetentionDays());
        legacy.setServiceType(IndexConfig.ServiceType.APPLICATION);

        indexes.put("main", legacy);
        log.info("Created legacy index 'main' from logs-dir");
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
}
```

### Phase 2: Multi-Index Indexer

**Goal:** Index logs from all configured indexes

**Modify: LogFileIndexer.java**

```java
@Service
public class LogFileIndexer {

    private final IndexConfigService indexConfigService;
    private final LogParser logParser;
    private final LuceneIndexService luceneIndexService;

    /**
     * Index all enabled indexes
     */
    public void indexAllLogs() throws IOException {
        log.info("Starting multi-index indexing...");

        List<IndexConfig> indexes = indexConfigService.getEnabledIndexes();

        for (IndexConfig indexConfig : indexes) {
            log.info("Indexing: {} ({})", indexConfig.getDisplayName(), indexConfig.getName());
            indexIndex(indexConfig);
        }

        log.info("Multi-index indexing completed for {} indexes", indexes.size());
    }

    /**
     * Index a single index
     */
    private void indexIndex(IndexConfig indexConfig) throws IOException {
        Path indexPath = Paths.get(indexConfig.getPath());

        if (!Files.exists(indexPath)) {
            log.warn("Index path does not exist, skipping: {}", indexConfig.getName());
            return;
        }

        Pattern filePattern = Pattern.compile(indexConfig.getFilePattern());
        AtomicInteger processedFiles = new AtomicInteger(0);

        // Recursive scan with file pattern matching
        try (Stream<Path> paths = Files.walk(indexPath)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> filePattern.matcher(path.getFileName().toString()).matches())
                .parallel()
                .forEach(path -> {
                    indexLogFile(path, indexConfig);
                    processedFiles.incrementAndGet();
                });
        }

        log.info("Indexed {} files for index: {}", processedFiles.get(), indexConfig.getName());
    }

    /**
     * Index a single log file
     */
    private void indexLogFile(Path logFile, IndexConfig indexConfig) {
        String filename = logFile.getFileName().toString();
        String indexName = indexConfig.getName();

        log.info("Indexing file: {} (index: {})", filename, indexName);

        // Parse and index with index name metadata
        // ... existing parsing logic ...

        // Add index name to each log entry
        for (LogEntry entry : entries) {
            entry.setIndexName(indexName);
            luceneIndexService.indexLogEntry(entry);
        }
    }

    /**
     * Scheduled auto-watch for all indexes
     */
    @Scheduled(fixedDelayString = "${log-search.watch-interval}000", initialDelay = 60000)
    public void watchForNewLogs() {
        List<IndexConfig> indexes = indexConfigService.getEnabledIndexes();

        for (IndexConfig indexConfig : indexes) {
            if (indexConfig.isAutoWatch()) {
                try {
                    watchIndex(indexConfig);
                } catch (Exception e) {
                    log.error("Error watching index: {}", indexConfig.getName(), e);
                }
            }
        }
    }
}
```

### Phase 3: Index-Based Search

**Goal:** Search specific indexes or all indexes

**Modify: LogSearchService.java**

```java
@Service
public class LogSearchService {

    private final IndexConfigService indexConfigService;

    /**
     * Search with optional index filter
     */
    public SearchResponse search(SearchRequest request) throws IOException {
        List<String> targetIndexes = determineTargetIndexes(request);

        log.info("Searching indexes: {}", targetIndexes);

        // Build query with index filter
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.add(parseUserQuery(request.getQuery()), BooleanClause.Occur.MUST);

        // Add index filter if specified
        if (!targetIndexes.isEmpty()) {
            BooleanQuery.Builder indexFilter = new BooleanQuery.Builder();
            for (String indexName : targetIndexes) {
                indexFilter.add(
                    new TermQuery(new Term("indexName", indexName)),
                    BooleanClause.Occur.SHOULD
                );
            }
            queryBuilder.add(indexFilter.build(), BooleanClause.Occur.MUST);
        }

        // Execute search
        return executeSearch(queryBuilder.build(), request);
    }

    /**
     * Determine which indexes to search
     */
    private List<String> determineTargetIndexes(SearchRequest request) {
        // Check if user specified index filter
        if (request.getIndexes() != null && !request.getIndexes().isEmpty()) {
            return request.getIndexes();
        }

        // Search all enabled indexes
        return indexConfigService.getIndexNames();
    }
}
```

**SearchRequest.java enhancement:**

```java
public class SearchRequest {
    private String query;
    private Long startTime;
    private Long endTime;
    private int pageSize;

    // NEW: Index filtering
    private List<String> indexes;  // Search specific indexes (null = all)

    // Getters and setters
}
```

### Phase 4: REST API Enhancements

**Goal:** Expose index management and filtering in API

**IndexController.java (NEW):**

```java
package com.lsearch.logsearch.controller;

import com.lsearch.logsearch.model.IndexConfig;
import com.lsearch.logsearch.model.IndexMetadata;
import com.lsearch.logsearch.service.IndexConfigService;
import com.lsearch.logsearch.service.IndexMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indexes")
public class IndexController {

    private final IndexConfigService indexConfigService;
    private final IndexMetadataService indexMetadataService;

    public IndexController(IndexConfigService indexConfigService,
                            IndexMetadataService indexMetadataService) {
        this.indexConfigService = indexConfigService;
        this.indexMetadataService = indexMetadataService;
    }

    /**
     * Get all indexes (for UI dropdown)
     */
    @GetMapping
    public ResponseEntity<List<IndexSummary>> getIndexes() {
        List<IndexConfig> configs = indexConfigService.getEnabledIndexes();
        List<IndexMetadata> metadatas = indexMetadataService.getAllIndexMetadata();

        List<IndexSummary> summaries = buildIndexSummaries(configs, metadatas);
        return ResponseEntity.ok(summaries);
    }

    /**
     * Get index details
     */
    @GetMapping("/{indexName}")
    public ResponseEntity<IndexDetails> getIndexDetails(@PathVariable String indexName) {
        IndexConfig config = indexConfigService.getIndex(indexName)
            .orElseThrow(() -> new IllegalArgumentException("Index not found: " + indexName));

        IndexMetadata metadata = indexMetadataService.getIndexMetadata(indexName);

        IndexDetails details = new IndexDetails(config, metadata);
        return ResponseEntity.ok(details);
    }

    /**
     * Trigger reindex for specific index
     */
    @PostMapping("/{indexName}/reindex")
    public ResponseEntity<Void> reindexIndex(@PathVariable String indexName) {
        // Trigger reindex for this index only
        indexMetadataService.reindex(indexName);
        return ResponseEntity.ok().build();
    }

    /**
     * Get index statistics
     */
    @GetMapping("/{indexName}/stats")
    public ResponseEntity<IndexStats> getIndexStats(@PathVariable String indexName) {
        IndexStats stats = indexMetadataService.getStats(indexName);
        return ResponseEntity.ok(stats);
    }
}
```

**Response Models:**

```java
public class IndexSummary {
    private String name;
    private String displayName;
    private ServiceType serviceType;
    private long documentCount;
    private String status;
}

public class IndexDetails {
    private String name;
    private String displayName;
    private String path;
    private String filePattern;
    private ServiceType serviceType;
    private int retentionDays;
    private long totalDocuments;
    private long sizeBytes;
    private ZonedDateTime lastIndexed;
}

public class IndexStats {
    private String indexName;
    private long totalLogs;
    private long errorCount;
    private long warnCount;
    private long infoCount;
    private Map<String, Long> topExceptions;
}
```

### Phase 5: UI Integration

**Goal:** Show index and environment selectors in search UI

**Modify: index.html**

```html
<!-- Two-Dimensional Filtering: Index × Environment (NEW) -->
<div class="filter-section">
    <div class="filter-row">
        <!-- Environment Selector -->
        <div class="filter-column">
            <label for="envSelector">Environment:</label>
            <select id="envSelector" multiple>
                <option value="*" selected>All Environments</option>
                <!-- Populated from /api/environments -->
            </select>
        </div>

        <!-- Index Selector -->
        <div class="filter-column">
            <label for="indexSelector">Indexes:</label>
            <select id="indexSelector" multiple>
                <option value="*" selected>All Indexes</option>
                <!-- Populated from /api/indexes -->
            </select>
        </div>
    </div>
</div>

<script>
// Load environments on page load
async function loadEnvironments() {
    const response = await fetch('/api/environments');
    const environments = await response.json();

    const selector = document.getElementById('envSelector');

    environments.forEach(env => {
        const option = document.createElement('option');
        option.value = env.name;
        option.textContent = `${env.displayName} (${env.indexCount} indexes)`;
        selector.appendChild(option);
    });
}

// Load indexes on page load (grouped by environment)
async function loadIndexes() {
    const response = await fetch('/api/indexes');
    const indexes = await response.json();

    const selector = document.getElementById('indexSelector');

    // Group by environment for better UX
    const byEnv = indexes.reduce((acc, index) => {
        const env = index.environment || 'other';
        if (!acc[env]) acc[env] = [];
        acc[env].push(index);
        return acc;
    }, {});

    // Add optgroups for each environment
    Object.entries(byEnv).forEach(([env, envIndexes]) => {
        const optgroup = document.createElement('optgroup');
        optgroup.label = env.toUpperCase();

        envIndexes.forEach(index => {
            const option = document.createElement('option');
            option.value = index.name;
            option.textContent = `${index.getServiceName()} (${index.documentCount} docs)`;
            option.dataset.environment = env;
            optgroup.appendChild(option);
        });

        selector.appendChild(optgroup);
    });
}

// Filter indexes by selected environment
document.getElementById('envSelector').addEventListener('change', function() {
    const selectedEnvs = Array.from(this.selectedOptions).map(opt => opt.value);
    const indexSelector = document.getElementById('indexSelector');
    const allOptions = indexSelector.querySelectorAll('option[data-environment]');

    allOptions.forEach(opt => {
        const envMatch = selectedEnvs.includes('*') ||
                         selectedEnvs.includes(opt.dataset.environment);
        opt.style.display = envMatch ? 'block' : 'none';
    });
});

// Include selected indexes and environments in search request
function buildSearchRequest() {
    const selectedEnvs = Array.from(
        document.getElementById('envSelector').selectedOptions
    ).map(opt => opt.value);

    const selectedIndexes = Array.from(
        document.getElementById('indexSelector').selectedOptions
    ).map(opt => opt.value);

    return {
        query: document.getElementById('searchQuery').value,
        startTime: getStartTime(),
        endTime: getEndTime(),
        environments: selectedEnvs.includes('*') ? null : selectedEnvs,
        indexes: selectedIndexes.includes('*') ? null : selectedIndexes
    };
}

loadEnvironments();
loadIndexes();
</script>
```

**Index Management Page (NEW):**

```html
<!-- Dashboards tab renamed to "Indexes & Dashboards" -->
<div class="tab-content" id="indexesTab">
    <h2>Configured Indexes</h2>

    <table class="index-table">
        <thead>
            <tr>
                <th>Index Name</th>
                <th>Display Name</th>
                <th>Type</th>
                <th>Documents</th>
                <th>Status</th>
                <th>Last Indexed</th>
                <th>Actions</th>
            </tr>
        </thead>
        <tbody id="indexTableBody">
            <!-- Populated from /api/indexes -->
        </tbody>
    </table>
</div>

<script>
async function loadIndexTable() {
    const response = await fetch('/api/indexes');
    const indexes = await response.json();

    const tbody = document.getElementById('indexTableBody');
    tbody.innerHTML = '';

    indexes.forEach(index => {
        const row = `
            <tr>
                <td><code>${index.name}</code></td>
                <td>${index.displayName}</td>
                <td>${index.serviceType}</td>
                <td>${index.documentCount.toLocaleString()}</td>
                <td><span class="status ${index.status}">${index.status}</span></td>
                <td>${formatDate(index.lastIndexed)}</td>
                <td>
                    <button onclick="reindexIndex('${index.name}')">Re-Index</button>
                    <button onclick="viewIndexDetails('${index.name}')">Details</button>
                </td>
            </tr>
        `;
        tbody.innerHTML += row;
    });
}
</script>
```

## Configuration Examples

### Example 1: Enterprise Application Stack

```yaml
log-search:
  indexes:
    - name: payment
      display-name: "Payment Service"
      path: /var/log/payment-service
      file-pattern: "payment-*.log"
      enabled: true
      retention-days: 90
      service-type: application

    - name: order
      display-name: "Order Service"
      path: /var/log/order-service
      file-pattern: "order-*.log"
      enabled: true
      retention-days: 90
      service-type: application

    - name: iib
      display-name: "Integration Bus"
      path: /opt/ibm/iib/logs
      file-pattern: "*.log"
      enabled: true
      retention-days: 30
      service-type: integration
```

### Example 2: Multi-Tenant SaaS

```yaml
log-search:
  indexes:
    - name: tenant_a
      display-name: "Tenant A Logs"
      path: /var/log/tenants/tenant-a
      file-pattern: "*.log"
      enabled: true
      retention-days: 30
      service-type: application

    - name: tenant_b
      display-name: "Tenant B Logs"
      path: /var/log/tenants/tenant-b
      file-pattern: "*.log"
      enabled: true
      retention-days: 30
      service-type: application
```

### Example 3: Staged Archives

```yaml
log-search:
  indexes:
    - name: current
      display-name: "Current Logs (Last 7 Days)"
      path: /var/log/current
      file-pattern: "*.log"
      enabled: true
      auto-watch: true
      retention-days: 7
      service-type: application

    - name: archive_monthly
      display-name: "Monthly Archives"
      path: /mnt/archives/monthly
      file-pattern: "*.log.gz"
      enabled: true
      auto-watch: false
      retention-days: 90
      service-type: archive

    - name: archive_yearly
      display-name: "Yearly Archives"
      path: /mnt/archives/yearly
      file-pattern: "*.log.gz"
      enabled: false  # Enable only when needed
      auto-watch: false
      retention-days: 365
      service-type: archive
```

## Search Query Syntax

### Filter by Index

```
# Search all indexes (default)
error

# Search specific index
index:payment-prod1 error

# Search multiple indexes
index:(payment-prod1 OR order-prod1) error

# Wildcard index search (all payment indexes across all environments)
index:payment-* error

# Complex query
index:payment-prod1 level:ERROR correlationId:*
```

### Filter by Environment

```
# Search specific environment (all applications in prod1)
env:prod1 error

# Search multiple environments
env:(uat1 OR prod1) error

# All dev environments
env:d* error

# Exclude production
env:* NOT env:prod* error
```

### Combined Index + Environment Filtering

```
# Payment errors in production
index:payment-* env:prod1 level:ERROR

# All applications in UAT with timeouts
env:uat1 timeout

# Payment or Order in dev environments
env:d* index:(payment-* OR order-*) error

# Specific correlation across all prod services
env:prod1 correlationId:txn-abc-123

# Integration errors in UAT
env:uat1 service-type:integration level:ERROR
```

### Real-World Query Examples

**1. Compare Same Issue Across Environments:**
```
# Search payment timeouts in all environments to compare behavior
index:payment-* timeout

Results grouped by environment:
- payment-d1: 5 occurrences
- payment-uat1: 12 occurrences
- payment-prod1: 3 occurrences
```

**2. Production-Only Error Search:**
```
# Find all production errors across all services
env:prod1 level:ERROR

# Or more specifically
env:prod* level:ERROR
```

**3. Service-Specific Across Environments:**
```
# All payment service errors (dev + uat + prod)
index:payment-* level:ERROR

# Payment errors only in non-prod
index:payment-* NOT env:prod* level:ERROR
```

**4. Integration Platform Errors:**
```
# IIB errors in production
index:iib-prod1 level:ERROR

# All integration platforms in UAT
env:uat1 service-type:integration level:ERROR
```

**5. Correlation Tracing Across Environments:**
```
# Find correlation ID in UAT environment
env:uat1 correlationId:txn-test-456

# Same correlation in prod (for comparison)
env:prod1 correlationId:txn-prod-789
```

### API Usage

```bash
# Search all indexes and environments
curl "http://localhost:8080/api/search?query=error"

# Search specific environment
curl "http://localhost:8080/api/search?query=error&environments=prod1"

# Search specific indexes in specific environment
curl "http://localhost:8080/api/search?query=error&environments=prod1&indexes=payment-prod1,order-prod1"

# Search multiple environments
curl "http://localhost:8080/api/search?query=error&environments=d1,d2,uat1"

# Get environment list
curl "http://localhost:8080/api/environments"

# Get all indexes for specific environment
curl "http://localhost:8080/api/environments/prod1/indexes"

# Get index list
curl "http://localhost:8080/api/indexes"

# Get indexes filtered by environment
curl "http://localhost:8080/api/indexes?environment=prod1"

# Get index details
curl "http://localhost:8080/api/indexes/payment-prod1"
```

### Advanced Query Patterns

**Environment Comparison:**
```bash
# Get error count per environment for payment service
for env in d1 d2 uat1 prod1; do
  echo "Environment: $env"
  curl "http://localhost:8080/api/search?query=index:payment-${env}+level:ERROR&count=true"
done
```

**Cross-Environment Correlation:**
```bash
# Trace correlation across all environments
curl "http://localhost:8080/api/search/correlation/txn-abc-123?environments=*"
```

## Migration Strategy

### Backward Compatibility

**Existing deployments without index configuration:**
- System automatically creates "main" index from `logs-dir`
- All existing functionality continues to work
- No breaking changes

**Migration path:**
```
Step 1: Deploy new version (uses legacy mode if no indexes configured)
Step 2: Add index configuration to application.yml
Step 3: Restart application
Step 4: Indexes appear in UI automatically
```

## Benefits

### 1. Splunk-Like Experience

Familiar index-based filtering for users coming from Splunk.

### 2. Organizational Structure

Clear separation of logs by application, service, or tenant.

### 3. Independent Management

Each index can have:
- Different retention policies
- Different file patterns
- Different auto-watch settings

### 4. Multi-Tenant Support

Different tenants/customers can have isolated indexes.

### 5. Performance

Search only relevant indexes, reducing search space.

### 6. Staged Archives

Mix of hot (current), warm (recent), and cold (archived) data.

## Success Criteria

1. **Configuration**: Easy YAML-based index configuration
2. **UI**: Index selector visible in search interface
3. **API**: RESTful index management endpoints
4. **Search**: Filter by single or multiple indexes
5. **Backward Compatibility**: Existing deployments work without changes
6. **Performance**: Index filtering reduces search time

## Implementation Timeline

**Total: 3-4 weeks**

- Week 1: IndexConfigService, configuration model, backward compatibility
- Week 2: Multi-index indexing, search filtering
- Week 3: REST API, UI integration
- Week 4: Testing, documentation, migration guide

## Conclusion

Multi-index support brings LogSearch closer to enterprise log platforms like Splunk while maintaining its lightweight, zero-dependency philosophy.

This feature enables:
- Better organization of logs from multiple sources
- Index-based filtering (Splunk-style queries)
- Independent retention and management policies
- Multi-tenant deployments
- Scalable architecture for large enterprises

Combined with metadata-first search architecture, this positions LogSearch as a **production-ready Splunk alternative** for historical log search.
