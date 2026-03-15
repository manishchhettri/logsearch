package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.AggregationResult;
import com.lsearch.logsearch.model.ChunkCandidate;
import com.lsearch.logsearch.model.ChunkQuery;
import com.lsearch.logsearch.model.Facet;
import com.lsearch.logsearch.model.LogEntry;
import com.lsearch.logsearch.model.PatternSummary;
import com.lsearch.logsearch.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LogSearchService {

    private static final Logger log = LoggerFactory.getLogger(LogSearchService.class);

    private final LogSearchProperties properties;
    private final CodeAnalyzer analyzer = new CodeAnalyzer();
    private final ExecutorService searchExecutor;
    private final MetadataIndexService metadataIndexService;
    private final LuceneIndexService luceneIndexService;

    public LogSearchService(LogSearchProperties properties,
                             MetadataIndexService metadataIndexService,
                             LuceneIndexService luceneIndexService) {
        this.properties = properties;
        this.metadataIndexService = metadataIndexService;
        this.luceneIndexService = luceneIndexService;
        // Create thread pool for parallel searching
        // Use available processors for optimal performance
        int threadCount = Math.max(4, Runtime.getRuntime().availableProcessors());
        this.searchExecutor = Executors.newFixedThreadPool(threadCount,
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("log-search-" + counter++);
                        t.setDaemon(true);
                        return t;
                    }
                });
        log.info("Initialized parallel search with {} threads", threadCount);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down search executor");
        searchExecutor.shutdown();
        try {
            if (!searchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                searchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            searchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public SearchResult search(String queryText, ZonedDateTime startTime, ZonedDateTime endTime,
                               int page, int pageSize, List<String> indexes, List<String> environments) throws Exception {
        long startMs = System.currentTimeMillis();

        boolean chunkingEnabled = properties.getChunking() != null && properties.getChunking().isEnabled();

        if (chunkingEnabled) {
            log.info("Using METADATA-FIRST search (chunking enabled)");
            return searchWithMetadata(queryText, startTime, endTime, page, pageSize, indexes, environments, startMs);
        } else {
            log.info("Using STANDARD search (chunking disabled)");
            return searchStandard(queryText, startTime, endTime, page, pageSize, indexes, environments, startMs);
        }
    }

    /**
     * Standard search (without metadata-first optimization)
     */
    private SearchResult searchStandard(String queryText, ZonedDateTime startTime, ZonedDateTime endTime,
                               int page, int pageSize, List<String> indexes, List<String> environments, long startMs) throws Exception {
        // Determine which day-based indexes to search
        List<String> datesToSearch = getDateRangeDirs(startTime, endTime);

        if (datesToSearch.isEmpty()) {
            log.info("No indexes found for date range: {} to {}", startTime, endTime);
            return SearchResult.builder()
                    .entries(new ArrayList<>())
                    .totalHits(0)
                    .page(page)
                    .pageSize(pageSize)
                    .searchTimeMs(System.currentTimeMillis() - startMs)
                    .build();
        }

        log.info("Searching {} day-based indexes from {} to {}", datesToSearch.size(),
                datesToSearch.get(0), datesToSearch.get(datesToSearch.size() - 1));

        // Parallel search across day indexes
        List<Future<DaySearchResult>> futures = new ArrayList<>();

        for (String date : datesToSearch) {
            futures.add(searchExecutor.submit(() -> searchDayIndex(date, queryText, startTime, endTime, indexes, environments)));
        }

        // Collect results from all futures with deduplication
        // Use LinkedHashMap to preserve insertion order while deduplicating
        Map<String, LogEntry> uniqueResults = new LinkedHashMap<>();

        for (Future<DaySearchResult> future : futures) {
            try {
                DaySearchResult result = future.get(30, TimeUnit.SECONDS);
                // Deduplicate by adding to map with unique key
                for (LogEntry entry : result.entries) {
                    String uniqueKey = generateUniqueKey(entry);
                    uniqueResults.putIfAbsent(uniqueKey, entry);
                }
            } catch (TimeoutException e) {
                log.error("Search timed out for a day index", e);
            } catch (ExecutionException e) {
                log.error("Error searching day index", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Search interrupted", e);
            }
        }

        // Convert to list and sort by timestamp descending (most recent first)
        List<LogEntry> allResults = new ArrayList<>(uniqueResults.values());
        allResults.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        // Total hits is now the count of unique results after deduplication
        long totalHits = allResults.size();

        // Apply pagination
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allResults.size());

        List<LogEntry> paginatedResults = startIndex < allResults.size()
                ? allResults.subList(startIndex, endIndex)
                : new ArrayList<>();

        long searchTimeMs = System.currentTimeMillis() - startMs;

        log.info("Search completed in {}ms, found {} unique hits (deduplicated), returning {} results",
                searchTimeMs, totalHits, paginatedResults.size());

        return SearchResult.builder()
                .entries(paginatedResults)
                .totalHits(totalHits)
                .page(page)
                .pageSize(pageSize)
                .searchTimeMs(searchTimeMs)
                .build();
    }

    /**
     * Metadata-first search (with chunk pruning for 90-98% efficiency)
     */
    private SearchResult searchWithMetadata(String queryText, ZonedDateTime startTime, ZonedDateTime endTime,
                               int page, int pageSize, List<String> indexes, List<String> environments, long startMs) throws Exception {
        // Stage 1: Build ChunkQuery and find candidate chunks
        ChunkQuery.Builder queryBuilder = new ChunkQuery.Builder();

        if (startTime != null && endTime != null) {
            queryBuilder.timeRange(
                startTime.toInstant().toEpochMilli(),
                endTime.toInstant().toEpochMilli()
            );
        }

        if (queryText != null && !queryText.trim().isEmpty()) {
            // Extract search terms for Bloom filter pruning
            // Parse field queries (field:value) and extract only values for Bloom filter matching
            Set<String> terms = new HashSet<>();

            // Define valid field names
            Set<String> validMetadataFields = new HashSet<>(Arrays.asList(
                "level", "user", "thread", "logger", "sourceFile",
                "correlationId", "messageId", "flowName", "endpoint"
            ));
            Set<String> validContentFields = new HashSet<>(Arrays.asList("message"));

            // Split on AND/OR operators first
            String[] clauses = queryText.split("\\s+(?:AND|OR)\\s+");

            for (String clause : clauses) {
                clause = clause.trim();

                // Check if this might be a field query (field:value)
                boolean isValidFieldQuery = false;
                String fieldName = null;
                String fieldValue = null;

                if (clause.contains(":")) {
                    int colonIndex = clause.indexOf(":");
                    String potentialFieldName = clause.substring(0, colonIndex).trim();
                    String potentialFieldValue = clause.substring(colonIndex + 1).trim();

                    // Only treat as field query if field name is valid
                    if (validMetadataFields.contains(potentialFieldName) ||
                        validContentFields.contains(potentialFieldName)) {
                        isValidFieldQuery = true;
                        fieldName = potentialFieldName;
                        fieldValue = potentialFieldValue;
                    }
                }

                if (isValidFieldQuery && fieldValue != null && !fieldValue.isEmpty()) {
                    // This is a valid field query
                    // Remove quotes if present
                    fieldValue = fieldValue.replaceAll("^\"|\"$", "");

                    // For metadata fields, skip adding to Bloom filter
                    // This lets all chunks through to Stage 2 for precise metadata filtering
                    if (validMetadataFields.contains(fieldName)) {
                        continue;
                    }

                    // For content fields (message), tokenize the value
                    if (validContentFields.contains(fieldName)) {
                        String[] valueTokens = fieldValue.split("[\\s\\.,;:()\\[\\]{}\"'<>=]+");
                        for (String token : valueTokens) {
                            if (token != null && !token.isEmpty()) {
                                terms.add(token.toLowerCase());
                            }
                        }
                    }
                } else {
                    // Not a valid field query - treat entire clause as regular text search
                    // Include colon in delimiters since it's not part of a field query
                    String[] tokens = clause.split("[\\s\\.,;:()\\[\\]{}\"'<>=]+");
                    for (String token : tokens) {
                        if (token != null && !token.isEmpty()) {
                            terms.add(token.toLowerCase());
                        }
                    }
                }
            }

            // If no terms were extracted (e.g., pure metadata field query),
            // don't add any terms - let all chunks through to Stage 2
            if (!terms.isEmpty()) {
                queryBuilder.searchTerms(terms);
            }
        }

        // TODO: Add service, logLevel filters based on indexes/environments if needed

        ChunkQuery chunkQuery = queryBuilder.build();

        long metadataStartMs = System.currentTimeMillis();
        List<ChunkCandidate> candidates = metadataIndexService.findCandidateChunks(chunkQuery);
        long metadataMs = System.currentTimeMillis() - metadataStartMs;

        log.info("Metadata index returned {} candidate chunks in {}ms (Stage 1 complete)",
                candidates.size(), metadataMs);

        if (candidates.isEmpty()) {
            log.info("No candidate chunks found - returning empty results");
            return SearchResult.builder()
                    .entries(new ArrayList<>())
                    .totalHits(0)
                    .page(page)
                    .pageSize(pageSize)
                    .searchTimeMs(System.currentTimeMillis() - startMs)
                    .build();
        }

        // Stage 2: Search only candidate chunks in parallel
        Map<String, LogEntry> uniqueResults = new LinkedHashMap<>();
        List<Future<List<LogEntry>>> futures = new ArrayList<>();

        for (ChunkCandidate candidate : candidates) {
            futures.add(searchExecutor.submit(() -> {
                try {
                    return luceneIndexService.searchChunk(candidate.getChunkId(), queryText, 10000);
                } catch (Exception e) {
                    log.error("Error searching chunk {}: {}", candidate.getChunkId(), e.getMessage());
                    return new ArrayList<>();
                }
            }));
        }

        // Collect results from chunk searches with deduplication
        for (Future<List<LogEntry>> future : futures) {
            try {
                List<LogEntry> chunkResults = future.get(30, TimeUnit.SECONDS);
                // Deduplicate by adding to map with unique key
                for (LogEntry entry : chunkResults) {
                    String uniqueKey = generateUniqueKey(entry);
                    uniqueResults.putIfAbsent(uniqueKey, entry);
                }
            } catch (TimeoutException e) {
                log.error("Chunk search timed out", e);
            } catch (ExecutionException e) {
                log.error("Error in chunk search", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Search interrupted", e);
            }
        }

        // Convert to list and sort by timestamp descending
        List<LogEntry> allResults = new ArrayList<>(uniqueResults.values());
        allResults.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        // Total hits is now the count of unique results after deduplication
        long totalHits = allResults.size();

        // Apply pagination
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allResults.size());

        List<LogEntry> paginatedResults = startIndex < allResults.size()
                ? allResults.subList(startIndex, endIndex)
                : new ArrayList<>();

        long searchTimeMs = System.currentTimeMillis() - startMs;

        log.info("Metadata-first search completed in {}ms (metadata: {}ms, chunks: {}ms), " +
                 "found {} unique hits (deduplicated) from {} chunks, returning {} results",
                searchTimeMs, metadataMs, searchTimeMs - metadataMs,
                totalHits, candidates.size(), paginatedResults.size());

        return SearchResult.builder()
                .entries(paginatedResults)
                .totalHits(totalHits)
                .page(page)
                .pageSize(pageSize)
                .searchTimeMs(searchTimeMs)
                .build();
    }

    /**
     * Inner class to hold search results from a single day index
     */
    private static class DaySearchResult {
        final List<LogEntry> entries;
        final long totalHits;

        DaySearchResult(List<LogEntry> entries, long totalHits) {
            this.entries = entries;
            this.totalHits = totalHits;
        }
    }

    /**
     * Search a single day index (thread-safe, called in parallel)
     */
    private DaySearchResult searchDayIndex(String date, String queryText,
                                           ZonedDateTime startTime, ZonedDateTime endTime,
                                           List<String> indexes, List<String> environments) {
        Path indexPath = Paths.get(properties.getIndexDir(), date);
        if (!Files.exists(indexPath)) {
            return new DaySearchResult(new ArrayList<>(), 0);
        }

        List<LogEntry> entries = new ArrayList<>();
        long totalHits = 0;

        try (Directory directory = FSDirectory.open(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);

            // Build query
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

            // Text search query (if provided) - search across all text fields
            if (queryText != null && !queryText.trim().isEmpty()) {
                String[] fields = {"message", "user", "level", "thread", "logger", "sourceFile", "patternText",
                                   "correlationIdText", "messageIdText", "flowNameText", "endpointText"};
                MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);
                Query textQuery = parser.parse(queryText);
                queryBuilder.add(textQuery, BooleanClause.Occur.MUST);
            }

            // Time range query
            long startEpoch = startTime.toInstant().toEpochMilli();
            long endEpoch = endTime.toInstant().toEpochMilli();
            Query timeRangeQuery = LongPoint.newRangeQuery("timestamp", startEpoch, endEpoch);
            queryBuilder.add(timeRangeQuery, BooleanClause.Occur.MUST);

            // Index filter (if specified)
            if (indexes != null && !indexes.isEmpty()) {
                BooleanQuery.Builder indexFilter = new BooleanQuery.Builder();
                for (String indexName : indexes) {
                    indexFilter.add(new TermQuery(new org.apache.lucene.index.Term("indexName", indexName)),
                            BooleanClause.Occur.SHOULD);
                }
                queryBuilder.add(indexFilter.build(), BooleanClause.Occur.MUST);
            }

            // Environment filter (if specified)
            if (environments != null && !environments.isEmpty()) {
                BooleanQuery.Builder envFilter = new BooleanQuery.Builder();
                for (String env : environments) {
                    envFilter.add(new TermQuery(new org.apache.lucene.index.Term("environment", env)),
                            BooleanClause.Occur.SHOULD);
                }
                queryBuilder.add(envFilter.build(), BooleanClause.Occur.MUST);
            }

            Query finalQuery = queryBuilder.build();

            // Execute search (get more than needed for pagination across indexes)
            TopDocs topDocs = searcher.search(finalQuery, 10000);
            totalHits = topDocs.totalHits.value;

            // Collect results
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                entries.add(documentToLogEntry(doc));
            }

        } catch (Exception e) {
            log.error("Error searching index for date: {}", date, e);
        }

        return new DaySearchResult(entries, totalHits);
    }

    private List<String> getDateRangeDirs(ZonedDateTime startTime, ZonedDateTime endTime) throws IOException {
        LocalDate startDate = startTime.toLocalDate();
        LocalDate endDate = endTime.toLocalDate();

        List<String> dates = new ArrayList<>();
        Path indexDir = Paths.get(properties.getIndexDir());

        if (!Files.exists(indexDir)) {
            return dates;
        }

        // Get all date directories that fall within range
        try (Stream<Path> paths = Files.list(indexDir)) {
            paths.filter(Files::isDirectory)
                    .forEach(path -> {
                        try {
                            String dirName = path.getFileName().toString();
                            LocalDate indexDate = LocalDate.parse(dirName, DateTimeFormatter.ISO_DATE);

                            if (!indexDate.isBefore(startDate) && !indexDate.isAfter(endDate)) {
                                dates.add(dirName);
                            }
                        } catch (Exception e) {
                            log.debug("Skipping non-date directory: {}", path.getFileName());
                        }
                    });
        }

        dates.sort(String::compareTo);
        return dates;
    }

    private LogEntry documentToLogEntry(Document doc) {
        long timestampMs = doc.getField("timestamp").numericValue().longValue();
        ZonedDateTime timestamp = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestampMs),
                ZoneId.of(properties.getTimezone())
        );

        return LogEntry.builder()
                .timestamp(timestamp)
                .level(doc.get("level"))
                .thread(doc.get("thread"))
                .logger(doc.get("logger"))
                .user(doc.get("user"))
                .message(doc.get("message"))
                .sourceFile(doc.get("sourceFile"))
                .lineNumber(doc.getField("lineNumber").numericValue().longValue())
                .build();
    }

    /**
     * Get aggregations/analytics for search results
     */
    public AggregationResult getAggregations(String queryText, ZonedDateTime startTime, ZonedDateTime endTime) throws Exception {
        long startMs = System.currentTimeMillis();

        List<String> datesToSearch = getDateRangeDirs(startTime, endTime);

        if (datesToSearch.isEmpty()) {
            return AggregationResult.builder()
                    .totalHits(0)
                    .levelFacets(new ArrayList<>())
                    .exceptionFacets(new ArrayList<>())
                    .loggerFacets(new ArrayList<>())
                    .userFacets(new ArrayList<>())
                    .fileFacets(new ArrayList<>())
                    .timelineHourly(new LinkedHashMap<>())
                    .timelineByLevel(new LinkedHashMap<>())
                    .detectedPatterns(new ArrayList<>())
                    .build();
        }

        // Determine interval based on time range
        long rangeHours = Duration.between(startTime, endTime).toHours();
        ChronoUnit intervalUnit;
        int intervalAmount;
        if (rangeHours <= 2) {
            intervalUnit = ChronoUnit.MINUTES;
            intervalAmount = 15; // 15-minute intervals
        } else if (rangeHours <= 168) { // 7 days
            intervalUnit = ChronoUnit.HOURS;
            intervalAmount = 1; // hourly
        } else {
            intervalUnit = ChronoUnit.DAYS;
            intervalAmount = 1; // daily
        }

        // Maps to collect aggregation data
        Map<String, Long> levelCounts = new HashMap<>();
        Map<String, Long> exceptionCounts = new HashMap<>();
        Map<String, Long> loggerCounts = new HashMap<>();
        Map<String, Long> userCounts = new HashMap<>();
        Map<String, Long> fileCounts = new HashMap<>();
        Map<String, Long> timelineHourly = new LinkedHashMap<>();
        Map<String, Map<String, Long>> timelineByLevel = new LinkedHashMap<>();

        // Pattern fingerprinting data
        Map<String, PatternData> patternCounts = new HashMap<>();

        long totalHits = 0;
        List<LogEntry> allEntries = new ArrayList<>();

        // Search each day index
        for (String date : datesToSearch) {
            Path indexPath = Paths.get(properties.getIndexDir(), date);
            if (!Files.exists(indexPath)) {
                continue;
            }

            try (Directory directory = FSDirectory.open(indexPath);
                 IndexReader reader = DirectoryReader.open(directory)) {

                IndexSearcher searcher = new IndexSearcher(reader);

                // Build query
                BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

                // Text search query (if provided)
                if (queryText != null && !queryText.trim().isEmpty()) {
                    String[] fields = {"message", "user", "level", "thread", "logger", "sourceFile", "patternText",
                                       "correlationIdText", "messageIdText", "flowNameText", "endpointText"};
                    MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);
                    Query textQuery = parser.parse(queryText);
                    queryBuilder.add(textQuery, BooleanClause.Occur.MUST);
                }

                // Time range query
                long startEpoch = startTime.toInstant().toEpochMilli();
                long endEpoch = endTime.toInstant().toEpochMilli();
                Query timeRangeQuery = LongPoint.newRangeQuery("timestamp", startEpoch, endEpoch);
                queryBuilder.add(timeRangeQuery, BooleanClause.Occur.MUST);

                Query finalQuery = queryBuilder.build();

                // Execute search - get all results for aggregation
                TopDocs topDocs = searcher.search(finalQuery, 10000);
                totalHits += topDocs.totalHits.value;

                // Collect and aggregate results
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    LogEntry entry = documentToLogEntry(doc);
                    allEntries.add(entry);

                    // Aggregate by level
                    String level = doc.get("level");
                    if (level != null && !level.isEmpty()) {
                        levelCounts.merge(level, 1L, Long::sum);
                    }

                    // Aggregate by exception type (extract from message)
                    String message = doc.get("message");
                    if (message != null) {
                        String exception = extractExceptionType(message);
                        if (exception != null) {
                            exceptionCounts.merge(exception, 1L, Long::sum);
                        }
                    }

                    // Aggregate by logger
                    String logger = doc.get("logger");
                    if (logger != null && !logger.isEmpty()) {
                        loggerCounts.merge(logger, 1L, Long::sum);
                    }

                    // Aggregate by user
                    String user = doc.get("user");
                    if (user != null && !user.isEmpty()) {
                        userCounts.merge(user, 1L, Long::sum);
                    }

                    // Aggregate by source file
                    String sourceFile = doc.get("sourceFile");
                    if (sourceFile != null && !sourceFile.isEmpty()) {
                        fileCounts.merge(sourceFile, 1L, Long::sum);
                    }

                    // Aggregate by pattern (fingerprinting)
                    String pattern = doc.get("pattern");
                    if (pattern != null && !pattern.isEmpty()) {
                        PatternData patternData = patternCounts.computeIfAbsent(pattern,
                            k -> new PatternData(pattern));
                        patternData.incrementCount();

                        // Store sample message if not set
                        if (patternData.getSampleMessage() == null) {
                            patternData.setSampleMessage(message);
                        }

                        // Track most common level for this pattern
                        if (level != null) {
                            patternData.addLevel(level);
                        }
                    }

                    // Build timeline with dynamic intervals
                    ZonedDateTime timestamp = entry.getTimestamp();
                    String intervalKey = getIntervalKey(timestamp, intervalUnit, intervalAmount);
                    timelineHourly.merge(intervalKey, 1L, Long::sum);

                    // Build timeline by level (reuse level variable from above)
                    if (level != null && !level.isEmpty()) {
                        timelineByLevel.computeIfAbsent(intervalKey, k -> new HashMap<>())
                                .merge(level, 1L, Long::sum);
                    }
                }

            } catch (Exception e) {
                log.error("Error aggregating index for date: {}", date, e);
            }
        }

        log.info("Aggregation completed in {}ms, processed {} hits",
                System.currentTimeMillis() - startMs, totalHits);

        // Convert to facets with percentages and sort by count
        List<Facet> levelFacets = buildFacets(levelCounts, totalHits, 10);
        List<Facet> exceptionFacets = buildFacets(exceptionCounts, totalHits, 10);
        List<Facet> loggerFacets = buildFacets(loggerCounts, totalHits, 10);
        List<Facet> userFacets = buildFacets(userCounts, totalHits, 10);
        List<Facet> fileFacets = buildFacets(fileCounts, totalHits, 10);

        // Build pattern summaries (fingerprinting)
        List<PatternSummary> patternSummaries = buildPatternSummaries(patternCounts, totalHits, 20);

        // Detect patterns
        List<String> patterns = detectPatterns(allEntries, timelineHourly);

        return AggregationResult.builder()
                .totalHits(totalHits)
                .levelFacets(levelFacets)
                .exceptionFacets(exceptionFacets)
                .loggerFacets(loggerFacets)
                .userFacets(userFacets)
                .fileFacets(fileFacets)
                .timelineHourly(timelineHourly)
                .timelineByLevel(timelineByLevel)
                .detectedPatterns(patterns)
                .patternSummaries(patternSummaries)
                .build();
    }

    private String getIntervalKey(ZonedDateTime timestamp, ChronoUnit intervalUnit, int intervalAmount) {
        ZonedDateTime truncated;
        if (intervalUnit == ChronoUnit.MINUTES) {
            // Round down to nearest interval
            int minute = timestamp.getMinute();
            int roundedMinute = (minute / intervalAmount) * intervalAmount;
            truncated = timestamp.withMinute(roundedMinute).withSecond(0).withNano(0);
        } else if (intervalUnit == ChronoUnit.HOURS) {
            truncated = timestamp.withMinute(0).withSecond(0).withNano(0);
        } else { // DAYS
            truncated = timestamp.withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        return truncated.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Build pattern summaries from pattern data (fingerprinting)
     */
    private List<PatternSummary> buildPatternSummaries(Map<String, PatternData> patternData, long total, int topN) {
        return patternData.values().stream()
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(topN)
                .map(data -> {
                    double percentage = total > 0 ? (data.getCount() * 100.0 / total) : 0;
                    return PatternSummary.builder()
                            .pattern(data.getPattern())
                            .count(data.getCount())
                            .percentage(percentage)
                            .sampleMessage(data.getSampleMessage())
                            .level(data.getMostCommonLevel())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper class to track pattern data during aggregation
     */
    private static class PatternData {
        private final String pattern;
        private long count = 0;
        private String sampleMessage;
        private final Map<String, Integer> levelCounts = new HashMap<>();

        public PatternData(String pattern) {
            this.pattern = pattern;
        }

        public void incrementCount() {
            this.count++;
        }

        public void addLevel(String level) {
            levelCounts.merge(level, 1, Integer::sum);
        }

        public String getPattern() {
            return pattern;
        }

        public long getCount() {
            return count;
        }

        public String getSampleMessage() {
            return sampleMessage;
        }

        public void setSampleMessage(String sampleMessage) {
            this.sampleMessage = sampleMessage;
        }

        public String getMostCommonLevel() {
            return levelCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }

    private List<Facet> buildFacets(Map<String, Long> counts, long total, int topN) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> Facet.builder()
                        .value(entry.getKey())
                        .count(entry.getValue())
                        .percentage(total > 0 ? (entry.getValue() * 100.0 / total) : 0.0)
                        .build())
                .collect(Collectors.toList());
    }

    private String extractExceptionType(String message) {
        // Common Java exception patterns
        String[] exceptionPatterns = {
            "Exception", "Error", "Throwable"
        };

        for (String pattern : exceptionPatterns) {
            int idx = message.indexOf(pattern);
            if (idx > 0) {
                // Look backward for the start of the class name
                int start = idx;
                while (start > 0 && (Character.isJavaIdentifierPart(message.charAt(start - 1))
                        || message.charAt(start - 1) == '.')) {
                    start--;
                }

                // Extract the full exception class name
                int end = idx + pattern.length();
                String exceptionName = message.substring(start, end).trim();

                // Return just the simple class name (last part after final dot)
                int lastDot = exceptionName.lastIndexOf('.');
                return lastDot >= 0 ? exceptionName.substring(lastDot + 1) : exceptionName;
            }
        }

        return null;
    }

    private List<String> detectPatterns(List<LogEntry> entries, Map<String, Long> timeline) {
        List<String> patterns = new ArrayList<>();

        if (entries.isEmpty()) {
            return patterns;
        }

        // Sort entries by timestamp
        List<LogEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(LogEntry::getTimestamp));

        // Detect spikes in timeline
        if (timeline.size() > 1) {
            List<Long> counts = new ArrayList<>(timeline.values());
            double avg = counts.stream().mapToLong(Long::longValue).average().orElse(0);
            double threshold = avg * 3; // 3x average is a spike

            timeline.forEach((time, count) -> {
                if (count > threshold) {
                    patterns.add("Spike detected at " + time.substring(11, 16) +
                            " (" + count + " logs, " + String.format("%.1fx", count / avg) + " average)");
                }
            });
        }

        // Detect repeated errors with class information
        // Map: ExceptionType -> (ClassName -> count)
        Map<String, Map<String, Long>> exceptionByClass = new HashMap<>();

        for (LogEntry entry : entries) {
            String exception = extractExceptionType(entry.getMessage());
            if (exception != null) {
                String className = extractTopLevelClass(entry.getMessage());
                if (className != null) {
                    exceptionByClass.computeIfAbsent(exception, k -> new HashMap<>())
                            .merge(className, 1L, Long::sum);
                }
            }
        }

        // Analyze patterns and generate alerts with class information
        exceptionByClass.forEach((exception, classCounts) -> {
            long totalCount = classCounts.values().stream().mapToLong(Long::longValue).sum();

            if (totalCount >= 5) {
                // Get top 3 classes by count
                List<Map.Entry<String, Long>> topClasses = classCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(3)
                        .collect(Collectors.toList());

                // Build class summary
                StringBuilder classSummary = new StringBuilder();
                for (int i = 0; i < topClasses.size(); i++) {
                    if (i > 0) classSummary.append(", ");
                    classSummary.append(topClasses.get(i).getKey())
                            .append(" (").append(topClasses.get(i).getValue()).append(")");
                }

                patterns.add(exception + " in " + classSummary.toString());
            }
        });

        // Detect memory-related issues with class information
        Map<String, Long> memoryErrorsByClass = new HashMap<>();
        entries.stream()
                .filter(e -> e.getMessage().toLowerCase().contains("outofmemory") ||
                           e.getMessage().toLowerCase().contains("heap") ||
                           e.getMessage().toLowerCase().contains("gc overhead"))
                .forEach(e -> {
                    String className = extractTopLevelClass(e.getMessage());
                    if (className != null) {
                        memoryErrorsByClass.merge(className, 1L, Long::sum);
                    }
                });

        if (!memoryErrorsByClass.isEmpty()) {
            List<Map.Entry<String, Long>> topMemoryClasses = memoryErrorsByClass.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(2)
                    .collect(Collectors.toList());

            StringBuilder memorySummary = new StringBuilder("Memory issues in ");
            for (int i = 0; i < topMemoryClasses.size(); i++) {
                if (i > 0) memorySummary.append(", ");
                memorySummary.append(topMemoryClasses.get(i).getKey())
                        .append(" (").append(topMemoryClasses.get(i).getValue()).append(")");
            }
            patterns.add(memorySummary.toString());
        }

        // Limit patterns to most important ones
        return patterns.stream().limit(5).collect(Collectors.toList());
    }

    /**
     * Generate a unique key for a log entry to enable deduplication.
     * This prevents the same log entry from appearing multiple times when
     * the same file is indexed from different locations (e.g., active and archived logs).
     *
     * Key components:
     * - timestamp (epoch millis)
     * - lineNumber (from original file)
     * - message hash (first 100 chars to handle very long messages efficiently)
     *
     * Note: sourceFile is NOT included in the key to ensure that the same log entry
     * from different file copies (e.g., access.log and archived.log) is deduplicated.
     */
    private String generateUniqueKey(LogEntry entry) {
        long timestamp = entry.getTimestamp() != null
            ? entry.getTimestamp().toInstant().toEpochMilli()
            : 0L;

        long lineNumber = entry.getLineNumber();

        // Use first 100 chars of message for efficiency while maintaining uniqueness
        String messagePrefix = entry.getMessage() != null && entry.getMessage().length() > 100
            ? entry.getMessage().substring(0, 100)
            : (entry.getMessage() != null ? entry.getMessage() : "");

        return timestamp + "|" + lineNumber + "|" + messagePrefix.hashCode();
    }

    private String extractTopLevelClass(String message) {
        // Extract the first application class from the stack trace
        // Look for patterns like "at com.package.ClassName.method"
        String[] lines = message.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("at ")) {
                String classInfo = line.substring(3).trim();
                int parenIdx = classInfo.indexOf('(');
                if (parenIdx > 0) {
                    classInfo = classInfo.substring(0, parenIdx);
                }

                int methodIdx = classInfo.lastIndexOf('.');
                if (methodIdx > 0) {
                    String fullClassName = classInfo.substring(0, methodIdx);

                    // Skip Java/framework classes
                    if (!fullClassName.startsWith("java.") &&
                        !fullClassName.startsWith("javax.") &&
                        !fullClassName.startsWith("sun.") &&
                        !fullClassName.startsWith("org.springframework.") &&
                        !fullClassName.startsWith("org.apache.")) {

                        // Return just the simple class name
                        int lastDot = fullClassName.lastIndexOf('.');
                        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
                    }
                }
            }
        }

        return null;
    }
}
