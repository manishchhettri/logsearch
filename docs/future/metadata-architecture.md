# Metadata-First Search Architecture

## Overview

This document outlines the implementation plan for a two-stage search architecture with metadata-based candidate selection to enable enterprise-scale performance under concurrent load.

## Problem Statement

### Current Architecture Limitations

**Current approach:**
```
User Query → Search all relevant day indexes → Merge results
```

**Issues at enterprise scale (100GB+, 10+ concurrent users):**
- Searches open and query too many index segments
- No service/application isolation
- Poor performance under concurrent load
- Inefficient resource utilization
- Unpredictable latency

### Target Use Case

**Enterprise shared service replacing Splunk:**
- Multiple applications (payment, auth, order, inventory, etc.)
- 10-50 concurrent developers searching
- 100-500GB log archives (30 days retention)
- Selective queries (specific time, service, exception type)

## Solution: Metadata-First Architecture

### Complete Architecture Components

**1. Adaptive Chunking**
```
Log files → Dynamic chunk sizing (150-250MB) → Predictable search performance
```

**2. Metadata Index with Bloom Filters**
```
Chunks → Extract metadata + Bloom filters → Fast candidate pruning
```

**3. Two-Stage Search**
```
Stage 1: Metadata query → Bloom filter pruning → Candidate list
Stage 2: Deep content search → Only candidates → Results
```

### Core Features

1. **Adaptive Chunking:** Dynamic chunk sizing based on log volume
   - Splits high-traffic hours into multiple chunks
   - Combines low-traffic hours into single chunks
   - Maintains 150-250MB target for predictable performance

2. **Bloom Filters:** Space-efficient term existence checks
   - ~500 bytes per chunk
   - 90-98% pruning efficiency
   - Zero false negatives (never misses matches)
   - ~1% false positives (acceptable trade-off)

3. **Metadata Catalog:** Lucene-based (no external database)
   - Time ranges, log levels, services
   - Top terms, exception types, package names
   - Integrated Bloom filters

### Benefits

| Metric | Current | With Full Metadata Architecture |
|--------|---------|----------------------------------|
| Search space | 150GB (all indexes) | 2-5GB (Bloom filter pruned) |
| Concurrent users | 2-3 (degraded) | 10-50 (stable) |
| Query latency | 5-15 seconds | 300ms-1s |
| Resource usage | High (CPU/IO spikes) | Low (predictable) |
| Chunk size variance | 10MB-2GB (unpredictable) | 150-250MB (consistent) |
| Pruning efficiency | N/A (searches everything) | 90-98% eliminated |

## Architecture Design

### Directory Structure

```
.log-search/
├── indexes/
│   ├── metadata/                    # NEW: Metadata catalog
│   │   └── chunks/                  # Single Lucene index for all chunk metadata
│   │
│   └── content/                     # Existing content indexes (enhanced)
│       ├── payment-service/         # NEW: Service-based organization
│       │   ├── 2026-03-12/
│       │   │   ├── chunk-00/        # Hour 00:00-00:59
│       │   │   ├── chunk-01/        # Hour 01:00-01:59
│       │   │   └── ...
│       │   └── 2026-03-13/
│       │
│       ├── auth-service/
│       │   ├── 2026-03-12/
│       │   └── ...
│       │
│       ├── order-service/
│       │   └── ...
│       │
│       ├── iib-integration/         # IBM Integration Bus logs
│       │   └── ...
│       │
│       ├── mq-broker/               # IBM MQ logs
│       │   └── ...
│       │
│       └── esb-gateway/             # ESB/API Gateway logs
│           └── ...
```

### Chunk Strategy

**Chunk by service + hour:**
```
File: payment-service/server-20260312.log (5GB, 24 hours)

Split into chunks:
  ├── chunk-00 (00:00-00:59) → 200MB
  ├── chunk-01 (01:00-01:59) → 200MB
  ├── chunk-02 (02:00-02:59) → 200MB
  ...
  └── chunk-23 (23:00-23:59) → 200MB
```

**Benefits:**
- Time-based pruning (user searches 10am-12pm → only chunks 10, 11, 12)
- Service-based pruning (search payment → ignore auth, order, etc.)
- Predictable chunk size (~100-300MB per chunk)
- Better parallelization (search 3 chunks instead of 1 huge file)

## Metadata Index Schema

### Lucene Document Structure

Each document in the metadata index represents one chunk:

```java
Document chunkMetadata = new Document();

// ===== Identity Fields =====
chunkMetadata.add(new StringField("chunkId",
    "payment-service::2026-03-12::chunk-10", Field.Store.YES));
chunkMetadata.add(new StringField("service",
    "payment-service", Field.Store.YES));
chunkMetadata.add(new StringField("fileName",
    "server-20260312.log", Field.Store.YES));

// ===== Time Range (critical for pruning) =====
chunkMetadata.add(new LongPoint("startTime", 1710234000000L));
chunkMetadata.add(new LongPoint("endTime", 1710237599000L));
chunkMetadata.add(new StoredField("startTimeMillis", 1710234000000L));
chunkMetadata.add(new StoredField("endTimeMillis", 1710237599000L));

// ===== Log Characteristics =====
chunkMetadata.add(new StringField("hasStackTrace", "true", Field.Store.YES));
chunkMetadata.add(new StringField("logLevels", "ERROR WARN INFO", Field.Store.YES));

// ===== Top Terms (for selective term pruning) =====
// Extract top 20-50 most frequent terms from chunk
chunkMetadata.add(new TextField("topTerms",
    "NullPointerException PaymentService timeout database connection",
    Field.Store.YES));

// ===== Package Names (for code-based pruning) =====
chunkMetadata.add(new TextField("packages",
    "com.company.payment com.company.auth com.company.validator",
    Field.Store.YES));

// ===== Exception Types (for error pattern pruning) =====
chunkMetadata.add(new TextField("exceptionTypes",
    "NullPointerException OutOfMemoryError SQLException",
    Field.Store.YES));

// ===== Statistics =====
chunkMetadata.add(new IntPoint("logCount", 45000));
chunkMetadata.add(new StoredField("logCount", 45000));
chunkMetadata.add(new IntPoint("errorCount", 234));
chunkMetadata.add(new StoredField("errorCount", 234));
chunkMetadata.add(new IntPoint("warnCount", 1203));
chunkMetadata.add(new StoredField("warnCount", 1203));

// ===== Size Information =====
chunkMetadata.add(new LongPoint("indexSizeBytes", 250000000L));
chunkMetadata.add(new StoredField("indexSizeBytes", 250000000L));

// ===== Integration Platform Support (IIB, MQ, ESB) =====
chunkMetadata.add(new StringField("integrationPlatform", "IIB", Field.Store.YES));  // IIB, MQ, ESB, null
chunkMetadata.add(new StringField("hasCorrelationId", "true", Field.Store.YES));
chunkMetadata.add(new StringField("hasMessageId", "true", Field.Store.YES));

// ===== Correlation Tracking =====
// Sample of correlation IDs found in chunk (for correlation search)
chunkMetadata.add(new TextField("correlationIds",
    "txn-abc-123 txn-def-456 txn-ghi-789",
    Field.Store.YES));

// ===== Integration Flow Names =====
chunkMetadata.add(new TextField("flowNames",
    "PaymentProcessingFlow OrderValidationFlow CustomerLookupFlow",
    Field.Store.YES));

// ===== API Endpoints (for integration platforms) =====
chunkMetadata.add(new TextField("endpoints",
    "/api/payment /api/order /api/customer",
    Field.Store.YES));

// ===== Message IDs (for IIB/MQ message tracking) =====
chunkMetadata.add(new TextField("messageIds",
    "msg-001 msg-002 msg-003",
    Field.Store.YES));
```

