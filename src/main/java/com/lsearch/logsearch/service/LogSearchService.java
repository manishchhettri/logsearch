package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import com.lsearch.logsearch.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class LogSearchService {

    private static final Logger log = LoggerFactory.getLogger(LogSearchService.class);

    private final LogSearchProperties properties;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();

    public LogSearchService(LogSearchProperties properties) {
        this.properties = properties;
    }

    public SearchResult search(String queryText, ZonedDateTime startTime, ZonedDateTime endTime,
                               int page, int pageSize) throws Exception {
        long startMs = System.currentTimeMillis();

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

        List<LogEntry> allResults = new ArrayList<>();
        long totalHits = 0;

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

                // Text search query (if provided) - search across all text fields
                if (queryText != null && !queryText.trim().isEmpty()) {
                    String[] fields = {"message", "user", "level", "thread", "logger"};
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

                // Execute search (get more than needed for pagination across indexes)
                TopDocs topDocs = searcher.search(finalQuery, 10000);
                totalHits += topDocs.totalHits.value;

                // Collect results
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    allResults.add(documentToLogEntry(doc));
                }

            } catch (Exception e) {
                log.error("Error searching index for date: {}", date, e);
            }
        }

        // Sort by timestamp descending (most recent first)
        allResults.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        // Apply pagination
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allResults.size());

        List<LogEntry> paginatedResults = startIndex < allResults.size()
                ? allResults.subList(startIndex, endIndex)
                : new ArrayList<>();

        long searchTimeMs = System.currentTimeMillis() - startMs;

        log.info("Search completed in {}ms, found {} total hits, returning {} results",
                searchTimeMs, totalHits, paginatedResults.size());

        return SearchResult.builder()
                .entries(paginatedResults)
                .totalHits(totalHits)
                .page(page)
                .pageSize(pageSize)
                .searchTimeMs(searchTimeMs)
                .build();
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
}
