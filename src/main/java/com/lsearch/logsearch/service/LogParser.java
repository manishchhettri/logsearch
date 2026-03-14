package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogParser {

    private static final Logger log = LoggerFactory.getLogger(LogParser.class);

    private final LogSearchProperties properties;
    private final Pattern logPattern;
    private final Map<String, FileTime> fileModifiedCache = new ConcurrentHashMap<>();
    private final List<Pattern> timestampPatterns = new ArrayList<>();

    public LogParser(LogSearchProperties properties) {
        this.properties = properties;

        // Compile the log line pattern from configuration
        // Default: [timestamp] [user] message
        this.logPattern = Pattern.compile(properties.getLogLinePattern());
        log.info("Initialized LogParser with pattern: {}", properties.getLogLinePattern());

        // Initialize common timestamp patterns for fallback detection
        initializeTimestampPatterns();
    }

    private void initializeTimestampPatterns() {
        // ISO 8601 formats
        timestampPatterns.add(Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}"));
        // Apache/NCSA format
        timestampPatterns.add(Pattern.compile("\\d{2}/\\w{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2}"));
        // Syslog format
        timestampPatterns.add(Pattern.compile("\\w{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}"));
        // Simple date-time
        timestampPatterns.add(Pattern.compile("\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}"));
    }

    // New method with Path support for fallback
    public LogEntry parseLine(String line, String sourceFile, long lineNumber, Path filePath) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // Tier 1: Try primary configured pattern
        LogEntry entry = tryPrimaryPattern(line, sourceFile, lineNumber);
        if (entry != null) {
            return entry;
        }

        // If fallback is disabled, return null
        if (!properties.isEnableFallback()) {
            return null;
        }

        // Tier 2: Try JSON format
        entry = tryJsonFormat(line, sourceFile, lineNumber);
        if (entry != null) {
            return entry;
        }

        // Tier 3: Try Apache/nginx access log format
        entry = tryApacheFormat(line, sourceFile, lineNumber);
        if (entry != null) {
            return entry;
        }

        // Tier 4: Try to auto-detect timestamp in line
        entry = tryTimestampExtraction(line, sourceFile, lineNumber);
        if (entry != null) {
            return entry;
        }

        // Tier 5: File-based fallback
        return createFallbackEntry(line, sourceFile, lineNumber, filePath);
    }

    // Legacy method for backward compatibility
    public LogEntry parseLine(String line, String sourceFile, long lineNumber) {
        return tryPrimaryPattern(line, sourceFile, lineNumber);
    }

    private LogEntry tryPrimaryPattern(String line, String sourceFile, long lineNumber) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = logPattern.matcher(line);
        if (!matcher.matches()) {
            log.debug("Line {} in {} does not match expected pattern", lineNumber, sourceFile);
            return null;
        }

        try {
            int groupCount = matcher.groupCount();

            // Group 1 is always timestamp
            String timestampStr = matcher.group(1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(properties.getLogDatetimeFormat());

            // Parse as LocalDateTime first, then apply configured timezone
            LocalDateTime localDateTime = LocalDateTime.parse(timestampStr, formatter);
            ZoneId zoneId = ZoneId.of(properties.getTimezone());
            ZonedDateTime timestamp = ZonedDateTime.of(localDateTime, zoneId);

            LogEntry.Builder builder = LogEntry.builder()
                    .timestamp(timestamp)
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber);

            // Support flexible patterns with different numbers of groups
            // Legacy 3-group pattern: (timestamp)(user)(message)
            // New 6-group pattern: (timestamp)(thread)(level)(logger)(user)(message)
            if (groupCount == 3) {
                // Legacy pattern: [timestamp] [user] message
                builder.user(matcher.group(2).trim())
                       .message(matcher.group(3));
            } else if (groupCount >= 6) {
                // Enhanced pattern: [timestamp] [thread] [level] [logger] [] [user:xxx] - message
                String thread = matcher.group(2);
                String level = matcher.group(3);
                String logger = matcher.group(4);
                String user = matcher.group(5);
                String message = matcher.group(6);

                builder.thread(thread != null ? thread.trim() : null)
                       .level(level != null ? level.trim() : null)
                       .logger(logger != null ? logger.trim() : null)
                       .user(user != null ? user.trim() : null)
                       .message(message != null ? message : "");
            } else {
                log.warn("Unexpected number of capture groups: {} in line {} of {}", groupCount, lineNumber, sourceFile);
                return null;
            }

            return builder.build();

        } catch (DateTimeParseException e) {
            log.warn("Failed to parse timestamp in line {} of {}: {}", lineNumber, sourceFile, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse line {} of {}: {}", lineNumber, sourceFile, e.getMessage());
            return null;
        }
    }

    public String extractDateFromFilename(String filename) {
        Pattern pattern = Pattern.compile(properties.getFilenameDatePattern());
        Matcher matcher = pattern.matcher(filename);

        if (matcher.matches()) {
            String year = matcher.group(1);
            String month = matcher.group(2);
            String day = matcher.group(3);
            return String.format("%s-%s-%s", year, month, day);
        }

        return null;
    }

    private LogEntry tryJsonFormat(String line, String sourceFile, long lineNumber) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        try {
            // Simple JSON parsing without external libraries
            // Look for common JSON log fields: timestamp, level, message, logger, etc.
            String timestamp = extractJsonField(trimmed, "timestamp", "time", "date", "@timestamp");
            String level = extractJsonField(trimmed, "level", "severity", "loglevel");
            String message = extractJsonField(trimmed, "message", "msg", "text");
            String logger = extractJsonField(trimmed, "logger", "name", "component");
            String thread = extractJsonField(trimmed, "thread", "threadName");
            String user = extractJsonField(trimmed, "user", "userId", "username");

            if (message == null) {
                message = trimmed; // Use whole JSON as message if no message field found
            }

            ZonedDateTime parsedTimestamp = null;
            if (timestamp != null) {
                parsedTimestamp = parseDetectedTimestamp(timestamp);
                if (parsedTimestamp == null) {
                    // Try ISO instant format (common in JSON logs)
                    try {
                        parsedTimestamp = ZonedDateTime.parse(timestamp);
                    } catch (Exception e) {
                        // Timestamp parsing failed, will fall back to current time
                    }
                }
            }

            if (parsedTimestamp == null) {
                parsedTimestamp = ZonedDateTime.now(ZoneId.of(properties.getTimezone()));
            }

            log.debug("Parsed JSON log format in line {}", lineNumber);
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

        } catch (Exception e) {
            log.debug("Failed to parse as JSON: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonField(String json, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
            // Also try non-quoted values (numbers, booleans)
            pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*([^,}\\]]+)");
            matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private LogEntry tryApacheFormat(String line, String sourceFile, long lineNumber) {
        // Apache/nginx combined log format:
        // 127.0.0.1 - user [timestamp] "GET /path HTTP/1.1" 200 1234 "referer" "user-agent"
        Pattern apachePattern = Pattern.compile(
            "^([\\d.]+) \\S+ (\\S+) \\[([^\\]]+)\\] \"([A-Z]+) ([^\"]+) HTTP/[^\"]+\" (\\d{3}) (\\d+|-).*"
        );

        Matcher matcher = apachePattern.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        try {
            String ip = matcher.group(1);
            String user = matcher.group(2);
            String timestampStr = matcher.group(3);
            String method = matcher.group(4);
            String path = matcher.group(5);
            String status = matcher.group(6);
            String size = matcher.group(7);

            // Parse Apache timestamp format: dd/MMM/yyyy:HH:mm:ss Z
            DateTimeFormatter apacheFormatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");
            ZonedDateTime timestamp = ZonedDateTime.parse(timestampStr, apacheFormatter);

            String level = "INFO";
            if (status.startsWith("4") || status.startsWith("5")) {
                level = "ERROR";
            }

            String message = String.format("%s %s %s %s %s", method, path, status, size, ip);

            log.debug("Parsed Apache log format in line {}", lineNumber);
            return LogEntry.builder()
                    .timestamp(timestamp)
                    .level(level)
                    .message(message)
                    .user(user.equals("-") ? null : user)
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber)
                    .build();

        } catch (Exception e) {
            log.debug("Failed to parse as Apache format: {}", e.getMessage());
            return null;
        }
    }

    private LogEntry tryTimestampExtraction(String line, String sourceFile, long lineNumber) {
        // Try to find any timestamp pattern in the line
        for (Pattern pattern : timestampPatterns) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String timestampStr = matcher.group();
                ZonedDateTime timestamp = parseDetectedTimestamp(timestampStr);

                if (timestamp != null) {
                    // Extract message (everything after timestamp, or whole line)
                    String message = line.substring(matcher.end()).trim();
                    if (message.isEmpty()) {
                        message = line;
                    }

                    log.debug("Auto-detected timestamp in line {}: {}", lineNumber, timestampStr);
                    return LogEntry.builder()
                            .timestamp(timestamp)
                            .message(message)
                            .sourceFile(sourceFile)
                            .lineNumber(lineNumber)
                            .build();
                }
            }
        }
        return null;
    }

    private ZonedDateTime parseDetectedTimestamp(String timestampStr) {
        // Try common formats
        String[] formats = {
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "dd/MMM/yyyy:HH:mm:ss",
            "MMM dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss"
        };

        for (String format : formats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                LocalDateTime localDateTime = LocalDateTime.parse(timestampStr, formatter);
                return ZonedDateTime.of(localDateTime, ZoneId.of(properties.getTimezone()));
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        return null;
    }

    private LogEntry createFallbackEntry(String line, String sourceFile, long lineNumber, Path filePath) {
        ZonedDateTime baseTimestamp = getBaseTimestamp(sourceFile, filePath);

        // Add microseconds based on line number to maintain order
        ZonedDateTime timestamp = baseTimestamp.plusNanos(lineNumber * 1_000_000);

        log.debug("Using fallback timestamp for line {} in {}: {}", lineNumber, sourceFile, timestamp);
        return LogEntry.builder()
                .timestamp(timestamp)
                .message(line)
                .sourceFile(sourceFile)
                .lineNumber(lineNumber)
                .build();
    }

    private ZonedDateTime getBaseTimestamp(String filename, Path filePath) {
        // Priority 1: Extract date from filename
        String dateFromFilename = extractDateFromFilename(filename);
        if (dateFromFilename != null) {
            try {
                LocalDate date = LocalDate.parse(dateFromFilename);
                return date.atStartOfDay(ZoneId.of(properties.getTimezone()));
            } catch (DateTimeParseException e) {
                log.debug("Failed to parse date from filename: {}", filename);
            }
        }

        // Priority 2: Use file last modified time
        if (filePath != null) {
            FileTime lastModified = getFileModifiedTime(filePath);
            if (lastModified != null) {
                return ZonedDateTime.ofInstant(
                        lastModified.toInstant(),
                        ZoneId.of(properties.getTimezone())
                ).truncatedTo(ChronoUnit.DAYS);
            }
        }

        // Priority 3: Current date (last resort)
        log.warn("Using current date as fallback for {}", filename);
        return ZonedDateTime.now(ZoneId.of(properties.getTimezone())).truncatedTo(ChronoUnit.DAYS);
    }

    private FileTime getFileModifiedTime(Path filePath) {
        return fileModifiedCache.computeIfAbsent(
                filePath.toString(),
                k -> {
                    try {
                        return Files.getLastModifiedTime(filePath);
                    } catch (IOException e) {
                        log.debug("Cannot read file modified time for {}: {}", filePath, e.getMessage());
                        return null;
                    }
                }
        );
    }

    /**
     * Check if a line matches the primary configured pattern.
     * This is used to distinguish new log entries from continuation lines.
     */
    public boolean matchesPrimaryPattern(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        return logPattern.matcher(line).matches();
    }

    /**
     * Smart heuristic to detect if a line is likely a continuation line
     * (e.g., stack trace, multi-line message) rather than a new log entry.
     */
    public boolean looksLikeContinuationLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }

        // Heuristic 1: Line starts with whitespace (indentation)
        if (Character.isWhitespace(line.charAt(0))) {
            return true;
        }

        String trimmed = line.trim();

        // Heuristic 2: Common stack trace patterns
        if (trimmed.startsWith("at ") ||
            trimmed.startsWith("Caused by:") ||
            trimmed.startsWith("Suppressed:") ||
            trimmed.matches("^\\.\\.\\. \\d+ more$") ||
            trimmed.matches("^\\.\\.\\. \\d+ common frames omitted$")) {
            return true;
        }

        // Heuristic 3: Looks like a plain property or continuation
        // (no timestamp-like pattern at the beginning)
        // Check if line starts with something that looks like a timestamp
        boolean hasTimestampLikeStart =
            trimmed.matches("^\\[.*") ||                    // Starts with [
            trimmed.matches("^\\d{4}[-/]\\d{2}[-/]\\d{2}.*") ||  // Date-like start
            trimmed.matches("^\\d{2}[:/].*") ||             // Two digits with : or /
            trimmed.matches("^[A-Z][a-z]{2}\\s+\\d{1,2}\\s+.*") || // Month day format
            trimmed.matches("^\\{.*");                      // JSON-like start

        // If it doesn't have timestamp-like start, it's likely a continuation
        return !hasTimestampLikeStart;
    }
}