### Field Types Explained

| Field Type | Purpose | Queryable | Stored |
|------------|---------|-----------|--------|
| `StringField` | Exact match filtering | Yes | Yes |
| `TextField` | Term/phrase search | Yes | Yes |
| `LongPoint` | Range queries (time, size) | Yes | No |
| `IntPoint` | Range queries (counts) | Yes | No |
| `StoredField` | Retrieve values (not indexed) | No | Yes |

## Implementation Plan

### Phase 1: Chunking Infrastructure

**Goal:** Split log files into hour-based chunks during indexing

**New Classes:**

#### 1. `ChunkIdentifier.java`
```java
public class ChunkIdentifier {
    private final String service;
    private final LocalDate date;
    private final int hourOfDay; // 0-23

    public String getChunkId() {
        return String.format("%s::%s::chunk-%02d",
            service, date.toString(), hourOfDay);
    }

    public static ChunkIdentifier fromTimestamp(String service,
                                                  ZonedDateTime timestamp) {
        return new ChunkIdentifier(
            service,
            timestamp.toLocalDate(),
            timestamp.getHour()
        );
    }
}
```

#### 2. `ChunkMetadata.java`
```java
public class ChunkMetadata {
    private String chunkId;
    private String service;
    private String fileName;
    private long startTimeMillis;
    private long endTimeMillis;
    private boolean hasStackTrace;
    private Set<String> logLevels;
    private List<String> topTerms;
    private Set<String> packages;
    private Set<String> exceptionTypes;
    private int logCount;
    private int errorCount;
    private int warnCount;
    private long indexSizeBytes;

    public Document toLuceneDocument() {
        // Convert to Lucene Document as shown in schema above
    }
}
```

#### 3. `ChunkMetadataExtractor.java`
```java
@Service
public class ChunkMetadataExtractor {

    /**
     * Extract metadata from a batch of log entries for a chunk
     */
    public ChunkMetadata extractMetadata(String chunkId,
                                          List<LogEntry> entries) {
        ChunkMetadata metadata = new ChunkMetadata();
        metadata.setChunkId(chunkId);

        // Extract time range
        long minTime = entries.stream()
            .mapToLong(LogEntry::getTimestamp)
            .min().orElse(0L);
        long maxTime = entries.stream()
            .mapToLong(LogEntry::getTimestamp)
            .max().orElse(0L);
        metadata.setStartTimeMillis(minTime);
        metadata.setEndTimeMillis(maxTime);

        // Extract log levels
        Set<String> levels = entries.stream()
            .map(LogEntry::getLevel)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        metadata.setLogLevels(levels);

        // Count errors and warnings
        int errors = (int) entries.stream()
            .filter(e -> "ERROR".equals(e.getLevel()))
            .count();
        int warnings = (int) entries.stream()
            .filter(e -> "WARN".equals(e.getLevel()))
            .count();
        metadata.setErrorCount(errors);
        metadata.setWarnCount(warnings);

        // Detect stack traces
        boolean hasStackTrace = entries.stream()
            .anyMatch(e -> e.getMessage().contains("Exception")
                        || e.getMessage().contains("\tat "));
        metadata.setHasStackTrace(hasStackTrace);

        // Extract top terms (simplified - use term frequency in real impl)
        List<String> topTerms = extractTopTerms(entries, 50);
        metadata.setTopTerms(topTerms);

        // Extract package names
        Set<String> packages = extractPackageNames(entries);
        metadata.setPackages(packages);

        // Extract exception types
        Set<String> exceptions = extractExceptionTypes(entries);
        metadata.setExceptionTypes(exceptions);

        metadata.setLogCount(entries.size());

        return metadata;
    }

    private List<String> extractTopTerms(List<LogEntry> entries, int limit) {
        // Term frequency analysis - extract most common terms
        Map<String, Integer> termFrequency = new HashMap<>();
        // ... implementation
        return topTerms;
    }

    private Set<String> extractPackageNames(List<LogEntry> entries) {
        Set<String> packages = new HashSet<>();
        Pattern packagePattern = Pattern.compile(
            "\\b([a-z][a-z0-9]*\\.)+[a-z][a-z0-9]*\\b"
        );
        // Extract all Java package patterns
        return packages;
    }

    private Set<String> extractExceptionTypes(List<LogEntry> entries) {
        Set<String> exceptions = new HashSet<>();
        Pattern exPattern = Pattern.compile(
            "\\b([A-Z][a-zA-Z0-9]*Exception|[A-Z][a-zA-Z0-9]*Error)\\b"
        );
        // Extract exception class names
        return exceptions;
    }
}
```

**Modify: `LogFileIndexer.java`**

