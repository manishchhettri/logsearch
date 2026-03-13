package com.lsearch.logsearch.controller;

import com.lsearch.logsearch.model.AggregationResult;
import com.lsearch.logsearch.model.SearchResult;
import com.lsearch.logsearch.service.LogFileIndexer;
import com.lsearch.logsearch.service.LogSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LogSearchController {

    private static final Logger log = LoggerFactory.getLogger(LogSearchController.class);

    private final LogSearchService searchService;
    private final LogFileIndexer fileIndexer;

    public LogSearchController(LogSearchService searchService, LogFileIndexer fileIndexer) {
        this.searchService = searchService;
        this.fileIndexer = fileIndexer;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResult> search(
            @RequestParam(required = false) String query,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {

        try {
            log.info("Search request - query: '{}', startTime: {}, endTime: {}, page: {}, pageSize: {}",
                    query, startTime, endTime, page, pageSize);

            SearchResult result = searchService.search(query, startTime, endTime, page, pageSize);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Search failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> triggerIndexing() {
        try {
            log.info("Manual indexing triggered via API");
            fileIndexer.indexAllLogs();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Indexing completed");
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
}
