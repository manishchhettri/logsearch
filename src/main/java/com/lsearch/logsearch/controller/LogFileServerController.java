package com.lsearch.logsearch.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Simulates a log file server (like WebLogic admin server, Apache, etc.)
 * Serves log files from the logs directory with directory listing support.
 *
 * Example URLs:
 * - http://localhost:8080/file-server/logs/
 * - http://localhost:8080/file-server/logs/server-20260313.log
 * - http://localhost:8080/file-server/logs/archive/
 */
@RestController
@RequestMapping("/file-server")
@CrossOrigin(origins = "*")
public class LogFileServerController {

    private static final Logger log = LoggerFactory.getLogger(LogFileServerController.class);

    @Value("${log-search.logs-dir:./logs}")
    private String logsDirectory;

    /**
     * Serve directory listing or file
     * GET /file-server/logs/
     * GET /file-server/logs/server-20260313.log
     * GET /file-server/logs/archive/
     */
    @GetMapping("/logs/**")
    public ResponseEntity<?> serveFile(HttpServletRequest request) {
        try {
            // Extract the requested path (everything after /file-server/logs/)
            String requestPath = extractRequestPath(request);

            Path basePath = Paths.get(logsDirectory).toAbsolutePath().normalize();
            Path requestedPath = basePath.resolve(requestPath).normalize();

            // Security check: ensure requested path is within logs directory
            if (!requestedPath.startsWith(basePath)) {
                return ResponseEntity.status(403).body("Access denied");
            }

            // Check if path exists
            if (!Files.exists(requestedPath)) {
                return ResponseEntity.notFound().build();
            }

            // If it's a directory, return directory listing
            if (Files.isDirectory(requestedPath)) {
                String html = generateDirectoryListing(requestedPath, requestPath);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html);
            }

            // If it's a file, serve the file
            Resource resource = new FileSystemResource(requestedPath);

            // Determine content type
            String contentType = Files.probeContentType(requestedPath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            log.info("Serving file: {}", requestedPath.getFileName());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + requestedPath.getFileName() + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("Error serving file", e);
            return ResponseEntity.internalServerError().body("Error serving file: " + e.getMessage());
        }
    }

    private String extractRequestPath(HttpServletRequest request) {
        // Extract path after /file-server/logs/
        String requestURI = request.getRequestURI();
        String prefix = "/file-server/logs/";

        if (requestURI.startsWith(prefix)) {
            String path = requestURI.substring(prefix.length());
            // Remove leading/trailing slashes and normalize
            return path.replaceAll("^/+", "").replaceAll("/+$", "");
        }

        return "";
    }

    /**
     * Generate HTML directory listing (mimics Apache/Nginx directory listing)
     */
    private String generateDirectoryListing(Path directory, String requestPath) throws IOException {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<title>Index of /logs/").append(requestPath).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: monospace; margin: 20px; }\n");
        html.append("h1 { border-bottom: 1px solid #ccc; }\n");
        html.append("table { border-collapse: collapse; width: 100%; }\n");
        html.append("th { text-align: left; padding: 8px; border-bottom: 2px solid #ccc; }\n");
        html.append("td { padding: 8px; border-bottom: 1px solid #eee; }\n");
        html.append("a { text-decoration: none; color: #0066cc; }\n");
        html.append("a:hover { text-decoration: underline; }\n");
        html.append(".size { text-align: right; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");

        html.append("<h1>Index of /logs/").append(requestPath).append("</h1>\n");
        html.append("<table>\n");
        html.append("<tr><th>Name</th><th>Size</th><th>Modified</th></tr>\n");

        // Parent directory link
        if (!requestPath.isEmpty()) {
            html.append("<tr><td><a href=\"../\">../</a></td><td>-</td><td>-</td></tr>\n");
        }

        // List files and directories
        try (Stream<Path> paths = Files.list(directory)) {
            paths.sorted((a, b) -> {
                // Directories first, then files, alphabetically
                boolean aIsDir = Files.isDirectory(a);
                boolean bIsDir = Files.isDirectory(b);
                if (aIsDir != bIsDir) {
                    return aIsDir ? -1 : 1;
                }
                return a.getFileName().toString().compareTo(b.getFileName().toString());
            }).forEach(path -> {
                try {
                    String name = path.getFileName().toString();
                    boolean isDirectory = Files.isDirectory(path);
                    String displayName = isDirectory ? name + "/" : name;
                    String size = isDirectory ? "-" : formatFileSize(Files.size(path));
                    String modified = Files.getLastModifiedTime(path).toString().substring(0, 19);

                    html.append("<tr>");
                    html.append("<td><a href=\"").append(name);
                    if (isDirectory) html.append("/");
                    html.append("\">").append(displayName).append("</a></td>");
                    html.append("<td class=\"size\">").append(size).append("</td>");
                    html.append("<td>").append(modified).append("</td>");
                    html.append("</tr>\n");
                } catch (IOException e) {
                    log.warn("Error reading file info: {}", path, e);
                }
            });
        }

        html.append("</table>\n");
        html.append("<hr>\n");
        html.append("<p><em>Log File Server (LogSearch Test)</em></p>\n");
        html.append("</body>\n</html>");

        return html.toString();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