```java
private void indexLogFile(Path logFile) {
    // ... existing code ...

    // NEW: Group log entries by chunk (service + hour)
    Map<ChunkIdentifier, List<LogEntry>> chunkGroups = new HashMap<>();

    while ((line = reader.readLine()) != null) {
        LogEntry entry = logParser.parseLine(line, filename, lineNumber, logFile);
        if (entry != null) {
            // Determine which chunk this entry belongs to
            ChunkIdentifier chunkId = ChunkIdentifier.fromTimestamp(
                extractServiceName(filename),
                entry.getTimestampAsZonedDateTime()
            );

            chunkGroups.computeIfAbsent(chunkId, k -> new ArrayList<>())
                       .add(entry);
        }
    }

    // Index each chunk separately
    for (Map.Entry<ChunkIdentifier, List<LogEntry>> chunkEntry : chunkGroups.entrySet()) {
        ChunkIdentifier chunkId = chunkEntry.getKey();
        List<LogEntry> entries = chunkEntry.getValue();

        // Index content (enhanced with chunkId)
        indexService.indexChunk(chunkId, entries);

        // Extract and index metadata
        ChunkMetadata metadata = metadataExtractor.extractMetadata(
            chunkId.getChunkId(), entries
        );
        metadataIndexService.indexChunkMetadata(metadata);
    }
}

private String extractServiceName(String filename) {
    // Extract service name from filename or directory structure
    // e.g., "payment-service/server-20260312.log" → "payment-service"
    // For now, could use a property or convention-based extraction
    return properties.getDefaultServiceName();
}
```

### Phase 2: Metadata Index Service

**New Class: `MetadataIndexService.java`**

```java
@Service
public class MetadataIndexService {

    private static final Logger log = LoggerFactory.getLogger(MetadataIndexService.class);

    private final LogSearchProperties properties;
    private IndexWriter metadataWriter;
    private IndexReader metadataReader;
    private IndexSearcher metadataSearcher;

    @PostConstruct
    public void initialize() throws IOException {
        Path metadataDir = Paths.get(properties.getIndexDir(), "metadata", "chunks");
        Files.createDirectories(metadataDir);

        Directory directory = FSDirectory.open(metadataDir);
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        this.metadataWriter = new IndexWriter(directory, config);
        this.metadataReader = DirectoryReader.open(metadataWriter);
        this.metadataSearcher = new IndexSearcher(metadataReader);

        log.info("Metadata index initialized at: {}", metadataDir);
    }

    /**
     * Index chunk metadata
     */
    public void indexChunkMetadata(ChunkMetadata metadata) throws IOException {
        Document doc = metadata.toLuceneDocument();
        metadataWriter.addDocument(doc);
        log.debug("Indexed metadata for chunk: {}", metadata.getChunkId());
    }

    /**
     * Find candidate chunks matching the given criteria
     */
    public List<ChunkCandidate> findCandidateChunks(ChunkQuery chunkQuery)
            throws IOException {

        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        // Time range pruning (CRITICAL)
        if (chunkQuery.hasTimeRange()) {
            queryBuilder.add(
                LongPoint.newRangeQuery("startTime",
                    chunkQuery.getStartTimeMillis(),
                    chunkQuery.getEndTimeMillis()),
                BooleanClause.Occur.MUST
            );
        }

        // Service pruning
        if (chunkQuery.hasService()) {
            queryBuilder.add(
                new TermQuery(new Term("service", chunkQuery.getService())),
                BooleanClause.Occur.MUST
            );
        }

        // Log level pruning
        if (chunkQuery.hasLogLevel()) {
            queryBuilder.add(
                new TermQuery(new Term("logLevels", chunkQuery.getLogLevel())),
                BooleanClause.Occur.MUST
            );
        }

        // Stack trace requirement
        if (chunkQuery.requiresStackTrace()) {
            queryBuilder.add(
                new TermQuery(new Term("hasStackTrace", "true")),
                BooleanClause.Occur.MUST
            );
        }

        // Package name filtering (optional, for advanced pruning)
        if (chunkQuery.hasPackageHint()) {
            queryBuilder.add(
                new TermQuery(new Term("packages", chunkQuery.getPackageHint())),
                BooleanClause.Occur.SHOULD
            );
        }

        // Refresh reader to see latest changes
        DirectoryReader newReader = DirectoryReader.openIfChanged(
            (DirectoryReader) metadataReader
        );
        if (newReader != null) {
            metadataReader.close();
            metadataReader = newReader;
            metadataSearcher = new IndexSearcher(metadataReader);
        }

        // Search metadata index
        TopDocs results = metadataSearcher.search(queryBuilder.build(), 10000);

        List<ChunkCandidate> candidates = new ArrayList<>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            Document doc = metadataSearcher.doc(scoreDoc.doc);
            candidates.add(ChunkCandidate.fromDocument(doc));
        }

        log.info("Found {} candidate chunks (out of {} total chunks)",
            candidates.size(), metadataReader.numDocs());

        return candidates;
    }

    /**
     * Commit metadata index
     */
    public void commit() throws IOException {
        metadataWriter.commit();
    }

    @PreDestroy
    public void shutdown() throws IOException {
        if (metadataReader != null) metadataReader.close();
        if (metadataWriter != null) {
            metadataWriter.commit();
            metadataWriter.close();
        }
    }
}
```

**Supporting Classes:**

```java
public class ChunkQuery {
    private Long startTimeMillis;
    private Long endTimeMillis;
    private String service;
    private String logLevel;
    private boolean requireStackTrace;
    private String packageHint;

    // Builder pattern for constructing queries
    public static class Builder {
        // ... builder implementation
    }
}

public class ChunkCandidate {
    private String chunkId;
    private String service;
    private long startTimeMillis;
    private long endTimeMillis;
    private int logCount;

    public static ChunkCandidate fromDocument(Document doc) {
        ChunkCandidate candidate = new ChunkCandidate();
        candidate.setChunkId(doc.get("chunkId"));
        candidate.setService(doc.get("service"));
        candidate.setStartTimeMillis(doc.getField("startTimeMillis").numericValue().longValue());
        candidate.setEndTimeMillis(doc.getField("endTimeMillis").numericValue().longValue());
        candidate.setLogCount(doc.getField("logCount").numericValue().intValue());
        return candidate;
    }
}
```

### Phase 3: Query Planner Integration

**Modify: `LogSearchService.java`**

Add metadata-first query planning:

