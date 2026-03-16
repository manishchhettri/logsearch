# Search Flow Diagram

## Overview
This document shows the complete flow when a user clicks the Search button in the Log Search application.

The search operation consists of **two parallel flows**:

1. **Main Search Flow** (Synchronous): Returns paginated log entries to display immediately
2. **Aggregations Flow** (Async): Runs in parallel to generate facets, patterns, and charts without blocking the UI

---

## Quick Summary

```
User Clicks Search
      |
      +--> [SYNC] Main Search → Display Log Entries → User sees results
      |
      +--> [ASYNC] Aggregations → Facets + Patterns + Charts → User sees analytics
```

**Key Points:**
- Search results appear first (blocking)
- Aggregations load in background (non-blocking)
- Aggregations only run on first page (page === 0)
- Both flows use same query parameters and time range

---

## Main Flow Diagram

```
index.html (User Interface)
    |
    | [User clicks Search button]
    |
    v
index.html → async function search(page)
    |
    | [Constructs query parameters from form fields]
    | - query
    | - startTime
    | - endTime
    | - page
    | - pageSize
    | - indexes
    | - environments
    |
    v
index.html → fetch(`/api/search?${params}`)
    |
    | [HTTP GET Request - MAIN SEARCH]
    |
    v
LogSearchController.java → @GetMapping("/api/search")
LogSearchController.java → search(...)
    |
    | [Receives request parameters]
    | [Logs search request details]
    |
    v
LogSearchController.java → searchService.search(...)
    |
    v
LogSearchService.java → search(...)
    |
    | [Preprocesses query to handle special characters]
    | [Checks if chunking is enabled]
    |
    +---> [CHUNKING ENABLED] ----------------------------------------+
    |                                                                |
    |                                                                v
    |                                          LogSearchService.java → searchWithMetadata(...)
    |                                                                |
    |                                                                | [Stage 1: Metadata Search]
    |                                                                |
    |                                                                v
    |                                          LogSearchService.java → metadataIndexService.findCandidateChunks(...)
    |                                                                |
    |                                                                | [Returns candidate chunks]
    |                                                                | [90-98% efficiency via Bloom filters]
    |                                                                |
    |                                                                v
    |                                          LogSearchService.java → luceneIndexService.searchChunk(...)
    |                                                                |
    |                                                                | [Stage 2: Parallel chunk search]
    |                                                                | [Searches only candidate chunks]
    |                                                                |
    |                                                                v
    |                                          [Deduplication & Pagination]
    |                                                                |
    |                                                                +---> [Return SearchResult]
    |
    +---> [CHUNKING DISABLED] ---------------------------------------+
                                                                     |
                                                                     v
                                         LogSearchService.java → searchStandard(...)
                                                                     |
                                                                     | [Determines date range indexes]
                                                                     |
                                                                     v
                                         LogSearchService.java → getDateRangeDirs(...)
                                                                     |
                                                                     | [Returns list of day-based indexes]
                                                                     |
                                                                     v
                                         LogSearchService.java → searchDayIndex(...) [Parallel]
                                                                     |
                                                                     | [Searches each day index in parallel]
                                                                     | [Uses Lucene IndexSearcher]
                                                                     | [Applies filters: time, text, index, env]
                                                                     |
                                                                     v
                                         LogSearchService.java → documentToLogEntry(...)
                                                                     |
                                                                     | [Converts Lucene Documents to LogEntry]
                                                                     |
                                                                     v
                                         [Deduplication & Pagination]
                                                                     |
                                                                     +---> [Return SearchResult]
                                                                           |
                                                                           v
                                                     LogSearchController.java → ResponseEntity.ok(result)
                                                                           |
                                                                           | [Returns JSON response with search results]
                                                                           |
                                                                           v
                                                     index.html → displayResults(result)
                                                                           |
                                                                           | [Renders search results]
                                                                           | [Displays log entries]
                                                                           | [Updates pagination]
                                                                           |
                                                                           v
                                                     [User sees search results]
                                                                           |
                                                     index.html → if (page === 0) fetchAggregations()
                                                                           |
                                                                           | [ASYNC - NON-BLOCKING]
                                                                           | [Triggered only on first page]
                                                                           |
                                                                           v
                                                     [See ASYNC AGGREGATIONS FLOW below]
```

