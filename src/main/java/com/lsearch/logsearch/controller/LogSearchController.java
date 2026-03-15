package com.lsearch.logsearch.controller;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.AggregationResult;
import com.lsearch.logsearch.model.SearchResult;
import com.lsearch.logsearch.service.LogFileIndexer;
import com.lsearch.logsearch.service.LogSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LogSearchController {

    private static final Logger log = LoggerFactory.getLogger(LogSearchController.class);

    private final LogSearchService searchService;
    private final LogFileIndexer fileIndexer;
    private final LogSearchProperties properties;

    public LogSearchController(LogSearchService searchService, LogFileIndexer fileIndexer, LogSearchProperties properties) {
        this.searchService = searchService;
        this.fileIndexer = fileIndexer;
        this.properties = properties;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResult> search(
            @RequestParam(required = false) String query,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(required = false) List<String> indexes,
            @RequestParam(required = false) List<String> environments) {

        try {
            log.info("Search request - query: '{}', startTime: {}, endTime: {}, page: {}, pageSize: {}, indexes: {}, environments: {}",
                    query, startTime, endTime, page, pageSize, indexes, environments);

            SearchResult result = searchService.search(query, startTime, endTime, page, pageSize, indexes, environments);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Search failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> triggerIndexing(
            @RequestParam(defaultValue = "false") boolean fullReindex) {
        try {
            if (fullReindex) {
                log.info("Full re-indexing triggered via API (deleting existing indexes)");
                fileIndexer.fullReindex();
            } else {
                log.info("Incremental indexing triggered via API");
                fileIndexer.indexAllLogs();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", fullReindex ? "Full re-indexing completed" : "Indexing completed");
            response.put("indexedFiles", fileIndexer.getIndexedFileCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Indexing failed", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Indexing failed: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/aggregations")
    public ResponseEntity<AggregationResult> getAggregations(
            @RequestParam(required = false) String query,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endTime) {

        try {
            log.info("Aggregation request - query: '{}', startTime: {}, endTime: {}",
                    query, startTime, endTime);

            AggregationResult result = searchService.getAggregations(query, startTime, endTime);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Aggregation failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "running");
        status.put("indexedFiles", fileIndexer.getIndexedFileCount());

        return ResponseEntity.ok(status);
    }

    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> getContext(
            @RequestParam String sourceFile,
            @RequestParam long lineNumber,
            @RequestParam(defaultValue = "10") int contextLines) {

        try {
            // Check if sourceFile is already an absolute path
            Path logFilePath = Paths.get(sourceFile);
            if (!logFilePath.isAbsolute()) {
                // If relative, combine with logs directory
                logFilePath = Paths.get(properties.getLogsDir(), sourceFile);
            }

            // Normalize path to resolve any ./ or ../ components
            logFilePath = logFilePath.normalize();

            if (!Files.exists(logFilePath)) {
                log.warn("Log file not found: {}", logFilePath);
                return ResponseEntity.notFound().build();
            }

            // Read all lines
            List<String> allLines;
            try (Stream<String> lines = Files.lines(logFilePath)) {
                allLines = lines.collect(Collectors.toList());
            }

            // Calculate range
            int totalLines = allLines.size();
            long targetLine = lineNumber - 1; // Convert to 0-based index
            int startLine = Math.max(0, (int)(targetLine - contextLines));
            int endLine = Math.min(totalLines, (int)(targetLine + contextLines + 1));

            // Extract context
            List<Map<String, Object>> contextEntries = new ArrayList<>();
            for (int i = startLine; i < endLine; i++) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("lineNumber", i + 1);
                entry.put("content", allLines.get(i));
                entry.put("isTarget", i == targetLine);
                contextEntries.add(entry);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("sourceFile", sourceFile);
            response.put("targetLine", lineNumber);
            response.put("contextLines", contextEntries);
            response.put("totalLines", totalLines);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error reading context from file: {}", sourceFile, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