```java
@Service
public class LogSearchService {

    private final MetadataIndexService metadataIndexService;
    private final LuceneIndexService luceneIndexService;
    private final LogSearchProperties properties;

    public SearchResponse search(SearchRequest request) throws IOException {

        // Step 1: Build metadata query for candidate selection
        ChunkQuery chunkQuery = buildChunkQuery(request);

        // Step 2: Find candidate chunks using metadata
        List<ChunkCandidate> candidates = metadataIndexService.findCandidateChunks(chunkQuery);

        if (candidates.isEmpty()) {
            log.info("No candidate chunks found for query: {}", request.getQuery());
            return SearchResponse.empty();
        }

        log.info("Searching {} candidate chunks (pruned from total)", candidates.size());

        // Step 3: Search only candidate chunks in parallel
        List<CompletableFuture<List<LogEntry>>> futures = candidates.stream()
            .map(candidate -> CompletableFuture.supplyAsync(() -> {
                try {
                    return luceneIndexService.searchChunk(
                        candidate.getChunkId(),
                        request.getQuery(),
                        request.getPageSize()
                    );
                } catch (IOException e) {
                    log.error("Error searching chunk: {}", candidate.getChunkId(), e);
                    return Collections.emptyList();
                }
            }, executorService))
            .collect(Collectors.toList());

        // Step 4: Merge results from all chunks
        List<LogEntry> allResults = futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
            .limit(request.getPageSize())
            .collect(Collectors.toList());

        return SearchResponse.of(allResults);
    }

    private ChunkQuery buildChunkQuery(SearchRequest request) {
        ChunkQuery.Builder builder = new ChunkQuery.Builder();

        // Always apply time range pruning
        if (request.hasTimeRange()) {
            builder.timeRange(request.getStartTime(), request.getEndTime());
        }

        // Extract service from request (if specified)
        if (request.hasService()) {
            builder.service(request.getService());
        }

        // Extract log level filter
        if (request.getQuery().contains("level:ERROR")) {
            builder.logLevel("ERROR");
        } else if (request.getQuery().contains("level:WARN")) {
            builder.logLevel("WARN");
        }

        // Detect if query is looking for exceptions (stack traces likely needed)
        if (request.getQuery().matches(".*Exception|.*Error")) {
            builder.requireStackTrace(true);
        }

        // Extract package hints from query (e.g., "PaymentService" → "payment")
        // This is optional advanced optimization
        String packageHint = extractPackageHint(request.getQuery());
        if (packageHint != null) {
            builder.packageHint(packageHint);
        }

        return builder.build();
    }

    private String extractPackageHint(String query) {
        // Extract potential package names from query
        // e.g., "PaymentService" → "payment"
        // This is heuristic-based and optional
        return null; // Simplified for now
    }
}
```

**Modify: `LuceneIndexService.java`**

Add chunk-specific search:

```java
/**
 * Search a specific chunk by ID
 */
public List<LogEntry> searchChunk(String chunkId, String queryString, int limit)
        throws IOException {

    // Parse user query
    Query userQuery = parseQuery(queryString);

    // Add chunk filter
    BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
    queryBuilder.add(userQuery, BooleanClause.Occur.MUST);
    queryBuilder.add(
        new TermQuery(new Term("chunkId", chunkId)),
        BooleanClause.Occur.MUST
    );

    // Search this specific chunk
    IndexSearcher searcher = getSearcherForChunk(chunkId);
    TopDocs results = searcher.search(queryBuilder.build(), limit);

    return convertToLogEntries(searcher, results);
}
```

### Phase 4: Content Index Enhancement

**Modify content documents to include chunkId:**

```java
// In LuceneIndexService.indexLogEntry()
public void indexLogEntry(LogEntry entry, String chunkId) throws IOException {
    Document doc = new Document();

    // Existing fields...
    doc.add(new TextField("message", entry.getMessage(), Field.Store.YES));
    doc.add(new StringField("level", entry.getLevel(), Field.Store.YES));

    // NEW: Add chunk identifier
    doc.add(new StringField("chunkId", chunkId, Field.Store.YES));

    // Index to appropriate chunk-specific index
    IndexWriter writer = getOrCreateIndexWriter(chunkId);
    writer.addDocument(doc);
}
```

### Phase 5: Bloom Filter Integration

**Goal:** Add space-efficient term existence checks for maximum pruning efficiency

**Add Dependency:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>31.1-jre</version>
</dependency>
```

**New Class: `BloomFilterManager.java`**

```java
package com.lsearch.logsearch.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Service
public class BloomFilterManager {

    private static final double FALSE_POSITIVE_RATE = 0.01; // 1% false positive rate

    /**
     * Create Bloom filter from log entries
     */
    public BloomFilter<String> createBloomFilter(List<LogEntry> entries,
                                                   int estimatedUniqueTerms) {
        BloomFilter<String> bloomFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            estimatedUniqueTerms,
            FALSE_POSITIVE_RATE
        );

        // Add all unique terms from all entries
        for (LogEntry entry : entries) {
            // Tokenize message
            String[] tokens = tokenize(entry.getMessage());
            for (String token : tokens) {
                bloomFilter.put(token.toLowerCase());
            }

            // Add structured fields
            if (entry.getLevel() != null) {
                bloomFilter.put(entry.getLevel().toLowerCase());
            }
            if (entry.getLogger() != null) {
                bloomFilter.put(entry.getLogger().toLowerCase());
            }
        }

        return bloomFilter;
    }

    /**
     * Serialize Bloom filter to bytes
     */
    public byte[] serialize(BloomFilter<String> bloomFilter) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bloomFilter.writeTo(baos);
        return baos.toByteArray();
    }

    /**
     * Deserialize Bloom filter from bytes
     */
    public BloomFilter<String> deserialize(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        return BloomFilter.readFrom(bais, Funnels.stringFunnel(StandardCharsets.UTF_8));
    }

    /**
     * Check if Bloom filter might contain all search terms
     */
    public boolean mightContainAll(BloomFilter<String> bloomFilter,
                                     Set<String> searchTerms) {
        return searchTerms.stream()
            .allMatch(term -> bloomFilter.mightContain(term.toLowerCase()));
    }

    /**
     * Simple tokenizer (can be enhanced with CodeAnalyzer logic)
     */
    private String[] tokenize(String text) {
        // Split on whitespace and common delimiters
        return text.toLowerCase()
            .split("[\\s\\.,;:()\\[\\]{}\"'<>]+");
    }

    /**
     * Estimate unique terms in entries (for Bloom filter sizing)
     */
    public int estimateUniqueTerms(List<LogEntry> entries) {
        // Conservative estimate: ~100 unique terms per 1000 entries
        return Math.max(1000, entries.size() / 10);
    }
}
```

**Modify: `ChunkMetadata.java`**

```java
public class ChunkMetadata {
    // Existing fields...

    // NEW: Bloom filter for term existence checks
    private byte[] bloomFilterBytes;

    public byte[] getBloomFilterBytes() {
        return bloomFilterBytes;
    }

    public void setBloomFilterBytes(byte[] bloomFilterBytes) {
        this.bloomFilterBytes = bloomFilterBytes;
    }

    public Document toLuceneDocument() {
        Document doc = new Document();

        // Existing fields...

        // NEW: Store Bloom filter
        if (bloomFilterBytes != null) {
            doc.add(new StoredField("bloomFilter", bloomFilterBytes));
        }

        return doc;
    }

