# Log Search Application - Technical Documentation

## Table of Contents
1. [Technology Stack](#technology-stack)
2. [Architecture Overview](#architecture-overview)
3. [Package Structure](#package-structure)
4. [Class Descriptions](#class-descriptions)
5. [Data Flow - Indexing](#data-flow---indexing)
6. [Data Flow - Searching](#data-flow---searching)
7. [Lucene Index Structure](#lucene-index-structure)
8. [API Endpoints](#api-endpoints)
9. [Configuration System](#configuration-system)
10. [Build and Deployment](#build-and-deployment)

---

## Technology Stack

### Core Frameworks and Libraries

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 8 | Runtime platform (Java 8 compatibility for broad deployment) |
| **Spring Boot** | 2.7.18 | Application framework, dependency injection, web server |
| **Apache Lucene** | 8.11.2 | Full-text search and indexing engine |
| **Apache Tomcat** | 9.0.83 | Embedded web server (via Spring Boot) |
| **Maven** | 3.6+ | Build tool and dependency management |
| **Chart.js** | 4.4.0 | Frontend charting library for dashboards and analytics |

### Lucene Dependencies

- **lucene-core**: Core indexing and search functionality
- **lucene-queryparser**: Query parsing (supports boolean, phrase, wildcard queries)
- **lucene-analyzers-common**: Text analysis (CodeAnalyzer for code-aware tokenization)

### Spring Boot Starters

- **spring-boot-starter-web**: REST API, embedded Tomcat, Jackson JSON
- **spring-boot-starter-test**: JUnit, Mockito, Spring Test utilities

---

## Architecture Overview

### High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                         Web Browser / API Client                  │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                │ HTTP REST
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│                      LogSearchController                          │
│                     (REST API Layer)                              │
└───────────┬─────────────────────────────────┬───────────────────┘
            │                                 │
            │ /api/search                     │ /api/index
            │                                 │
┌───────────▼────────────────┐    ┌──────────▼───────────────────┐
│   LogSearchService         │    │    LogFileIndexer            │
│   (Search Logic)           │    │    (File Watching)           │
└───────────┬────────────────┘    └──────────┬───────────────────┘
            │                                 │
            │ Query day indexes              │ Parse & index logs
            │                                 │
┌───────────▼─────────────────────────────────▼───────────────────┐
│                    LuceneIndexService                             │
│              (Index Management & Storage)                         │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                │ Read/Write
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│                  Lucene Day-Based Indexes                         │
│   .log-search/indexes/                                            │
│   ├── 2026-03-10/  (Lucene index for March 10)                   │
│   ├── 2026-03-11/  (Lucene index for March 11)                   │
│   └── 2026-03-12/  (Lucene index for March 12)                   │
└───────────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **Day-based Index Partitioning**
   - Each day gets its own Lucene index directory
   - Enables efficient date-range queries (only open relevant indexes)
   - Simplifies retention management (delete old day directories)
   - Typical search across 7 days = open only 7 indexes

2. **Embedded Lucene (No External Services)**
   - No need for ElasticSearch/Solr servers
   - Single JAR deployment
   - Suitable for individual developer machines

3. **Code-Aware Tokenization (CodeAnalyzer)**
   - Custom Lucene analyzer optimized for Java code and stack traces
   - Splits on dots, parentheses, colons to enable class name searches
   - `com.example.DataValidator` → indexed as ["com", "example", "datavalidator"]
   - Enables searching by class name, package, or method without full paths

4. **Multi-field Search**
   - Searches across message, user, level, thread, and logger fields
   - Enables finding logs by any metadata or content
   - Field-specific queries: `level:ERROR`, `user:admin`, `logger:DataValidator`

5. **Multi-line Log Support**
   - Intelligent continuation line detection (stack traces, multi-line messages)
   - Lines starting with whitespace or "at " are appended to previous entry
   - Stack traces remain grouped with their originating log entry

6. **Fallback Parsing**
   - 3-tier parsing strategy ensures all logs get indexed
   - Tier 1: Primary configured pattern
   - Tier 2: Auto-detected timestamp patterns (ISO 8601, Apache, Syslog)
   - Tier 3: File metadata (filename date or last modified time)

7. **External Configuration**
   - Spring Boot's externalized configuration
   - Users can customize log patterns without rebuilding

---

## Package Structure

```
com.lsearch.logsearch/
│
├── LogSearchApplication.java          # Main entry point (@SpringBootApplication)
│
├── cli/
│   └── CommandLineRunner.java         # CLI argument processing
│
├── config/
│   └── LogSearchProperties.java       # Configuration properties (@ConfigurationProperties)
│
├── controller/
│   └── LogSearchController.java       # REST API endpoints
│
├── model/
│   ├── LogEntry.java                  # Domain model for a single log entry
│   ├── SearchResult.java              # Search response model (results + metadata)
│   ├── AggregationResult.java         # Aggregation response with facets and timelines
│   └── Facet.java                     # Facet model (value, count, percentage)
│
└── service/
    ├── LogParser.java                 # Parses log lines into LogEntry objects
    ├── LuceneIndexService.java        # Manages Lucene IndexWriters per day
    ├── LogFileIndexer.java            # Watches log files and triggers indexing
    └── LogSearchService.java          # Executes searches across day indexes
```

---

## Class Descriptions

### 1. LogSearchApplication.java
**Package**: `com.lsearch.logsearch`
**Type**: Main Class

**Responsibilities**:
- Spring Boot application entry point
- Enables component scanning, auto-configuration, scheduling

**Key Annotations**:
```java
@SpringBootApplication  // Combines @Configuration, @EnableAutoConfiguration, @ComponentScan
@EnableScheduling       // Enables @Scheduled methods for auto-indexing
```

**Startup Flow**:
1. `main()` calls `SpringApplication.run()`
2. Spring initializes beans: properties, services, controllers
3. Embedded Tomcat starts on port 8080
4. LogFileIndexer's `@PostConstruct` triggers initial indexing

---

### 2. CommandLineRunner.java
**Package**: `com.lsearch.logsearch.cli`
**Type**: Component

**Responsibilities**:
- Parses command-line arguments (`start`, `index`, `--logs-dir`, `--index-dir`)
- Overrides configuration properties from arguments
- Executes commands (start server or index-only mode)

**Commands**:
- `start` (default): Index logs + start web server
- `index`: Index logs and exit (no web server)

**Example Usage**:
```bash
java -jar log-search.jar start --logs-dir=/path/to/logs
java -jar log-search.jar index --logs-dir=/path/to/logs
```

---

### 3. LogSearchProperties.java
**Package**: `com.lsearch.logsearch.config`
**Type**: Configuration Class

**Responsibilities**:
- Binds YAML configuration to Java object
- Provides getter/setter methods for all config properties

**Key Annotation**:
```java
@ConfigurationProperties(prefix = "log-search")
```

**Configuration Fields**:
```java
private String logsDir;              // ./logs
private String indexDir;             // ./.log-search/indexes
private String filePattern;          // server-\\d{8}\\.log
private String filenameDatePattern;  // server-(\\d{4})(\\d{2})(\\d{2})\\.log
private String logLinePattern;       // ^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$
private String logDatetimeFormat;    // yyyy-MM-dd'T'HH:mm:ss.SSSXXX
private String timezone;             // Pacific/Auckland
private int retentionDays;           // 30
private boolean autoWatch;           // true
private int watchInterval;           // 60 seconds
```

---

### 4. LogSearchController.java
**Package**: `com.lsearch.logsearch.controller`
**Type**: REST Controller

**Responsibilities**:
- Exposes REST API endpoints
- Validates request parameters
- Delegates to service layer
- Formats responses

**Dependencies**:
- `LogSearchService` - for executing searches
- `LogFileIndexer` - for triggering indexing

**Endpoints**: See [API Endpoints](#api-endpoints) section

---

### 5. LogEntry.java
**Package**: `com.lsearch.logsearch.model`
**Type**: Domain Model

**Responsibilities**:
- Represents a single parsed log entry
- Immutable data transfer object

**Fields**:
```java
private ZonedDateTime timestamp;  // Parsed timestamp with timezone
private String level;             // Log level (ERROR, WARN, INFO, DEBUG) - optional
private String thread;            // Thread name - optional
private String logger;            // Logger/class name - optional
private String user;              // User from [user] field - optional
private String message;           // Log message content (may include multi-line stack traces)
private String sourceFile;        // Original log filename
private long lineNumber;          // Line number in source file
```

**Builder Pattern**:
```java
LogEntry entry = LogEntry.builder()
    .timestamp(timestamp)
    .user("john.doe")
    .message("Application started")
    .sourceFile("server-20260312.log")
    .lineNumber(42)
    .build();
```

---

### 6. SearchResult.java
**Package**: `com.lsearch.logsearch.model`
**Type**: Response Model

**Responsibilities**:
- Wraps search results with metadata
- Provides pagination information

**Fields**:
```java
private List<LogEntry> entries;   // Actual log entries
private long totalHits;           // Total matching documents (across all pages)
private int page;                 // Current page number (0-indexed)
private int pageSize;             // Results per page
private long searchTimeMs;        // Search execution time
```

---

### 7. LogParser.java
**Package**: `com.lsearch.logsearch.service`
**Type**: Service

**Responsibilities**:
- Parses raw log lines into `LogEntry` objects
- Supports both 3-group (simple) and 6-group (enterprise) log formats
- Handles multi-line log entries (stack traces, exception details)
- 3-tier fallback parsing for non-matching lines
- Handles timestamp parsing with timezone
- Uses configurable regex pattern from application.yml

**Supported Formats**:

1. **3-Group Pattern** - `[timestamp] [user] message`:
```
^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$
```

2. **6-Group Pattern** - `[timestamp] [thread] [level] [logger] [] [user:username] - message`:
```
^\\[([^\\]]+)\\]\\s+(.+?)\\s+\\[([^\\]]+)\\]\\s+\\[([^\\]]+)\\]\\s+\\[\\]\\s+\\[user:([^\\]]+)\\]\\s+-\\s+(.*)$
```

**Multi-line Support**:
```java
public boolean looksLikeContinuationLine(String line) {
    // Heuristic 1: Starts with whitespace
    if (Character.isWhitespace(line.charAt(0))) return true;

    // Heuristic 2: Common stack trace patterns
    if (trimmed.startsWith("at ") ||
        trimmed.startsWith("Caused by:") ||
        trimmed.startsWith("Suppressed:")) {
        return true;
    }

    // Heuristic 3: No timestamp-like pattern at beginning
    return !hasTimestampLikeStart;
}
```

**5-Tier Fallback Parsing**:
```java
public LogEntry parseLine(String line, String sourceFile, long lineNumber, Path filePath) {
    // Tier 1: Try primary configured pattern
    LogEntry entry = tryPrimaryPattern(line, sourceFile, lineNumber);
    if (entry != null) return entry;

    // If fallback disabled, stop here
    if (!properties.isEnableFallback()) return null;

    // Tier 2: Try JSON format
    entry = tryJsonFormat(line, sourceFile, lineNumber);
    if (entry != null) return entry;

    // Tier 3: Try Apache/nginx access log format
    entry = tryApacheFormat(line, sourceFile, lineNumber);
    if (entry != null) return entry;

    // Tier 4: Auto-detect timestamp in line
    // Tries: ISO 8601, Apache/NCSA, Syslog, etc.
    entry = tryTimestampExtraction(line, sourceFile, lineNumber);
    if (entry != null) return entry;

    // Tier 5: Use file metadata (filename date or file modified time)
    return createFallbackEntry(line, sourceFile, lineNumber, filePath);
}
```

**JSON Log Parsing**:
```java
private LogEntry tryJsonFormat(String line, String sourceFile, long lineNumber) {
    String trimmed = line.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return null;
    }

    // Extract common JSON log fields using regex
    String timestamp = extractJsonField(trimmed, "timestamp", "time", "date", "@timestamp");
    String level = extractJsonField(trimmed, "level", "severity", "loglevel");
    String message = extractJsonField(trimmed, "message", "msg", "text");
    String logger = extractJsonField(trimmed, "logger", "name", "component");
    String thread = extractJsonField(trimmed, "thread", "threadName");
    String user = extractJsonField(trimmed, "user", "userId", "username");

    // Build LogEntry from extracted fields
    return LogEntry.builder()
            .timestamp(parsedTimestamp)
            .level(level)
            .message(message)
            .logger(logger)
            .thread(thread)
            .user(user)
            .sourceFile(sourceFile)
            .lineNumber(lineNumber)
            .build();
}
```

**Apache/Nginx Log Parsing**:
```java
private LogEntry tryApacheFormat(String line, String sourceFile, long lineNumber) {
    // Apache/nginx combined log format:
    // 127.0.0.1 - user [timestamp] "GET /path HTTP/1.1" 200 1234 "referer" "user-agent"
    Pattern apachePattern = Pattern.compile(
        "^([\\d.]+) \\S+ (\\S+) \\[([^\\]]+)\\] \"([A-Z]+) ([^\"]+) HTTP/[^\"]+\" (\\d{3}) (\\d+|-).*"
    );

    // Parse and extract IP, user, timestamp, method, path, status
    // Map status codes to log levels (4xx/5xx = ERROR, others = INFO)
    DateTimeFormatter apacheFormatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");
    ZonedDateTime timestamp = ZonedDateTime.parse(timestampStr, apacheFormatter);

    return LogEntry.builder()
            .timestamp(timestamp)
            .level(level)
            .message(message)
            .user(user.equals("-") ? null : user)
            .sourceFile(sourceFile)
            .lineNumber(lineNumber)
            .build();
}
```

**Parsing Logic** (3-group example):
```java
Matcher matcher = logPattern.matcher(line);
if (!matcher.matches()) return null;

String timestampStr = matcher.group(1);   // [2026-03-12T14:30:45.123+13:00]
String user = matcher.group(2);           // [john.doe]
String message = matcher.group(3);        // Application started

DateTimeFormatter formatter = DateTimeFormatter.ofPattern(properties.getLogDatetimeFormat());
ZonedDateTime timestamp = ZonedDateTime.parse(timestampStr, formatter);

return LogEntry.builder()
    .timestamp(timestamp)
    .user(user)
    .message(message)
    .sourceFile(sourceFile)
    .lineNumber(lineNumber)
    .build();
```

**Parsing Logic** (6-group example):
```java
if (groupCount >= 6) {
    builder.timestamp(parsedTimestamp)
           .thread(matcher.group(2))      // [[ACTIVE] ExecuteThread: '5']
           .level(matcher.group(3))       // ERROR
           .logger(matcher.group(4))      // com.example.Handler
           .user(matcher.group(5))        // admin
           .message(matcher.group(6))     // Error message
           .sourceFile(sourceFile)
           .lineNumber(lineNumber);
}
```

---

### 8. CodeAnalyzer.java
**Package**: `com.lsearch.logsearch.service`
**Type**: Lucene Analyzer (extends `org.apache.lucene.analysis.Analyzer`)

**Responsibilities**:
- Custom tokenization optimized for Java code and stack traces
- Splits text on code-specific delimiters (dots, parentheses, colons, etc.)
- Enables searching for Java class names without full package paths
- Applies lowercase filtering and stop word removal

**Problem Solved**:
StandardAnalyzer treats dotted names like `com.example.DataValidator` as single tokens or email addresses, making individual components unsearchable. CodeAnalyzer splits them properly.

**Tokenization Pattern**:
```java
// Splits on: . ( ) : @ $ [ ] < > ; , / \ and whitespace
private static final Pattern TOKEN_PATTERN =
    Pattern.compile("[\\s.():@$\\[\\]<>;,/\\\\]+");
```

**Examples**:

| Input | StandardAnalyzer Tokens | CodeAnalyzer Tokens |
|-------|------------------------|---------------------|
| `com.example.DataValidator` | `com.example.datavalidator` | `com`, `example`, `datavalidator` |
| `validateLevel78(DataValidator.java:1232)` | `validatelevel78`, `datavalidator.java`, `1232` | `validatelevel78`, `datavalidator`, `java`, `1232` |
| `OutOfMemoryError` | `outofmemoryerror` | `outofmemoryerror` |
| `org.springframework.web.servlet` | `org.springframework.web.servlet` | `org`, `springframework`, `web`, `servlet` |

**Token Stream Pipeline**:
```java
@Override
protected TokenStreamComponents createComponents(String fieldName) {
    // 1. Tokenize using pattern splitter
    Tokenizer tokenizer = new PatternTokenizer(TOKEN_PATTERN, -1);

    // 2. Convert to lowercase
    TokenStream tokenStream = new LowerCaseFilter(tokenizer);

    // 3. Remove English stop words (the, a, at, is, etc.)
    tokenStream = new StopFilter(tokenStream, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);

    return new TokenStreamComponents(tokenizer, tokenStream);
}
```

**Impact on Search**:
- **Before** (StandardAnalyzer): `DataValidator` → 0 results
- **After** (CodeAnalyzer): `DataValidator` → finds all occurrences in any package

**Used By**:
- `LuceneIndexService` - during indexing
- `LogSearchService` - during search query parsing

---

### 9. LuceneIndexService.java
**Package**: `com.lsearch.logsearch.service`
**Type**: Service

**Responsibilities**:
- Manages Lucene `IndexWriter` instances (one per day)
- Creates day-based index directories
- Adds documents to appropriate day index
- Commits and closes index writers
- Deletes old indexes based on retention policy

**Key Data Structure**:
```java
// Thread-safe map of date -> IndexWriter
private final Map<String, IndexWriter> indexWriters = new ConcurrentHashMap<>();
```

**Index Writer Creation**:
```java
private IndexWriter getOrCreateIndexWriter(String date) throws IOException {
    return indexWriters.computeIfAbsent(date, d -> {
        // Create directory: .log-search/indexes/2026-03-12/
        Path indexPath = Paths.get(properties.getIndexDir(), date);
        Files.createDirectories(indexPath);

        // Open Lucene directory
        Directory directory = FSDirectory.open(indexPath);

        // Configure index writer
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        return new IndexWriter(directory, config);
    });
}
```

**Document Indexing**:
```java
public void indexLogEntry(LogEntry entry) throws IOException {
    // Determine date: 2026-03-12
    String date = entry.getTimestamp().toLocalDate().format(DateTimeFormatter.ISO_DATE);

    // Get or create writer for this date
    IndexWriter writer = getOrCreateIndexWriter(date);

    // Create Lucene document
    Document doc = new Document();
    doc.add(new LongPoint("timestamp", entry.getTimestamp().toInstant().toEpochMilli()));
    doc.add(new StoredField("timestamp", entry.getTimestamp().toInstant().toEpochMilli()));
    doc.add(new TextField("user", entry.getUser(), Field.Store.YES));
    doc.add(new TextField("message", entry.getMessage(), Field.Store.YES));
    doc.add(new StringField("sourceFile", entry.getSourceFile(), Field.Store.YES));
    doc.add(new LongPoint("lineNumber", entry.getLineNumber()));
    doc.add(new StoredField("lineNumber", entry.getLineNumber()));

    writer.addDocument(doc);
}
```

**Retention Management**:
```java
public void deleteOldIndexes() throws IOException {
    LocalDate cutoffDate = LocalDate.now().minusDays(properties.getRetentionDays());

    // Delete directories older than cutoff
    Files.list(Paths.get(properties.getIndexDir()))
        .filter(Files::isDirectory)
        .forEach(path -> {
            LocalDate indexDate = LocalDate.parse(path.getFileName().toString());
            if (indexDate.isBefore(cutoffDate)) {
                deleteDirectory(path);  // Recursively delete
            }
        });
}
```

**Lifecycle Management**:
```java
@PreDestroy
public void shutdown() {
    // Close all index writers on application shutdown
    for (IndexWriter writer : indexWriters.values()) {
        writer.close();
    }
}
```

---

### 10. LogFileIndexer.java
**Package**: `com.lsearch.logsearch.service`
**Type**: Service

**Responsibilities**:
- Scans log directory for matching files
- Tracks indexed files to avoid re-indexing
- Schedules periodic re-indexing (auto-watch)
- Triggers retention cleanup

**Key Data Structures**:
```java
private final Set<String> indexedFiles = ConcurrentHashMap.newKeySet();
private final AtomicInteger indexedFileCount = new AtomicInteger(0);
```

**Initial Indexing**:
```java
@PostConstruct
public void initialize() throws IOException {
    log.info("Log File Indexer initialized");
    indexAllLogs();  // Index on startup
}
```

**Scheduled Indexing**:
```java
@Scheduled(fixedDelayString = "${log-search.watch-interval}000")
public void scheduledIndexing() {
    if (properties.isAutoWatch()) {
        indexAllLogs();
    }
}
```

**Indexing Logic**:
```java
public void indexAllLogs() throws IOException {
    Path logsDir = Paths.get(properties.getLogsDir());
    Pattern filePattern = Pattern.compile(properties.getFilePattern());

    // Find all matching log files
    Files.list(logsDir)
        .filter(Files::isRegularFile)
        .filter(path -> filePattern.matcher(path.getFileName().toString()).matches())
        .forEach(this::indexFile);

    // Commit all changes
    luceneIndexService.commit();

    // Cleanup old indexes
    luceneIndexService.deleteOldIndexes();
}

private void indexFile(Path filePath) {
    String fileName = filePath.getFileName().toString();

    // Skip if already indexed
    if (indexedFiles.contains(fileName)) {
        return;
    }

    log.info("Indexing log file: {}", fileName);

    long lineNumber = 1;
    int indexedCount = 0;

    try (BufferedReader reader = Files.newBufferedReader(filePath)) {
        String line;
        while ((line = reader.readLine()) != null) {
            LogEntry entry = logParser.parseLine(line, fileName, lineNumber);
            if (entry != null) {
                luceneIndexService.indexLogEntry(entry);
                indexedCount++;
            }
            lineNumber++;
        }
    }

    indexedFiles.add(fileName);
    indexedFileCount.incrementAndGet();

    log.info("Completed indexing {}: {} indexed", fileName, indexedCount);
}
```

---

### 11. LogSearchService.java
**Package**: `com.lsearch.logsearch.service`
**Type**: Service

**Responsibilities**:
- Executes searches across multiple day indexes
- Builds Lucene queries (text + time range)
- Merges results from multiple indexes
- Applies sorting and pagination

**Search Flow**:
```java
public SearchResult search(String queryText, ZonedDateTime startTime,
                          ZonedDateTime endTime, int page, int pageSize) {
    // 1. Determine which day indexes to search
    List<String> datesToSearch = getDateRangeDirs(startTime, endTime);
    // Example: ["2026-03-11", "2026-03-12", "2026-03-13"]

    List<LogEntry> allResults = new ArrayList<>();
    long totalHits = 0;

    // 2. Search each day index
    for (String date : datesToSearch) {
        Path indexPath = Paths.get(properties.getIndexDir(), date);
        Directory directory = FSDirectory.open(indexPath);
        IndexReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);

        // 3. Build query
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

        // Text search (multi-field: message and user)
        if (queryText != null && !queryText.isEmpty()) {
            String[] fields = {"message", "user"};
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);
            Query textQuery = parser.parse(queryText);
            queryBuilder.add(textQuery, BooleanClause.Occur.MUST);
        }

        // Time range filter
        long startEpoch = startTime.toInstant().toEpochMilli();
        long endEpoch = endTime.toInstant().toEpochMilli();
        Query timeRangeQuery = LongPoint.newRangeQuery("timestamp", startEpoch, endEpoch);
        queryBuilder.add(timeRangeQuery, BooleanClause.Occur.MUST);

        // 4. Execute search
        TopDocs topDocs = searcher.search(queryBuilder.build(), 10000);
        totalHits += topDocs.totalHits.value;

        // 5. Collect results
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            allResults.add(documentToLogEntry(doc));
        }

        reader.close();
        directory.close();
    }

    // 6. Sort by timestamp (most recent first)
    allResults.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

    // 7. Apply pagination
    int startIndex = page * pageSize;
    int endIndex = Math.min(startIndex + pageSize, allResults.size());
    List<LogEntry> paginatedResults = allResults.subList(startIndex, endIndex);

    return SearchResult.builder()
        .entries(paginatedResults)
        .totalHits(totalHits)
        .page(page)
        .pageSize(pageSize)
        .searchTimeMs(searchTimeMs)
        .build();
}
```

**Date Range Directory Lookup**:
```java
private List<String> getDateRangeDirs(ZonedDateTime startTime, ZonedDateTime endTime) {
    LocalDate startDate = startTime.toLocalDate();  // 2026-03-11
    LocalDate endDate = endTime.toLocalDate();      // 2026-03-13

    // List all subdirectories in index directory
    // Filter to only dates within range
    // Return sorted list: ["2026-03-11", "2026-03-12", "2026-03-13"]
}
```

---

## Data Flow - Indexing

### Startup Indexing Flow

```
1. Application Startup
   └─> LogSearchApplication.main()
       └─> Spring Boot initializes beans
           └─> LogFileIndexer bean created
               └─> @PostConstruct initialize()

2. LogFileIndexer.initialize()
   └─> indexAllLogs()
       └─> Scans ./logs directory
           └─> Filters files matching "server-\\d{8}\\.log"
               └─> For each file: indexFile(path)

3. LogFileIndexer.indexFile(path)
   └─> Reads file line by line
       └─> For each line: LogParser.parseLine()
           └─> Regex match: [timestamp] [user] message
               └─> Parse timestamp with timezone
                   └─> Returns LogEntry object

4. LogEntry Processing
   └─> LuceneIndexService.indexLogEntry(entry)
       └─> Extracts date from timestamp (2026-03-12)
           └─> Gets or creates IndexWriter for "2026-03-12"
               └─> Creates Lucene Document
                   └─> Adds fields: timestamp, user, message, etc.
                       └─> writer.addDocument(doc)

5. Post-Indexing
   └─> LuceneIndexService.commit()
       └─> Commits all index writers (flushes to disk)
           └─> LuceneIndexService.deleteOldIndexes()
               └─> Deletes indexes older than retention period

6. Result
   └─> Indexes created:
       .log-search/indexes/
       ├── 2026-03-11/
       ├── 2026-03-12/
       └── 2026-03-13/
```

### Auto-Watch Indexing (Scheduled)

```
Every 60 seconds (configurable):

@Scheduled(fixedDelayString = "${log-search.watch-interval}000")
└─> LogFileIndexer.scheduledIndexing()
    └─> Checks if autoWatch = true
        └─> indexAllLogs()
            └─> Scans for new files
                └─> Skips already indexed files (via indexedFiles Set)
                    └─> Indexes only new files
```

---

## Data Flow - Searching

### Search Request Flow

```
1. HTTP Request
   GET /api/search?query=error&startTime=2026-03-12T00:00:00Z&endTime=2026-03-12T23:59:59Z

2. LogSearchController.search()
   └─> Validates parameters
       └─> Logs search request
           └─> Calls LogSearchService.search()

3. LogSearchService.search()
   └─> getDateRangeDirs(startTime, endTime)
       └─> Determines day indexes to search
           Example: ["2026-03-12"]

4. For each day index:
   └─> Opens Lucene Directory and IndexReader
       └─> Creates IndexSearcher
           └─> Builds BooleanQuery:
               ├─> Text query (MultiFieldQueryParser on message + user)
               └─> Time range query (LongPoint.newRangeQuery)

5. Execute Search
   └─> searcher.search(query, 10000)
       └─> Returns TopDocs (matching document IDs + scores)
           └─> For each ScoreDoc:
               └─> searcher.doc(docId) retrieves Document
                   └─> documentToLogEntry() converts to LogEntry

6. Merge and Sort
   └─> Combine results from all day indexes
       └─> Sort by timestamp (descending)
           └─> Apply pagination (page * pageSize)

7. Return Response
   └─> SearchResult object:
       ├─> entries: List<LogEntry>
       ├─> totalHits: 42
       ├─> page: 0
       ├─> pageSize: 100
       └─> searchTimeMs: 156
```

### Query Examples

**Simple Text Search**:
```
User Input: error
Lucene Query: (message:error OR user:error) AND timestamp:[startEpoch TO endEpoch]
```

**Phrase Search**:
```
User Input: "database connection failed"
Lucene Query: (message:"database connection failed" OR user:"database connection failed") AND timestamp:[...]
```

**Boolean Search**:
```
User Input: error AND database
Lucene Query: ((message:error AND message:database) OR (user:error AND user:database)) AND timestamp:[...]
```

**Wildcard Search**:
```
User Input: user*
Lucene Query: (message:user* OR user:user*) AND timestamp:[...]
```

---

## Lucene Index Structure

### Directory Layout

```
.log-search/indexes/
│
├── 2026-03-10/                    # Day-based index directory
│   ├── _0.cfe                     # Compound file entries
│   ├── _0.cfs                     # Compound file segments
│   ├── _0.si                      # Segment info
│   ├── segments_1                 # Segments metadata
│   └── write.lock                 # Index lock file
│
├── 2026-03-11/
│   └── (same structure)
│
└── 2026-03-12/
    └── (same structure)
```

### Document Schema

Each indexed log entry becomes a Lucene Document with these fields:

| Field Name | Type | Indexed | Stored | Purpose |
|------------|------|---------|--------|---------|
| `timestamp` | LongPoint + StoredField | Yes (range queries) | Yes | Log timestamp as epoch millis |
| `date` | StringField | Yes | Yes | Date string (2026-03-12) for filtering |
| `user` | TextField | Yes (tokenized) | Yes | User field from log, searchable |
| `message` | TextField | Yes (tokenized) | Yes | Main log message, searchable |
| `sourceFile` | StringField | Yes (exact) | Yes | Original log filename |
| `lineNumber` | LongPoint + StoredField | Yes (range queries) | Yes | Line number in source file |

### Field Types Explained

**LongPoint**:
- Efficient range queries (timestamp between X and Y)
- Multi-dimensional indexing
- Not stored (needs separate StoredField)

**StoredField**:
- Stores value for retrieval
- Not indexed (can't search on it)
- Used to get actual value when displaying results

**TextField**:
- Tokenized and analyzed (split into words)
- Supports full-text search, wildcards, phrases
- Stored for retrieval

**StringField**:
- Indexed as single token (exact match)
- Not analyzed (case-sensitive)

### Analyzer Configuration

```java
StandardAnalyzer analyzer = new StandardAnalyzer();
```

**StandardAnalyzer behavior**:
- Tokenizes on whitespace and punctuation
- Lowercases tokens
- Removes common stop words (optional)

**Example**:
```
Input:  "ERROR: Database connection failed!"
Tokens: ["error", "database", "connection", "failed"]
```

---

## API Endpoints

### 1. Search Logs

**Endpoint**: `GET /api/search`

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `query` | String | No | Search query (Lucene syntax) |
| `startTime` | ISO-8601 DateTime | Yes | Start of time range |
| `endTime` | ISO-8601 DateTime | Yes | End of time range |
| `page` | Integer | No | Page number (0-indexed, default: 0) |
| `pageSize` | Integer | No | Results per page (default: 100) |

**Example Request**:
```bash
curl "http://localhost:8080/api/search?query=NullPointerException&startTime=2026-03-12T00:00:00Z&endTime=2026-03-12T23:59:59Z&page=0&pageSize=50"
```

**Response**:
```json
{
  "entries": [
    {
      "timestamp": "2026-03-12T14:30:45.123+13:00",
      "user": "john.doe",
      "message": "ERROR: NullPointerException in OrderService",
      "sourceFile": "server-20260312.log",
      "lineNumber": 142
    }
  ],
  "totalHits": 25,
  "page": 0,
  "pageSize": 50,
  "searchTimeMs": 156
}
```

**Search Query Syntax**:
```
Simple:         error
Phrase:         "database connection"
Boolean:        error AND database
Wildcard:       user*
NOT:            error NOT warning
Field-specific: user:john.doe
Regex:          /error.*/
```

---

### 2. Trigger Indexing

**Endpoint**: `POST /api/index`

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `fullReindex` | Boolean | No | If `true`, deletes all indexes and rebuilds from scratch. Default: `false` |

**Description**: Manually triggers re-indexing of log files

**Behavior**:
- **Incremental** (default): Only indexes new files that haven't been indexed yet
- **Full Re-index** (`fullReindex=true`): Deletes all existing indexes, clears tracked files, and re-indexes all logs from scratch

**Example Requests**:
```bash
# Incremental re-index (only new files)
curl -X POST http://localhost:8080/api/index

# Full re-index (delete and rebuild all)
curl -X POST "http://localhost:8080/api/index?fullReindex=true"
```

**Response**:
```json
{
  "success": true,
  "message": "Indexing completed",
  "indexedFiles": 13
}
```

**Use Cases for Full Re-index**:
- Changed log format settings in `config/application.yml`
- Changed `log-line-pattern`, `log-datetime-format`, or `timezone`
- Troubleshooting indexing issues
- Ensuring all logs are parsed with current configuration

---

### 4. Get Aggregations

**Endpoint**: `GET /api/aggregations`

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `query` | String | No | Search query (same as /api/search) |
| `startTime` | ISO-8601 DateTime | Yes | Start of time range |
| `endTime` | ISO-8601 DateTime | Yes | End of time range |

**Description**: Returns aggregations and analytics for the search results

**Example Request**:
```bash
curl "http://localhost:8080/api/aggregations?query=error&startTime=2026-03-12T00:00:00Z&endTime=2026-03-13T23:59:59Z"
```

**Response**:
```json
{
  "totalHits": 1523,
  "levelFacets": [
    {"value": "ERROR", "count": 456, "percentage": 29.9},
    {"value": "WARN", "count": 234, "percentage": 15.4}
  ],
  "exceptionFacets": [
    {"value": "NullPointerException", "count": 140, "percentage": 9.2}
  ],
  "loggerFacets": [...],
  "userFacets": [...],
  "fileFacets": [...],
  "timelineHourly": {
    "2026-03-12T14:00:00Z": 45,
    "2026-03-12T15:00:00Z": 67
  },
  "timelineByLevel": {
    "2026-03-12T14:00:00Z": {"ERROR": 10, "WARN": 20, "INFO": 15}
  },
  "detectedPatterns": [
    "Spike detected at 15:00 (3x average)",
    "NullPointerException in EvidenceProcessor (140), WorkflowEngine (100)"
  ]
}
```

---

### 5. Get Log Context

**Endpoint**: `GET /api/context`

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `sourceFile` | String | Yes | Log filename |
| `lineNumber` | Long | Yes | Target line number |
| `contextLines` | Integer | No | Lines before/after (default: 10) |

**Description**: Returns surrounding log lines for context

**Example Request**:
```bash
curl "http://localhost:8080/api/context?sourceFile=server-20260313.log&lineNumber=142&contextLines=5"
```

**Response**:
```json
{
  "sourceFile": "server-20260313.log",
  "targetLine": 142,
  "totalLines": 5000,
  "contextLines": [
    {"lineNumber": 137, "content": "[13 Mar 2026...] Log line", "isTarget": false},
    {"lineNumber": 142, "content": "[13 Mar 2026...] ERROR: NPE", "isTarget": true},
    {"lineNumber": 147, "content": "[13 Mar 2026...] Recovery", "isTarget": false}
  ]
}
```

---

### 2. Trigger Indexing (continued)

**Example Request**:
```bash
curl -X POST http://localhost:8080/api/index
```

**Response**:
```json
{
  "success": true,
  "message": "Indexing completed",
  "indexedFiles": 10
}
```

---

### 3. Check Status

**Endpoint**: `GET /api/status`

**Description**: Returns application status and metrics

**Example Request**:
```bash
curl http://localhost:8080/api/status
```

**Response**:
```json
{
  "status": "running",
  "indexedFiles": 10
}
```

---

## Configuration System

### Configuration Hierarchy (Priority Order)

1. **Command-line arguments** (highest priority)
   ```bash
   java -jar log-search.jar --logs-dir=/custom/path
   ```

2. **External config file**: `./config/application.yml`
   ```bash
   java -jar log-search.jar --spring.config.location=file:./config/application.yml
   ```

3. **Bundled config**: `src/main/resources/application.yml` (JAR internal)

4. **Default values** in `LogSearchProperties.java`

### External Configuration File

**Location**: `config/application.yml` (same directory as JAR)

**Full Configuration**:
```yaml
server:
  port: 8080

log-search:
  # Directory containing log files
  logs-dir: ${LOGS_DIR:./logs}

  # Directory for Lucene indexes
  index-dir: ${INDEX_DIR:./.log-search/indexes}

  # Regex pattern for log file names
  file-pattern: "server-\\d{8}\\.log"

  # Regex to extract date from filename
  # Groups: (year)(month)(day)
  filename-date-pattern: "server-(\\d{4})(\\d{2})(\\d{2})\\.log"

  # Regex pattern for log lines (3 capture groups: timestamp, user, message)
  # Default: [timestamp] [user] message
  # Examples:
  #   Default:  ^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$
  #   Apache:   ^(\\S+)\\s+(\\S+)\\s+(.*)$
  #   Syslog:   ^(\\S+\\s+\\d+\\s+\\S+)\\s+(\\S+)\\s+(.*)$
  log-line-pattern: "^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$"

  # Date format in log lines (Java DateTimeFormatter)
  log-datetime-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"

  # Timezone for parsing logs
  timezone: "Pacific/Auckland"

  # Days to retain indexes (0 = unlimited)
  retention-days: 30

  # Auto-watch for new log files
  auto-watch: true

  # Watch interval in seconds
  watch-interval: 60

logging:
  level:
    com.lsearch.logsearch: INFO
```

### Environment Variable Overrides

```bash
export LOGS_DIR=/var/log/myapp
export INDEX_DIR=/data/indexes
java -jar log-search.jar
```

---

## Build and Deployment

### Building from Source

**Prerequisites**:
- Java 8 or higher
- Maven 3.6+

**Build Steps**:
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package as executable JAR
mvn clean package

# Skip tests (faster)
mvn clean package -DskipTests
```

**Output**:
```
target/log-search-1.0.0.jar    (Executable JAR, ~15-20 MB)
```

### Deployment Package Structure

```
log-search-distribution/
│
├── log-search-1.0.0.jar       # Executable JAR
│
├── config/
│   └── application.yml        # External configuration
│
├── start.sh                   # Linux/Mac startup script
├── start.bat                  # Windows startup script
│
├── stop.sh                    # Linux/Mac shutdown script
├── stop.bat                   # Windows shutdown script
│
├── generate-logs.sh           # Sample log generator
│
└── README.md                  # User documentation
```

### Running the Application

**Option 1: Using startup script (recommended)**
```bash
./start.sh start --logs-dir=/path/to/logs
```

**Option 2: Direct Java execution**
```bash
java -jar log-search-1.0.0.jar start --logs-dir=./logs
```

**Option 3: With external config**
```bash
java -jar log-search-1.0.0.jar --spring.config.location=file:./config/application.yml
```

**Option 4: Index-only mode (no web server)**
```bash
java -jar log-search-1.0.0.jar index --logs-dir=./logs
```

### JVM Tuning

**For large log volumes**:
```bash
java -Xmx2g -Xms512m -jar log-search-1.0.0.jar
```

**Parameters**:
- `-Xmx2g`: Max heap size (2 GB)
- `-Xms512m`: Initial heap size (512 MB)
- `-XX:+UseG1GC`: Use G1 garbage collector (optional)

---

## Performance Characteristics

### Index Size

| Raw Logs | Lucene Index | Compression Ratio |
|----------|--------------|-------------------|
| 2 GB/day | 400-800 MB/day | 20-40% of raw size |
| 30 days | 12-24 GB | With 30-day retention |

### Search Performance (Typical)

| Date Range | Search Time | Notes |
|------------|-------------|-------|
| 1 day | 50-200 ms | Single index |
| 7 days | 200-500 ms | 7 indexes |
| 30 days | 500-1500 ms | 30 indexes |

**Hardware**: Modern laptop (SSD, 16GB RAM, 4-core CPU)
**Log Volume**: 2GB raw logs per day

### Optimization Tips

1. **Narrow date ranges**: Only search days you need
2. **Specific queries**: More specific = faster (fewer matches)
3. **SSD storage**: Significantly faster than HDD
4. **Retention policy**: Keep only needed days (reduces search scope)
5. **JVM heap**: Allocate enough memory for index caching

---

## Thread Safety

### Thread-Safe Components

1. **LuceneIndexService.indexWriters**: `ConcurrentHashMap`
2. **LogFileIndexer.indexedFiles**: `ConcurrentHashMap.newKeySet()`
3. **LogFileIndexer.indexedFileCount**: `AtomicInteger`

### Concurrency Model

- **Indexing**: Single-threaded (via `@Scheduled` task)
- **Searching**: Multi-threaded (Spring Boot handles multiple concurrent requests)
- **Index Writers**: One per day, shared across search threads (read-only)

### Synchronization Points

- **Index commit**: Synchronized via Lucene's internal locks
- **Directory creation**: Atomic via `Files.createDirectories()`
- **File tracking**: Synchronized via `ConcurrentHashMap`

---

## Error Handling

### Parsing Errors

- **Malformed lines**: Skipped, logged at DEBUG level
- **Invalid timestamps**: Line skipped, counted in "skipped" metric
- **File read errors**: Logged as ERROR, indexing continues with next file

### Indexing Errors

- **IOException during indexing**: Logged, current file skipped
- **Disk space errors**: Logged, indexing stops
- **Lock file conflicts**: Logged, waits for lock release

### Search Errors

- **Invalid query syntax**: Returns HTTP 500 with error message
- **Missing index directories**: Returns empty result set
- **Corrupted index**: Logged, skips corrupted day index

### Shutdown Handling

```java
@PreDestroy
public void shutdown() {
    // Gracefully closes all index writers
    // Ensures data is flushed to disk
    // Prevents index corruption
}
```

---

## Security Considerations

### Current Security Posture

- **No authentication**: Open API endpoints
- **CORS enabled**: `@CrossOrigin(origins = "*")`
- **Local deployment**: Designed for developer machines
- **No encryption**: Data stored in plaintext

### Production Hardening (If Needed)

1. **Add Spring Security** for authentication
2. **Restrict CORS** to specific origins
3. **Enable HTTPS** with SSL certificates
4. **Input validation** on search queries (prevent injection)
5. **Rate limiting** to prevent abuse
6. **Encrypt sensitive log data** before indexing

---

## Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=LogParserTest

# Run with coverage
mvn test jacoco:report
```

### Test Categories

1. **Unit Tests**: Individual class testing (LogParser, etc.)
2. **Integration Tests**: Spring Boot context tests
3. **Service Tests**: Lucene indexing/searching tests

---

## Troubleshooting

### Common Issues

**Issue**: No search results
**Solution**: Check date range includes log files, verify file pattern matches

**Issue**: OutOfMemoryError
**Solution**: Increase JVM heap: `java -Xmx2g -jar log-search.jar`

**Issue**: Slow searches
**Solution**: Narrow date range, reduce retention period, use SSD

**Issue**: Logs not parsing
**Solution**: Verify log format matches regex in `LogParser.LOG_PATTERN`

**Issue**: Index corruption
**Solution**: Delete `.log-search/indexes/*` and re-index

---

## Frontend Features

### User Interface Components

The application includes a modern, responsive web interface built with vanilla JavaScript and Chart.js.

**Main Tabs**:
1. **Search Logs**: Main search interface with analytics sidebar
2. **Download Logs**: Bulk log download functionality
3. **Dashboards**: Custom dashboard management

**Search Features**:
- **Quick Time Range Buttons**: Last 1h, 6h, 24h, 2d, 7d
- **Relative Time Support**: Auto-updating time ranges (stored as relative offsets)
- **Saved Searches**: localStorage-based persistence with relative time tracking
- **Field Highlighting**: Automatic pattern recognition and highlighting:
  - Error keywords (red background)
  - Java exceptions (light red)
  - IP addresses (purple)
  - URLs (blue)
  - File paths (green)
  - IDs and numbers (light blue)
  - Timestamps (yellow)
- **Context View**: Modal showing ±10 lines around any log entry
- **Faceted Filters**: Click-to-filter on level, exception, logger, user, file

**Dashboard Features**:
- **CRUD Operations**: Create, read, update, delete dashboards
- **Widget Types**:
  - Pie charts (Log Levels Distribution)
  - Stacked bar charts (Timeline by Level)
  - Statistics cards (Error Count, Total Logs)
- **localStorage Persistence**: Client-side dashboard storage
- **Relative Time Refresh**: Dashboards auto-update for relative time ranges
- **Dashboard-to-Search**: "View Search Results" button navigates to search tab with query/time
- **Professional Modals**: Custom modal dialogs matching app design

**Analytics Sidebar**:
- Statistics cards (Total Results, Error Count, Unique Users, Time Span)
- Timeline chart (hourly/daily distribution)
- Pattern alerts (spikes, memory issues, high error rates)
- Top-N facets (exceptions, loggers, users, files)

**Technology Stack (Frontend)**:
- Vanilla JavaScript (no frameworks)
- Chart.js 4.4.0 for visualizations
- CSS Grid and Flexbox for layouts
- localStorage API for client-side persistence
- Fetch API for REST communication

## Future Enhancements

### Potential Improvements

1. **Real-time indexing**: Watch log files with `WatchService` instead of scheduled polling
2. **Distributed search**: Support multiple log-search instances with shared index
3. **Live tail**: WebSocket-based real-time log streaming
4. **Export functionality**: Export search results to CSV/JSON/Excel
5. **Share links**: Shareable URLs with filter state
6. **Performance monitoring**: Track search times, index sizes, etc.
7. **Alert engine**: Email/webhook notifications on patterns

---

## References

- **Apache Lucene**: https://lucene.apache.org/core/
- **Spring Boot**: https://spring.io/projects/spring-boot
- **Lucene Query Syntax**: https://lucene.apache.org/core/8_11_2/queryparser/org/apache/lucene/queryparser/classic/package-summary.html

---

## License

Internal tool - modify as needed for your use case.
