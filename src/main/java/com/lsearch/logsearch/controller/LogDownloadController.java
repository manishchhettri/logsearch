package com.lsearch.logsearch.controller;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.DownloadLocation;
import com.lsearch.logsearch.service.LogDownloadService;
import com.lsearch.logsearch.service.LogFileIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LogDownloadController {

    private static final Logger log = LoggerFactory.getLogger(LogDownloadController.class);

    private final LogDownloadService downloadService;
    private final LogFileIndexer fileIndexer;
    private final LogSearchProperties properties;

    public LogDownloadController(LogDownloadService downloadService, LogFileIndexer fileIndexer,
                                 LogSearchProperties properties) {
        this.downloadService = downloadService;
        this.fileIndexer = fileIndexer;
        this.properties = properties;
    }

    /**
     * Get configured default download locations
     * GET /api/download-locations
     */
    @GetMapping("/download-locations")
    public ResponseEntity<List<DownloadLocation>> getDownloadLocations() {
        List<DownloadLocation> locations = properties.getDownloadLocations();

        if (locations == null || locations.isEmpty()) {
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }

        // Only return enabled locations
        List<DownloadLocation> enabledLocations = locations.stream()
                .filter(DownloadLocation::isEnabled)
                .collect(Collectors.toList());

        return ResponseEntity.ok(enabledLocations);
    }

    @PostMapping("/download-logs")
    public ResponseEntity<Map<String, Object>> downloadLogs(@RequestBody DownloadRequest request) {
        try {
            log.info("Download request received for {} URLs", request.getUrls().size());

            // Validate request
            if (request.getUrls() == null || request.getUrls().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("No URLs provided"));
            }

            // Find target path from download location configuration
            String targetPath = findTargetPathForUrl(request.getUrls().get(0));

            // Download and extract logs
            LogDownloadService.DownloadResult result;
            if (targetPath != null) {
                log.info("Using configured target path: {}", targetPath);
                result = downloadService.downloadLogs(
                        request.getUrls(),
                        request.getUsername(),
                        request.getPassword(),
                        targetPath
                );
            } else {
                result = downloadService.downloadLogs(
                        request.getUrls(),
                        request.getUsername(),
                        request.getPassword()
                );
            }

            // Trigger re-indexing if any files were downloaded
            if (result.getTotalFiles() > 0) {
                log.info("Triggering re-indexing after downloading {} files", result.getTotalFiles());
                fileIndexer.indexAllLogs();
            }

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", !result.hasErrors() || result.getTotalFiles() > 0);
            response.put("targetPath", targetPath != null ? targetPath : properties.getLogsDir());
            response.put("downloadedFiles", result.getDownloadedFiles());
            response.put("extractedFiles", result.getExtractedFiles());
            response.put("totalFiles", result.getTotalFiles());
            response.put("errors", result.getErrors());

            if (result.getTotalFiles() > 0) {
                String absolutePath = java.nio.file.Paths.get(
                    targetPath != null ? targetPath : properties.getLogsDir()
                ).toAbsolutePath().toString();
                response.put("message", "Downloaded " + result.getTotalFiles() +
                    " file(s) to: " + absolutePath);
            }

            if (result.hasErrors() && result.getTotalFiles() == 0) {
                return ResponseEntity.internalServerError().body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Download failed", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Download failed: " + e.getMessage()));
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    /**
     * Find the target path for a given URL from configured download locations
     */
    private String findTargetPathForUrl(String url) {
        List<DownloadLocation> locations = properties.getDownloadLocations();
        if (locations == null || locations.isEmpty()) {
            return null;
        }

        for (DownloadLocation location : locations) {
            if (location.getUrl() != null && url.startsWith(location.getUrl())) {
                return location.getTargetPath();
            }
        }

        return null;
    }

    public static class DownloadRequest {
        private List<String> urls;
        private String username;
        private String password;

        public List<String> getUrls() {
            return urls;
        }

        public void setUrls(List<String> urls) {
            this.urls = urls;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