    public static ChunkMetadata fromDocument(Document doc) {
        ChunkMetadata metadata = new ChunkMetadata();

        // Existing field extraction...

        // NEW: Extract Bloom filter
        BytesRef bloomRef = doc.getBinaryValue("bloomFilter");
        if (bloomRef != null) {
            metadata.setBloomFilterBytes(bloomRef.bytes);
        }

        return metadata;
    }
}
```

**Modify: `ChunkMetadataExtractor.java`**

```java
@Service
public class ChunkMetadataExtractor {

    private final BloomFilterManager bloomFilterManager;

    public ChunkMetadataExtractor(BloomFilterManager bloomFilterManager) {
        this.bloomFilterManager = bloomFilterManager;
    }

    public ChunkMetadata extractMetadata(String chunkId,
                                          List<LogEntry> entries) {
        ChunkMetadata metadata = new ChunkMetadata();

        // Existing metadata extraction...

        // NEW: Create and attach Bloom filter
        try {
            int estimatedTerms = bloomFilterManager.estimateUniqueTerms(entries);
            BloomFilter<String> bloomFilter =
                bloomFilterManager.createBloomFilter(entries, estimatedTerms);
            byte[] bloomBytes = bloomFilterManager.serialize(bloomFilter);
            metadata.setBloomFilterBytes(bloomBytes);

            log.debug("Created Bloom filter for chunk {} ({} bytes, {} estimated terms)",
                chunkId, bloomBytes.length, estimatedTerms);
        } catch (IOException e) {
            log.warn("Failed to create Bloom filter for chunk: {}", chunkId, e);
            // Continue without Bloom filter (will fall back to term list)
        }

        return metadata;
    }
}
```

**Modify: `MetadataIndexService.java`**

```java
@Service
public class MetadataIndexService {

    private final BloomFilterManager bloomFilterManager;

    public MetadataIndexService(LogSearchProperties properties,
                                 BloomFilterManager bloomFilterManager) {
        this.properties = properties;
        this.bloomFilterManager = bloomFilterManager;
    }

    public List<ChunkCandidate> findCandidateChunks(ChunkQuery chunkQuery)
            throws IOException {

        // Step 1: Traditional metadata query (time, service, level)
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        // ... existing query building code ...

        TopDocs results = metadataSearcher.search(queryBuilder.build(), 10000);

        // Step 2: Further prune with Bloom filters (if query has search terms)
        Set<String> searchTerms = chunkQuery.getSearchTerms();
        if (searchTerms != null && !searchTerms.isEmpty()) {
            List<ChunkCandidate> bloomFilteredCandidates = new ArrayList<>();

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = metadataSearcher.doc(scoreDoc.doc);
                ChunkCandidate candidate = ChunkCandidate.fromDocument(doc);

                // Check Bloom filter
                byte[] bloomBytes = candidate.getBloomFilterBytes();
                if (bloomBytes != null) {
                    try {
                        BloomFilter<String> bloomFilter =
                            bloomFilterManager.deserialize(bloomBytes);

                        if (bloomFilterManager.mightContainAll(bloomFilter, searchTerms)) {
                            bloomFilteredCandidates.add(candidate);
                        } else {
                            log.debug("Bloom filter pruned chunk: {}", candidate.getChunkId());
                        }
                    } catch (IOException e) {
                        log.warn("Failed to deserialize Bloom filter, including chunk: {}",
                            candidate.getChunkId());
                        bloomFilteredCandidates.add(candidate); // Include on error
                    }
                } else {
                    // No Bloom filter, include chunk
                    bloomFilteredCandidates.add(candidate);
                }
            }

            log.info("Bloom filter pruned {} chunks (from {} to {})",
                results.scoreDocs.length - bloomFilteredCandidates.size(),
                results.scoreDocs.length,
                bloomFilteredCandidates.size());

            return bloomFilteredCandidates;
        }

        // No search terms, return all metadata-matched chunks
        return convertToCandidates(results);
    }
}
```

**Bloom Filter Benefits:**

| Metric | Without Bloom Filter | With Bloom Filter |
|--------|---------------------|-------------------|
| Storage per chunk | ~500 bytes | ~700 bytes (+200 bytes) |
| Pruning accuracy | Top 50 terms only | ALL terms |
| False negatives | Possible | **NEVER** |
| Pruning ratio | 60-80% | 90-98% |
| Check speed | Text parse + search | Bit array lookup (μs) |

### Phase 6: Adaptive Chunking

**Goal:** Dynamic chunk sizing based on log volume for predictable performance

**Modify: `ChunkingStrategy.java` (new interface)**

```java
package com.lsearch.logsearch.service;

import com.lsearch.logsearch.model.LogEntry;
import java.nio.file.Path;
import java.util.List;

public interface ChunkingStrategy {
    /**
     * Determine chunk boundaries for a set of log entries
     */
    List<Chunk> createChunks(List<LogEntry> entries);

