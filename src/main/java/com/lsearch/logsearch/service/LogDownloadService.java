package com.lsearch.logsearch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class LogDownloadService {

    private static final Logger log = LoggerFactory.getLogger(LogDownloadService.class);

    @Value("${log-search.logs-dir}")
    private String logsDirectory;

    public DownloadResult downloadLogs(List<String> urls, String username, String password) {
        DownloadResult result = new DownloadResult();

        // Ensure logs directory exists
        Path logsPath = Paths.get(logsDirectory);
        try {
            Files.createDirectories(logsPath);
        } catch (IOException e) {
            log.error("Failed to create logs directory: {}", logsDirectory, e);
            result.addError("Failed to create logs directory: " + e.getMessage());
            return result;
        }

        // Download each URL
        for (String urlString : urls) {
            if (urlString == null || urlString.trim().isEmpty()) {
                continue;
            }

            try {
                downloadAndExtract(urlString.trim(), username, password, logsPath, result);
            } catch (Exception e) {
                log.error("Failed to download from URL: {}", urlString, e);
                result.addError("Failed to download " + urlString + ": " + e.getMessage());
            }
        }

        return result;
    }

    private void downloadAndExtract(String urlString, String username, String password,
                                    Path logsPath, DownloadResult result) throws IOException {

        log.info("Processing URL: {}", urlString);

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Set basic authentication
        if (username != null && !username.isEmpty()) {
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
        }

        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000); // 30 seconds
        conn.setReadTimeout(300000);   // 5 minutes

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for URL: " + urlString);
        }

        // Check Content-Type to detect directory listing
        String contentType = conn.getContentType();
        if (contentType != null && contentType.contains("text/html")) {
            log.info("Detected directory listing at: {}", urlString);
            handleDirectoryListing(conn, urlString, username, password, logsPath, result);
            conn.disconnect();
            return;
        }

        // Download as file
        downloadFile(conn, urlString, logsPath, result);
        conn.disconnect();
    }

    private void handleDirectoryListing(HttpURLConnection conn, String baseUrl, String username,
                                       String password, Path logsPath, DownloadResult result) throws IOException {

        // Read HTML content
        StringBuilder htmlContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                htmlContent.append(line).append("\n");
            }
        }

        // Parse HTML for log file links
        List<String> fileUrls = parseDirectoryListing(htmlContent.toString(), baseUrl);

        if (fileUrls.isEmpty()) {
            log.warn("No log files found in directory listing: {}", baseUrl);
            result.addError("No log files (.log, .gz, .zip) found in directory: " + baseUrl);
            return;
        }

        log.info("Found {} log files in directory listing", fileUrls.size());

        // Download each file
        for (String fileUrl : fileUrls) {
            try {
                downloadAndExtract(fileUrl, username, password, logsPath, result);
            } catch (Exception e) {
                log.error("Failed to download file from directory: {}", fileUrl, e);
                result.addError("Failed to download " + fileUrl + ": " + e.getMessage());
            }
        }
    }

    private List<String> parseDirectoryListing(String html, String baseUrl) {
        Set<String> fileUrls = new HashSet<>();

        // Ensure base URL ends with /
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        // Pattern to match href links
        // Matches: href="filename" or href='filename'
        Pattern pattern = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String href = matcher.group(1);

            // Skip parent directory, current directory, and absolute URLs
            if (href.equals("..") || href.equals("../") || href.equals(".") ||
                href.startsWith("http://") || href.startsWith("https://") ||
                href.startsWith("/") || href.startsWith("?")) {
                continue;
            }

            // Skip directories (ending with /)
            if (href.endsWith("/")) {
                continue;
            }

            String lowerHref = href.toLowerCase();

            // Include log files with extensions
            if (lowerHref.endsWith(".log") || lowerHref.endsWith(".log.gz") ||
                lowerHref.endsWith(".gz") || lowerHref.endsWith(".zip")) {

                String fullUrl = baseUrl + href;
                fileUrls.add(fullUrl);
                log.debug("Found log file: {}", fullUrl);
            }
            // Include extensionless files (WebLogic style)
            // Skip common web files
            else if (!lowerHref.endsWith(".html") && !lowerHref.endsWith(".htm") &&
                     !lowerHref.endsWith(".css") && !lowerHref.endsWith(".js") &&
                     !lowerHref.endsWith(".jpg") && !lowerHref.endsWith(".png") &&
                     !lowerHref.endsWith(".gif") && !lowerHref.endsWith(".ico") &&
                     !lowerHref.endsWith(".xml") && !lowerHref.endsWith(".json") &&
                     !lowerHref.contains(".")) {  // No extension at all

                String fullUrl = baseUrl + href;
                fileUrls.add(fullUrl);
                log.debug("Found extensionless file (potential log): {}", fullUrl);
            }
        }

        return new ArrayList<>(fileUrls);
    }

    private void downloadFile(HttpURLConnection conn, String urlString, Path logsPath,
                             DownloadResult result) throws IOException {

        // Extract filename from URL
        String filename = extractFilename(urlString);
        Path tempFile = Files.createTempFile("logdownload", filename);

        // Download to temp file
        long totalBytes = 0;
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            log.info("Downloaded {} bytes from {}", totalBytes, urlString);
        }

        // Skip empty files
        if (totalBytes == 0) {
            log.warn("Skipping empty file: {}", filename);
            Files.deleteIfExists(tempFile);
            return;
        }

        // Check if file needs extraction
        if (filename.endsWith(".gz")) {
            extractGzip(tempFile, logsPath, filename, result);
        } else if (filename.endsWith(".zip")) {
            extractZip(tempFile, logsPath, result);
        } else {
            // For files without extension or .log files, check if it's text
            if (!hasKnownLogExtension(filename)) {
                if (!isTextFile(tempFile)) {
                    log.warn("Skipping binary file: {}", filename);
                    result.addError("Skipped binary file: " + filename);
                    Files.deleteIfExists(tempFile);
                    return;
                }
                log.info("Detected text file: {}", filename);
            }

            // Copy as-is
            Path targetFile = logsPath.resolve(filename);
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            result.addDownloadedFile(filename);
            log.info("Saved file: {}", targetFile);
        }

        // Clean up temp file if it still exists
        Files.deleteIfExists(tempFile);
    }

    private boolean hasKnownLogExtension(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".log") || lower.endsWith(".txt");
    }

    private boolean isTextFile(Path file) throws IOException {
        // Read first 8KB to determine if it's text
        byte[] sample = new byte[8192];
        int bytesRead;

        try (InputStream in = Files.newInputStream(file)) {
            bytesRead = in.read(sample);
        }

        if (bytesRead <= 0) {
            return false;
        }

        // Check for null bytes (strong indicator of binary)
        for (int i = 0; i < bytesRead; i++) {
            if (sample[i] == 0) {
                return false;
            }
        }

        // Count printable ASCII and common whitespace characters
        int printableCount = 0;
        for (int i = 0; i < bytesRead; i++) {
            byte b = sample[i];
            // ASCII printable (32-126), or common whitespace (9, 10, 13)
            if ((b >= 32 && b <= 126) || b == 9 || b == 10 || b == 13) {
                printableCount++;
            }
        }

        // If at least 95% of characters are printable, consider it text
        double printableRatio = (double) printableCount / bytesRead;
        return printableRatio >= 0.95;
    }

    private void extractGzip(Path gzipFile, Path targetDir, String originalFilename,
                            DownloadResult result) throws IOException {

        // Remove .gz extension
        String extractedFilename = originalFilename.substring(0, originalFilename.length() - 3);
        Path targetFile = targetDir.resolve(extractedFilename);

        try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(gzipFile));
             OutputStream out = Files.newOutputStream(targetFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = gzipIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            result.addExtractedFile(extractedFilename);
            log.info("Extracted gzip to: {}", targetFile);
        }
    }

    private void extractZip(Path zipFile, Path targetDir, DownloadResult result) throws IOException {

        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                Path targetFile = targetDir.resolve(entry.getName());

                // Create parent directories if needed
                Files.createDirectories(targetFile.getParent());

                try (OutputStream out = Files.newOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = zipIn.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                result.addExtractedFile(entry.getName());
                log.info("Extracted from zip: {}", targetFile);

                zipIn.closeEntry();
            }
        }
    }

    private String extractFilename(String urlString) {
        String path = urlString;

        // Remove query parameters
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }

        // Get last part of path
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            path = path.substring(lastSlash + 1);
        }

        return path.isEmpty() ? "downloaded-log.txt" : path;
    }

    public static class DownloadResult {
        private final List<String> downloadedFiles = new ArrayList<>();
        private final List<String> extractedFiles = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public void addDownloadedFile(String filename) {
            downloadedFiles.add(filename);
        }

        public void addExtractedFile(String filename) {
            extractedFiles.add(filename);
        }

        public void addError(String error) {
            errors.add(error);
        }

        public List<String> getDownloadedFiles() {
            return downloadedFiles;
        }

        public List<String> getExtractedFiles() {
            return extractedFiles;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int getTotalFiles() {
            return downloadedFiles.size() + extractedFiles.size();
        }
    }
}