---

## Async Aggregations Flow (Parallel - Non-Blocking)

```
index.html → fetchAggregations() [ASYNC - triggered after main search displays]
    |
    | [Constructs same query parameters]
    | - query
    | - startTime
    | - endTime
    |
    v
index.html → fetch(`/api/aggregations?${params}`)
    |
    | [HTTP GET Request - AGGREGATIONS]
    | [Runs in parallel with user viewing search results]
    |
    v
LogSearchController.java → @GetMapping("/api/aggregations")
LogSearchController.java → getAggregations(...)
    |
    | [Receives query, startTime, endTime]
    | [Logs aggregation request]
    |
    v
LogSearchController.java → searchService.getAggregations(...)
    |
    v
LogSearchService.java → getAggregations(...)
    |
    | [Determines date range indexes]
    |
    v
LogSearchService.java → getDateRangeDirs(...)
    |
    | [Returns list of day-based indexes]
    |
    v
LogSearchService.java → For each day index:
    |
    | [Searches each day index sequentially]
    | [Uses same query and time filters]
    |
    v
    | [Collects ALL matching documents (up to 10,000)]
    | [Aggregates data while iterating:]
    |
    +---> LogSearchService.java → levelCounts (Log levels: ERROR, WARN, INFO, etc.)
    |
    +---> LogSearchService.java → exceptionCounts (Extract exception types from messages)
    |
    +---> LogSearchService.java → loggerCounts (Component/class names)
    |
    +---> LogSearchService.java → userCounts (User identifiers)
    |
    +---> LogSearchService.java → fileCounts (Source file paths)
    |
    +---> LogSearchService.java → patternCounts (Pattern fingerprinting)
    |                              |
    |                              | [Groups logs by pattern]
    |                              | [Counts occurrences]
    |                              | [Stores sample messages]
    |                              | [Tracks most common log level per pattern]
    |
    +---> LogSearchService.java → timelineHourly (Time-based distribution)
    |                              |
    |                              | [Dynamic intervals based on time range:]
    |                              | - <= 2 hours: 15-minute intervals
    |                              | - <= 7 days: hourly intervals
    |                              | - > 7 days: daily intervals
    |
    +---> LogSearchService.java → timelineByLevel (Timeline broken down by level)
    |
    v
LogSearchService.java → buildFacets(...)
    |
    | [Converts counts to Facet objects]
    | [Calculates percentages]
    | [Sorts by count descending]
    | [Limits to top N items]
    |
    v
LogSearchService.java → buildPatternSummaries(...)
    |
    | [Creates PatternSummary objects]
    | [Includes: pattern, count, percentage, sample message, level]
    | [Returns top 20 patterns]
    |
    v
LogSearchService.java → detectPatterns(...)
    |
    | [Analyzes entries for interesting patterns:]
    |
    +---> Spike Detection (3x average rate)
    +---> Repeated Exceptions (>= 5 occurrences with class info)
    +---> Memory Issues (OutOfMemory, heap, GC overhead)
    |
    v
LogSearchService.java → Build AggregationResult
    |
    | [Contains:]
    | - totalHits
    | - levelFacets (ERROR, WARN, INFO, etc.)
    | - exceptionFacets (Top exceptions)
    | - loggerFacets (Top components)
    | - userFacets (Top users)
    | - fileFacets (Top source files)
    | - patternSummaries (Top 20 log patterns)
    | - timelineHourly (Time distribution)
    | - timelineByLevel (Time + level distribution)
    | - detectedPatterns (Spikes, repeated errors, etc.)
    |
    v
LogSearchController.java → ResponseEntity.ok(result)
    |
    | [Returns JSON response with aggregations]
    |
    v
index.html → aggregations = await response.json()
index.html → displayAggregations(aggregations)
    |
    v
index.html → displayAggregations(agg)
    |
    +---> index.html → displayStats(agg)
    |                   |
    |                   | [Renders stats grid:]
    |                   | - Total Logs
    |                   | - Errors (from levelFacets)
    |                   | - Warnings (from levelFacets)
    |                   | - Top Exception (from exceptionFacets)
    |                   |
    |                   v
    |             [Updates stats display at top of sidebar]
    |
    +---> index.html → displayFacets(agg)
    |                   |
    |                   | [Renders collapsible facet groups:]
    |                   |
    |                   +---> index.html → Pattern Summaries
    |                   |                  |
    |                   |                  | [Top patterns with:]
    |                   |                  | - Pattern text
    |                   |                  | - Count badge
    |                   |                  | - Percentage
    |                   |                  | - Sample message
    |                   |                  | - Most common level
    |                   |                  |
    |                   |                  v
    |                   |            [Clickable to refine search]
    |                   |
    |                   +---> index.html → Regular Facets
    |                                     |
    |                                     | - Log Level (with color badges)
    |                                     | - Exception Type
    |                                     | - Component (logger)
    |                                     | - User
    |                                     | - Source File
    |                                     |
    |                                     v
    |                               [Clickable to filter results]
    |
    +---> index.html → displayCharts(agg)
                       |
                       | [Renders Chart.js visualizations:]
                       |
                       +---> Timeline chart (timelineHourly)
                       +---> Log Levels pie chart (levelFacets)
                       +---> Top Exceptions bar chart (exceptionFacets)
                       +---> Timeline by Level stacked chart (timelineByLevel)
                       |
                       v
                 [Charts display in analytics section]
                       |
                       v
                 [User sees facets, patterns, and charts]
                 [Can click facets to refine search]
```