    /**
     * Get strategy name
     */
    String getStrategyName();
}
```

**Implement: `AdaptiveChunkingStrategy.java`**

```java
package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdaptiveChunkingStrategy implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveChunkingStrategy.class);

    // Target chunk size: 150-250 MB
    private static final long TARGET_SIZE_BYTES = 200_000_000L;
    private static final long MIN_SIZE_BYTES = 150_000_000L;
    private static final long MAX_SIZE_BYTES = 250_000_000L;

    // Time constraints
    private static final long MIN_DURATION_MINUTES = 15;
    private static final long MAX_DURATION_HOURS = 6;

    private final LogSearchProperties properties;

    public AdaptiveChunkingStrategy(LogSearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Chunk> createChunks(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        List<Chunk> chunks = new ArrayList<>();
        Chunk currentChunk = new Chunk();
        long currentSize = 0;
        ZonedDateTime chunkStartTime = entries.get(0).getTimestampAsZonedDateTime();

        for (LogEntry entry : entries) {
            long entrySize = estimateEntrySize(entry);
            ZonedDateTime entryTime = entry.getTimestampAsZonedDateTime();

            // Calculate current chunk duration
            Duration chunkDuration = Duration.between(chunkStartTime, entryTime);
            long durationMinutes = chunkDuration.toMinutes();
            long durationHours = chunkDuration.toHours();

            // Decision: Should we finalize current chunk?
            boolean shouldFinalize = false;

            // Rule 1: Exceeded maximum duration (hard limit)
            if (durationHours >= MAX_DURATION_HOURS) {
                shouldFinalize = true;
                log.debug("Finalizing chunk due to max duration: {} hours", durationHours);
            }

            // Rule 2: Exceeded target size AND met minimum duration
            else if (currentSize + entrySize > TARGET_SIZE_BYTES &&
                     durationMinutes >= MIN_DURATION_MINUTES) {
                shouldFinalize = true;
                log.debug("Finalizing chunk due to size: {} MB (duration: {} min)",
                    currentSize / 1_000_000, durationMinutes);
            }

            // Rule 3: Way over max size (emergency finalize)
            else if (currentSize + entrySize > MAX_SIZE_BYTES) {
                shouldFinalize = true;
                log.warn("Emergency finalize chunk due to max size: {} MB",
                    currentSize / 1_000_000);
            }

            if (shouldFinalize && !currentChunk.isEmpty()) {
                // Finalize current chunk
                currentChunk.setEndTime(entryTime);
                chunks.add(currentChunk);

                log.info("Created adaptive chunk: {} entries, {} MB, {} minutes",
                    currentChunk.size(),
                    currentSize / 1_000_000,
                    durationMinutes);

                // Start new chunk
                currentChunk = new Chunk();
                currentSize = 0;
                chunkStartTime = entryTime;
            }

            // Add entry to current chunk
            currentChunk.add(entry);
            currentSize += entrySize;
        }

        // Finalize last chunk
        if (!currentChunk.isEmpty()) {
            currentChunk.setEndTime(entries.get(entries.size() - 1)
                .getTimestampAsZonedDateTime());
            chunks.add(currentChunk);

            log.info("Created final adaptive chunk: {} entries, {} MB",
                currentChunk.size(), currentSize / 1_000_000);
        }

        log.info("Adaptive chunking created {} chunks from {} entries",
            chunks.size(), entries.size());

        return chunks;
    }

    /**
     * Estimate size of a log entry in bytes
     * (message + metadata overhead)
     */
    private long estimateEntrySize(LogEntry entry) {
        // Rough estimate:
        // - Message text
        // - Timestamp (8 bytes)
        // - Metadata (~100 bytes for level, logger, user, etc.)
        // - Index overhead (~50% of raw size)

        int messageBytes = entry.getMessage() != null ?
            entry.getMessage().length() * 2 : 0; // 2 bytes per char (UTF-16)

        int metadataBytes = 100;
        int indexOverhead = (messageBytes + metadataBytes) / 2;

        return messageBytes + metadataBytes + indexOverhead;
    }

    @Override
    public String getStrategyName() {
        return "ADAPTIVE";
    }
}
```

**Implement: `HourlyChunkingStrategy.java`**

```java
package com.lsearch.logsearch.service;

import com.lsearch.logsearch.model.LogEntry;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HourlyChunkingStrategy implements ChunkingStrategy {

    @Override
    public List<Chunk> createChunks(List<LogEntry> entries) {
        // Group by hour
        Map<String, Chunk> hourlyChunks = new HashMap<>();

        for (LogEntry entry : entries) {
            ZonedDateTime timestamp = entry.getTimestampAsZonedDateTime();
            String hourKey = String.format("%s-%02d",
                timestamp.toLocalDate(), timestamp.getHour());

            Chunk chunk = hourlyChunks.computeIfAbsent(hourKey, k -> new Chunk());
            chunk.add(entry);
        }

        return new ArrayList<>(hourlyChunks.values());
    }

    @Override
    public String getStrategyName() {
        return "HOURLY";
    }
}
```

**Modify: `LogFileIndexer.java`**

```java
@Service
public class LogFileIndexer {

    private final ChunkingStrategy chunkingStrategy;

    public LogFileIndexer(LogSearchProperties properties,
                          LogParser logParser,
                          LuceneIndexService indexService,
                          ChunkingStrategy chunkingStrategy) {
        this.properties = properties;
        this.logParser = logParser;
        this.indexService = indexService;
        this.chunkingStrategy = chunkingStrategy;
    }

    private void indexLogFile(Path logFile) {
        // ... parse all entries ...

        List<LogEntry> allEntries = parseAllEntries(logFile);

        // Use configured chunking strategy
        List<Chunk> chunks = chunkingStrategy.createChunks(allEntries);

        log.info("Chunking strategy '{}' created {} chunks for {}",
            chunkingStrategy.getStrategyName(), chunks.size(), filename);

        // Index each chunk
        for (Chunk chunk : chunks) {
            String chunkId = generateChunkId(chunk);
            indexService.indexChunk(chunkId, chunk.getEntries());

            // Extract and index metadata
            ChunkMetadata metadata = metadataExtractor.extractMetadata(
                chunkId, chunk.getEntries()
            );
            metadataIndexService.indexChunkMetadata(metadata);
        }
    }
}
```

**Adaptive Chunking Benefits:**

| Metric | Fixed Hourly | Adaptive |
|--------|--------------|----------|
| Chunk size | 10MB - 2GB | 150-250 MB |
| Search time | 100ms - 8s | 400-800ms |
| Predictability | Low | High |
| Traffic adaptation | None | Automatic |
| Peak hour handling | Poor (huge chunks) | Good (split into multiple) |
| Off-peak efficiency | Poor (many tiny chunks) | Good (combine hours) |

**Real-World Example:**

E-commerce site during Black Friday sale:

```
Fixed Hourly:
  09:00-10:00 → 2.5 GB (peak traffic)
  Search time: 8 seconds ❌

Adaptive:
  09:00-09:05 → 220 MB
  09:05-09:12 → 205 MB
  09:12-09:18 → 215 MB
  ... (18 chunks)
  Search time: 600ms per chunk ✅

During off-peak:
  02:00-06:00 → 180 MB (4 hours combined)
  Search time: 500ms ✅
```

## Configuration Changes

**Add to `application.yml`:**

```yaml
log-search:
  # Existing config...

  # NEW: Chunking configuration
  chunking:
    enabled: true                    # Enable chunk-based indexing
    strategy: "ADAPTIVE"             # ADAPTIVE (recommended), HOURLY, or SIZE_BASED

    # Adaptive strategy parameters
    adaptive:
      target-size-mb: 200            # Target chunk size
      min-size-mb: 150               # Minimum acceptable chunk size
      max-size-mb: 250               # Maximum chunk size (hard limit)
      min-duration-minutes: 15       # Minimum time span per chunk
      max-duration-hours: 6          # Maximum time span per chunk

    # Hourly strategy (fallback if adaptive not suitable)
    hourly:
      enabled: false                 # Use fixed hourly chunks

  # NEW: Service name extraction
  service-name: "default-service"    # Default service name if not detected
  service-name-pattern: "([^/]+)/.*\\.log"  # Regex to extract from path

  # NEW: Metadata options
  metadata:
    top-terms-count: 50              # Number of top terms to extract per chunk
    enable-package-extraction: true  # Extract Java package names
    enable-exception-extraction: true # Extract exception types

    # Bloom filter configuration
    bloom-filter:
      enabled: true                  # Enable Bloom filters for term pruning
      false-positive-rate: 0.01      # 1% false positive rate (lower = larger filter)
      estimated-terms-per-chunk: 10000  # Expected unique terms (auto-adjusted)
```

## Migration Strategy

### Existing Deployments

For users with existing indexes:

1. **Backward compatibility mode:**
   - If `chunking.enabled: false`, use old search path
   - If `chunking.enabled: true`, rebuild indexes with chunking

2. **Gradual migration:**
   ```
   Step 1: Deploy new version with chunking disabled
   Step 2: User enables chunking in config
   Step 3: Run full reindex (creates chunk-based structure)
   Step 4: Old indexes automatically cleaned up after retention period
   ```

3. **Dual-mode search:**
   ```java
   if (metadataIndexExists() && properties.isChunkingEnabled()) {
       return metadataFirstSearch(request);
   } else {
       return legacySearch(request);
   }
   ```

## Performance Expectations

### Before (Current Architecture)

```
Query: OutOfMemoryError in PaymentService, 10am-12pm, March 12
Archive: 150GB (10 services × 30 days)
Concurrent users: 10

Search process:
  → Open all day indexes for March 12 (~5GB)
  → Search all services (no pruning)
  → 10 users × 5GB = 50GB scanned concurrently
  → Result: 8-15 second latency, high CPU/IO
```

### After (Metadata-First with Bloom Filters + Adaptive Chunking)

```
Query: OutOfMemoryError in PaymentService, 10am-12pm, March 12
Archive: 150GB (10 services × 30 days)
Concurrent users: 10

Search process:
  → Query metadata index (50MB)
  → Initial candidates: payment-service, March 12, time 10:00-12:00
    → 6 potential chunks identified

  → Apply Bloom filter pruning:
    → Check each chunk's Bloom filter for "OutOfMemoryError" AND "PaymentService"
    → 4 chunks definitely don't contain these terms (pruned)
    → 2 chunks might contain them (search these)

  → Adaptive chunks are uniform size (~200MB each)
  → Search only 2 chunks (~400MB total)
  → 10 users × 400MB = 4GB scanned concurrently
  → Result: 300ms-800ms latency, minimal CPU/IO
```

**Improvement: 10-20x faster, 12x less data scanned**

**With Adaptive Chunking during Black Friday peak:**
```
Fixed hourly:
  09:00-10:00 → 2.5GB single chunk → 8s search time

Adaptive:
  09:00-09:05 → 220MB → 600ms
  09:05-09:12 → 205MB → 550ms
  ... (only chunks with matching terms searched)
  → Average 600ms per chunk, parallel search
```

## Testing Plan

### Unit Tests

1. **ChunkIdentifier Tests**
   - Verify correct chunk ID generation
   - Test service name extraction
   - Test hour-based bucketing

2. **MetadataExtractor Tests**
   - Verify term extraction
   - Verify package name extraction
   - Verify exception type detection

3. **MetadataIndexService Tests**
   - Test metadata indexing
   - Test candidate selection queries
   - Test time range pruning

4. **BloomFilterManager Tests**
   - Verify Bloom filter creation from entries
   - Test serialization/deserialization
   - Verify term existence checks
   - Test false positive rate (should be ~1%)
   - Verify zero false negatives

5. **AdaptiveChunkingStrategy Tests**
   - Test chunk size boundaries (150-250MB)
   - Test minimum duration enforcement (15 min)
   - Test maximum duration enforcement (6 hours)
   - Test emergency finalization at max size
   - Test varied traffic patterns (peak vs off-peak)

6. **HourlyChunkingStrategy Tests**
   - Verify hourly boundaries
   - Test edge cases (midnight, DST transitions)

### Integration Tests

1. **End-to-End Chunking**
   - Index multi-service log files
   - Verify chunks created correctly
   - Verify metadata extracted

2. **Query Pruning**
   - Test time-based pruning
   - Test service-based pruning
   - Test level-based pruning
   - Verify correct chunks selected

3. **Search Accuracy**
   - Same queries return same results (metadata vs non-metadata)
   - No false negatives (all matching chunks found)

4. **Bloom Filter Pruning**
   - Test with rare terms (should prune 90%+ of chunks)
   - Test with common terms (should prune fewer chunks)
   - Verify no false negatives (all matching chunks included)
   - Measure actual false positive rate

5. **Adaptive Chunking Behavior**
   - Index logs with varied traffic (peak + off-peak)
   - Verify chunk sizes within 150-250MB range
   - Verify time constraints honored
   - Compare search times: adaptive vs hourly

### Load Testing

1. **Concurrent Search Load**
   - 10+ users searching simultaneously
   - Mixed query patterns
   - Measure: latency, throughput, resource usage

2. **Large Archive Scaling**
   - Test with 100GB, 200GB, 500GB archives
   - Measure: query latency vs archive size
   - Verify linear scaling

## Monitoring and Metrics

**Add logging for observability:**

```java
log.info("Metadata query found {} candidates out of {} total chunks (pruned {}%)",
    candidateCount, totalChunkCount, pruningPercentage);

log.info("Searched {} chunks in {}ms (avg {}ms per chunk)",
    candidateCount, totalTime, avgTimePerChunk);
```

**Expose metrics via API:**

```
GET /api/stats/chunks

Response:
{
  "totalChunks": 720,
  "lastQuery": {
    "candidatesFound": 12,
    "pruningRatio": 98.3,
    "searchTimeMs": 450
  }
}
```

## Rollout Plan

### Phase 1: Core Infrastructure (Week 1-2)
- Implement chunking infrastructure (ChunkIdentifier, ChunkMetadata)
- Implement metadata index service
- Implement query planner
- Unit tests for core components

### Phase 2: Bloom Filters (Week 3)
- Add Guava dependency
- Implement BloomFilterManager
- Integrate with metadata extraction
- Integrate with query pruning
- Unit tests for Bloom filter logic

### Phase 3: Adaptive Chunking (Week 4)
- Implement ChunkingStrategy interface
- Implement AdaptiveChunkingStrategy
- Implement HourlyChunkingStrategy (fallback)
- Strategy selection based on config
- Unit tests for chunking strategies

### Phase 4: Integration Platform Support & Correlation Tracking (Week 5)
- Add IIB, MQ, ESB format patterns to LogPatternDetector
- Implement IntegrationMetadataExtractor
- Extract correlation IDs, message IDs, flow names, endpoints
- Add integration-specific fields to ChunkMetadata
- Implement correlation search API (`/api/search/correlation/{id}`)
- Implement flow search API (`/api/search/flow/{flowName}`)
- Unit tests for integration metadata extraction
- Test correlation ID tracking and search

**New Classes:**

**IntegrationMetadataExtractor.java:**
```java
@Service
public class IntegrationMetadataExtractor {