---

## Key Components

### Frontend (index.html)

**Main Search Flow:**
- `async function search(page)` - Main search handler
- `fetch('/api/search?...')` - Search API call
- `displayResults(result)` - Renders search results
- `fetchAggregations()` - Triggers async aggregations (non-blocking)

**Aggregations Flow:**
- `fetch('/api/aggregations?...')` - Aggregations API call
- `displayAggregations(agg)` - Main aggregation renderer
- `displayStats(agg)` - Renders stats grid (Total Logs, Errors, Warnings)
- `displayFacets(agg)` - Renders faceted filters including:
  - Pattern summaries (fingerprinting)
  - Regular facets (Level, Exception, Component, User, File)
- `displayCharts(agg)` - Renders Chart.js visualizations

### REST Layer (LogSearchController.java)
- `@GetMapping("/api/search")` - Main search endpoint
- `@GetMapping("/api/aggregations")` - Aggregations endpoint

### Service Layer (LogSearchService.java)

**Main Search:**
- `search(...)` - Main search method with routing logic
- `searchWithMetadata(...)` - Metadata-first search (chunking enabled)
- `searchStandard(...)` - Standard search (chunking disabled)
- `searchDayIndex(...)` - Individual day index search
- `documentToLogEntry(...)` - Document to LogEntry conversion
- `generateUniqueKey(...)` - Deduplication key generation

**Aggregations & Analytics:**
- `getAggregations(...)` - Main aggregations method
- Day index iteration and data collection
- Aggregation counters (levels, exceptions, loggers, users, files, patterns, timeline)
- `buildFacets(...)` - Converts counts to facets with percentages
- `buildPatternSummaries(...)` - Creates pattern fingerprinting summaries
- `detectPatterns(...)` - Pattern detection (spikes, repeated errors, memory issues)
- `extractExceptionType(...)` - Extracts exception types from log messages
- `extractTopLevelClass(...)` - Extracts application class from stack traces

### Index Services (when chunking enabled)
- **MetadataIndexService** - Finds candidate chunks using Bloom filters
- **LuceneIndexService** - Searches individual chunks

---

## Search Strategies

### Standard Search (Chunking Disabled)
1. Determine date-based indexes to search
2. Search each day index in parallel
3. Aggregate results from all indexes
4. Deduplicate entries using unique key (timestamp + lineNumber + message hash)
5. Apply pagination
6. Return results