    private static final Pattern CORRELATION_ID_PATTERN =
        Pattern.compile("correlationId[=:\\s]+([a-zA-Z0-9-]+)");
    private static final Pattern MESSAGE_ID_PATTERN =
        Pattern.compile("messageId[=:\\s]+([a-zA-Z0-9-]+)");
    private static final Pattern FLOW_NAME_PATTERN =
        Pattern.compile("flow[=:\\s]+([a-zA-Z0-9_.-]+)");
    private static final Pattern ENDPOINT_PATTERN =
        Pattern.compile("(\/api\/[a-zA-Z0-9/_-]+)");

    public void enrichMetadata(ChunkMetadata metadata, List<LogEntry> entries) {
        Set<String> correlationIds = new HashSet<>();
        Set<String> messageIds = new HashSet<>();
        Set<String> flowNames = new HashSet<>();
        Set<String> endpoints = new HashSet<>();

        for (LogEntry entry : entries) {
            String message = entry.getMessage();

            // Extract correlation IDs
            Matcher corrMatcher = CORRELATION_ID_PATTERN.matcher(message);
            while (corrMatcher.find()) {
                correlationIds.add(corrMatcher.group(1));
            }

            // Extract message IDs
            Matcher msgMatcher = MESSAGE_ID_PATTERN.matcher(message);
            while (msgMatcher.find()) {
                messageIds.add(msgMatcher.group(1));
            }

            // Extract flow names
            Matcher flowMatcher = FLOW_NAME_PATTERN.matcher(message);
            while (flowMatcher.find()) {
                flowNames.add(flowMatcher.group(1));
            }

            // Extract API endpoints
            Matcher endpointMatcher = ENDPOINT_PATTERN.matcher(message);
            while (endpointMatcher.find()) {
                endpoints.add(endpointMatcher.group(1));
            }
        }

        // Set metadata fields
        metadata.setCorrelationIds(correlationIds);
        metadata.setHasCorrelationId(!correlationIds.isEmpty());
        metadata.setMessageIds(messageIds);
        metadata.setHasMessageId(!messageIds.isEmpty());
        metadata.setFlowNames(flowNames);
        metadata.setEndpoints(endpoints);

        // Detect integration platform
        String platform = detectIntegrationPlatform(entries);
        metadata.setIntegrationPlatform(platform);
    }

    private String detectIntegrationPlatform(List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            String message = entry.getMessage();
            if (message.contains("BIP") && message.matches(".*BIP\\d+[A-Z]:.*")) {
                return "IIB";
            }
            if (message.contains("AMQ") && message.matches(".*AMQ\\d+[A-Z]:.*")) {
                return "MQ";
            }
            if (message.contains("WSWS") || message.contains("CWSWS")) {
                return "ESB";
            }
        }
        return null;
    }
}
```

**CorrelationSearchController.java:**
```java
@RestController
@RequestMapping("/api/search")
public class CorrelationSearchController {

    private final MetadataIndexService metadataService;
    private final LogSearchService searchService;

    /**
     * Search by correlation ID across all services and chunks
     */
    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<CorrelationSearchResult> searchByCorrelation(
            @PathVariable String correlationId,
            @RequestParam(required = false, defaultValue = "100") int limit) {

        try {
            // Step 1: Find chunks containing this correlation ID
            List<ChunkCandidate> candidates = metadataService
                .findChunksWithCorrelationId(correlationId);

            // Step 2: Search only those chunks
            List<LogEntry> entries = searchService
                .searchChunksByCorrelationId(candidates, correlationId, limit);

            // Step 3: Sort chronologically to show transaction timeline
            entries.sort(Comparator.comparing(LogEntry::getTimestamp));

            CorrelationSearchResult result = new CorrelationSearchResult();
            result.setCorrelationId(correlationId);
            result.setEntries(entries);
            result.setTimeline(buildTimeline(entries));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search by flow name
     */
    @GetMapping("/flow/{flowName}")
    public ResponseEntity<List<LogEntry>> searchByFlow(
            @PathVariable String flowName,
            @RequestParam Long startTime,
            @RequestParam Long endTime,
            @RequestParam(required = false, defaultValue = "100") int limit) {

        // Find chunks with this flow name
        List<ChunkCandidate> candidates = metadataService
            .findChunksWithFlow(flowName, startTime, endTime);

        // Search those chunks
        List<LogEntry> entries = searchService
            .searchChunksForFlow(candidates, flowName, limit);

        return ResponseEntity.ok(entries);
    }
}
```

### Phase 5: Integration Testing (Week 6)
- End-to-end tests with all components
- Load testing with realistic data (100GB+)
- Performance benchmarking
- Verify Bloom filter pruning efficiency
- Verify adaptive chunking behavior under varied traffic

### Phase 6: Beta Deployment (Week 7-8)
- Deploy to staging environment
- Small user group testing (include integration platform logs)
- Collect performance metrics
- Collect feedback on predictability
- Test correlation search with real integration logs

### Phase 7: Production Rollout (Week 9)
- Documentation updates
- Production deployment
- Migration guide for existing users
- Monitoring dashboards

## Success Criteria

1. **Performance:**
   - Search latency < 1s for 100GB archives (with Bloom filters)
   - Search latency < 2s for 500GB archives
   - Support 10-50 concurrent users without degradation
   - 90%+ pruning ratio for typical queries (with Bloom filters)
   - Consistent chunk sizes (150-250MB with adaptive chunking)

2. **Accuracy:**
   - Zero false negatives (all matching chunks found)
   - Same results as non-metadata search

3. **Resource Usage:**
   - CPU usage < 50% under 10 concurrent users
   - Predictable memory footprint

4. **User Experience:**
   - Transparent to end users (no API changes)
   - Backward compatible (can disable if needed)

## Future Enhancements (Post-v1.0)

1. **Query Cache:** Cache frequent query results
2. **Smart Prefetching:** Preload likely chunks based on query patterns
3. **Distributed Indexing:** Split chunks across multiple nodes
4. **Machine Learning:** Predict likely chunks based on historical query patterns