### Metadata-First Search (Chunking Enabled)
1. **Stage 1**: Query metadata index to find candidate chunks
   - Extracts search terms from query
   - Uses Bloom filters for efficient pruning
   - Achieves 90-98% efficiency (filters out non-matching chunks)
2. **Stage 2**: Search only candidate chunks in parallel
   - Searches each chunk using Lucene
   - Executes in parallel thread pool
3. Deduplicate entries across chunks using unique key
4. Apply pagination
5. Return results

---

## Aggregations & Analytics Strategy

### Faceted Filters
1. **Level Facets**: Count by log level (ERROR, WARN, INFO, DEBUG, etc.)
2. **Exception Facets**: Extract and count exception types from messages
3. **Logger Facets**: Count by logger/component name
4. **User Facets**: Count by user identifier
5. **File Facets**: Count by source file path

### Pattern Fingerprinting
1. Groups log messages by pattern (from indexed pattern field)
2. Counts occurrences of each pattern
3. Calculates percentage of total logs
4. Stores sample message for each pattern
5. Tracks most common log level per pattern
6. Returns top 20 patterns by frequency

### Timeline Analysis
1. **Dynamic Intervals**:
   - <= 2 hours: 15-minute intervals
   - <= 7 days: hourly intervals
   - > 7 days: daily intervals
2. **Overall Timeline**: Count logs per interval
3. **Timeline by Level**: Count logs per interval, broken down by level

### Pattern Detection
1. **Spike Detection**: Identifies time periods with 3x average log rate
2. **Repeated Exceptions**: Finds exceptions occurring >= 5 times, grouped by class
3. **Memory Issues**: Detects OutOfMemory, heap, and GC overhead issues

---

## Performance Features

### Search Performance
- **Parallel Execution**: Thread pool searches multiple indexes/chunks concurrently
- **Deduplication**: Prevents duplicate entries from different sources (active + archived logs)
- **Pagination**: Returns only requested page of results
- **Query Preprocessing**: Handles special characters, spaces, and field queries
- **Time Range Filtering**: Filters by timestamp at Lucene index level
- **Metadata Optimization**: Bloom filter pruning reduces search space by 90-98%

### Aggregations Performance
- **Non-Blocking**: Runs asynchronously after search results display
- **Single Pass**: Aggregates all metrics in one iteration
- **Top-N Limiting**: Returns only top 10-20 items per facet
- **Pattern Deduplication**: Efficient pattern grouping and counting
- **Smart Extraction**: Extracts exceptions and classes from messages during aggregation

---

## Execution Timeline

Here's how the two flows execute in time:

```
Time   Main Search Flow                    Aggregations Flow
────   ─────────────────────────           ──────────────────
0ms    User clicks Search
       |
50ms   fetch('/api/search')
       |
100ms  LogSearchController.search()
       |
500ms  LogSearchService searches indexes
       |
800ms  Returns SearchResult
       |
850ms  displayResults() renders entries
       |                                    fetchAggregations() starts [ASYNC]
900ms  ✓ User sees log entries             |
       |                                    fetch('/api/aggregations')
       |                                    |
       |                                    LogSearchController.getAggregations()
       |                                    |
       |                                    LogSearchService.getAggregations()
       |                                    |
       |                                    Iterates indexes, collects:
       |                                    - Level counts
       |                                    - Exception counts
       |                                    - Pattern fingerprints
       |                                    - Timeline data
       |                                    |
2000ms (User scrolling through results)    Builds facets with percentages
       |                                    |
       |                                    Detects patterns (spikes, errors)
       |                                    |
2500ms |                                    Returns AggregationResult
       |                                    |
       |                                    displayAggregations()
       |                                    - displayStats()
       |                                    - displayFacets()
       |                                    - displayCharts()
       |                                    |
3000ms |                                    ✓ User sees facets, patterns, charts
       |                                    |
       v                                    v
    [Both flows complete - full UI rendered]
```

**Benefits of Async Design:**
- User sees search results in ~900ms (fast feedback)
- Analytics populate without blocking the UI
- User can start reading logs immediately
- No "loading" state for analytics - they appear when ready
- Clicking pagination (page > 0) doesn't re-run aggregations
