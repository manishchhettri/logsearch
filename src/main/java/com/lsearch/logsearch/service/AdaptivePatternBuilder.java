package com.lsearch.logsearch.service;

import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adaptive pattern builder that analyzes log line structure to automatically
 * detect field positions and build custom patterns.
 *
 * Algorithm:
 * 1. Extract all bracketed fields [...] from the line
 * 2. Classify each field (timestamp, log level, thread, logger, user)
 * 3. Build a custom regex pattern based on detected structure
 * 4. Validate pattern on sample lines
 * 5. Cache successful patterns for reuse
 */
public class AdaptivePatternBuilder {

    private static final Logger log = LoggerFactory.getLogger(AdaptivePatternBuilder.class);

    // Common date/time formats to try when detecting timestamps
    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss:SSS"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss.SSS")
    );

    // Log level keywords (uppercase first, then mixed case)
    private static final Set<String> LOG_LEVELS_UPPER = new HashSet<>(Arrays.asList(
            "ERROR", "WARN", "WARNING", "INFO", "DEBUG", "TRACE", "FATAL", "SEVERE", "FINE", "FINER", "FINEST"
    ));

    private static final Set<String> LOG_LEVELS_MIXED = new HashSet<>(Arrays.asList(
            "Error", "Warn", "Warning", "Info", "Debug", "Trace", "Fatal", "Severe", "Fine", "Finer", "Finest"
    ));

    // Thread detection keywords
    private static final Pattern THREAD_PATTERN = Pattern.compile(
            ".*(Thread|ExecuteThread|Worker|Pool|Executor|AsyncTask|Runnable).*",
            Pattern.CASE_INSENSITIVE
    );

    // User detection patterns
    private static final Pattern USER_PATTERN = Pattern.compile(
            "user:?\\s*([^\\]\\s]+)|userid:?\\s*([^\\]\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Logger detection (Java package/class pattern)
    private static final Pattern LOGGER_PATTERN = Pattern.compile(
            "^([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*\\.?[A-Z][a-zA-Z0-9_]*)$|^[A-Z][a-zA-Z0-9_]+$"
    );

    /**
     * Analyze a log line and build a custom pattern
     */
    public LogFormatPattern buildPattern(List<String> sampleLines, ZoneId zoneId) {
        if (sampleLines == null || sampleLines.isEmpty()) {
            return null;
        }

        // Analyze first line to detect structure
        String firstLine = sampleLines.get(0);
        FieldClassification classification = classifyFields(firstLine);

        if (classification == null || !classification.isValid()) {
            log.debug("Could not classify fields in line: {}", firstLine);
            return null;
        }

        // Build regex pattern from classification
        LogFormatPattern pattern = buildPatternFromClassification(classification, zoneId);

        if (pattern == null) {
            return null;
        }

        // Validate pattern on remaining sample lines
        double successRate = validatePattern(pattern, sampleLines, zoneId);

        if (successRate >= 0.7) { // 70% success rate required
            log.info("Adaptive pattern built successfully: {} (success rate: {:.1f}%)",
                     classification.getStructureDescription(), successRate * 100);
            return pattern;
        } else {
            log.debug("Adaptive pattern validation failed: {:.1f}% success rate", successRate * 100);
            return null;
        }
    }

    /**
     * Extract and classify all bracketed fields from a log line
     */
    private FieldClassification classifyFields(String line) {
        List<String> brackets = extractBrackets(line);

        if (brackets.isEmpty()) {
            return null;
        }

        FieldClassification classification = new FieldClassification();
        classification.totalBrackets = brackets.size();

        // Classify each bracket
        for (int i = 0; i < brackets.size(); i++) {
            String field = brackets.get(i);

            // Try timestamp detection
            if (classification.timestampIndex == -1 && isTimestamp(field, classification)) {
                classification.timestampIndex = i;
                log.debug("  Bracket {}: TIMESTAMP -> {}", i, field);
                continue;
            }

            // Try log level detection (uppercase first)
            String logLevel = detectLogLevel(field, true);
            if (classification.levelIndex == -1 && logLevel != null) {
                classification.levelIndex = i;
                classification.level = logLevel;
                log.debug("  Bracket {}: LOG LEVEL (uppercase) -> {}", i, field);
                continue;
            }

            // Try log level detection (mixed case)
            logLevel = detectLogLevel(field, false);
            if (classification.levelIndex == -1 && logLevel != null) {
                classification.levelIndex = i;
                classification.level = logLevel;
                log.debug("  Bracket {}: LOG LEVEL (mixed) -> {}", i, field);
                continue;
            }

            // Try thread detection
            if (classification.threadIndex == -1 && isThread(field)) {
                classification.threadIndex = i;
                log.debug("  Bracket {}: THREAD -> {}", i, field);
                continue;
            }

            // Try user extraction
            String user = extractUser(field);
            if (classification.userIndex == -1 && user != null) {
                classification.userIndex = i;
                classification.hasUserPrefix = true;
                log.debug("  Bracket {}: USER -> {}", i, field);
                continue;
            }

            // Try logger detection
            if (classification.loggerIndex == -1 && isLogger(field)) {
                classification.loggerIndex = i;
                log.debug("  Bracket {}: LOGGER -> {}", i, field);
                continue;
            }

            log.debug("  Bracket {}: UNKNOWN -> {}", i, field);
        }

        // Check for message after brackets
        String afterBrackets = line.substring(findLastBracketEnd(line)).trim();
        if (!afterBrackets.isEmpty()) {
            // Check if message starts with separator (- or :)
            if (afterBrackets.startsWith("-") || afterBrackets.startsWith(":")) {
                classification.messageSeparator = afterBrackets.substring(0, 1);
                classification.message = afterBrackets.substring(1).trim();
            } else {
                classification.message = afterBrackets;
            }
        }

        return classification;
    }

    /**
     * Extract all bracketed [...] fields from a line
     */
    private List<String> extractBrackets(String line) {
        List<String> brackets = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '[') {
                if (depth == 0) {
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    brackets.add(current.toString());
                } else if (depth > 0) {
                    current.append(c);
                }
            } else if (depth > 0) {
                current.append(c);
            }
        }

        return brackets;
    }

    /**
     * Find the position after the last closing bracket
     */
    private int findLastBracketEnd(String line) {
        int lastBracket = line.lastIndexOf(']');
        return lastBracket >= 0 ? lastBracket + 1 : line.length();
    }

    /**
     * Try to parse field as timestamp using common formats
     */
    private boolean isTimestamp(String field, FieldClassification classification) {
        // Try each format
        for (DateTimeFormatter formatter : TIMESTAMP_FORMATS) {
            try {
                LocalDateTime.parse(field.trim(), formatter);
                classification.timestampFormat = formatter;
                return true;
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        return false;
    }

    /**
     * Detect log level in field (uppercase first, then mixed case)
     */
    private String detectLogLevel(String field, boolean uppercase) {
        String trimmed = field.trim();
        Set<String> levels = uppercase ? LOG_LEVELS_UPPER : LOG_LEVELS_MIXED;

        // Exact match
        if (levels.contains(trimmed)) {
            return trimmed.toUpperCase();
        }

        // Check if field contains log level
        for (String level : levels) {
            if (trimmed.equalsIgnoreCase(level)) {
                return level.toUpperCase();
            }
        }

        return null;
    }

    /**
     * Check if field looks like a thread name
     */
    private boolean isThread(String field) {
        return THREAD_PATTERN.matcher(field).matches();
    }

    /**
     * Extract user from field (e.g., "user:admin" -> "admin")
     */
    private String extractUser(String field) {
        Matcher matcher = USER_PATTERN.matcher(field);
        if (matcher.find()) {
            String user = matcher.group(1);
            return user != null ? user : matcher.group(2);
        }
        return null;
    }

    /**
     * Check if field looks like a logger (Java class/package name)
     */
    private boolean isLogger(String field) {
        String trimmed = field.trim();
        // Should be reasonably short and match logger pattern
        return trimmed.length() > 0 && trimmed.length() < 200
               && LOGGER_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Build a LogFormatPattern from the field classification
     */
    private LogFormatPattern buildPatternFromClassification(FieldClassification classification, ZoneId zoneId) {
        if (!classification.isValid()) {
            return null;
        }

        // Build regex pattern
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < classification.totalBrackets; i++) {
            regex.append("\\[");

            if (i == classification.timestampIndex) {
                regex.append("([^\\]]+)"); // Capture timestamp
            } else if (i == classification.threadIndex) {
                regex.append("(.+?)"); // Capture thread (non-greedy for nested brackets)
            } else if (i == classification.levelIndex) {
                regex.append("([A-Za-z]+)\\s*"); // Capture level
            } else if (i == classification.loggerIndex) {
                regex.append("([^\\]]+)"); // Capture logger
            } else if (i == classification.userIndex) {
                if (classification.hasUserPrefix) {
                    regex.append("user:([^\\]]+)"); // Capture user with prefix
                } else {
                    regex.append("([^\\]]+)"); // Capture user without prefix
                }
            } else {
                regex.append("[^\\]]*"); // Skip unknown brackets
            }

            regex.append("\\]");

            if (i < classification.totalBrackets - 1) {
                regex.append("\\s*");
            }
        }

        // Add message pattern
        if (classification.messageSeparator != null) {
            regex.append("\\s*").append(Pattern.quote(classification.messageSeparator)).append("\\s*(.*)$");
        } else {
            regex.append("\\s*(.*)$");
        }

        // Build extractor
        LogFormatPattern.FieldExtractor extractor = buildExtractor(classification, zoneId);

        return new LogFormatPattern(
                "Adaptive-" + classification.getStructureDescription(),
                regex.toString(),
                null, // Formatter stored in classification
                extractor
        );
    }

    /**
     * Build field extractor based on classification
     */
    private LogFormatPattern.FieldExtractor buildExtractor(FieldClassification classification, ZoneId zoneId) {
        return (matcher, formatter, zone, sourceFile, lineNumber) -> {
            // Map regex groups to fields based on classification
            int groupIndex = 1;

            String timestamp = null;
            String thread = null;
            String level = null;
            String logger = null;
            String user = null;
            String message = null;

            for (int i = 0; i < classification.totalBrackets; i++) {
                if (i == classification.timestampIndex) {
                    timestamp = matcher.group(groupIndex++);
                } else if (i == classification.threadIndex) {
                    thread = matcher.group(groupIndex++);
                } else if (i == classification.levelIndex) {
                    level = matcher.group(groupIndex++);
                } else if (i == classification.loggerIndex) {
                    logger = matcher.group(groupIndex++);
                } else if (i == classification.userIndex) {
                    user = matcher.group(groupIndex++);
                }
            }

            // Message is always last group
            message = matcher.group(groupIndex);

            // Parse timestamp
            LocalDateTime localDateTime = LocalDateTime.parse(timestamp.trim(), classification.timestampFormat);
            ZonedDateTime zonedTimestamp = ZonedDateTime.of(localDateTime, zoneId);

            return LogEntry.builder()
                    .timestamp(zonedTimestamp)
                    .thread(thread != null ? thread.trim() : null)
                    .level(level != null ? level.trim().toUpperCase() : null)
                    .logger(logger != null ? logger.trim() : null)
                    .user(user != null ? user.trim() : null)
                    .message(message != null ? message : "")
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber)
                    .build();
        };
    }

    /**
     * Validate pattern on sample lines and return success rate
     */
    private double validatePattern(LogFormatPattern pattern, List<String> sampleLines, ZoneId zoneId) {
        int successful = 0;
        int tested = Math.min(sampleLines.size(), 10); // Test first 10 lines

        for (int i = 0; i < tested; i++) {
            LogEntry entry = pattern.tryParse(sampleLines.get(i), "test", i + 1, zoneId);
            if (entry != null) {
                successful++;
            }
        }

        return (double) successful / tested;
    }

    /**
     * Holds classification results for a log line
     */
    private static class FieldClassification {
        int totalBrackets = 0;
        int timestampIndex = -1;
        int levelIndex = -1;
        int threadIndex = -1;
        int loggerIndex = -1;
        int userIndex = -1;
        DateTimeFormatter timestampFormat = null;
        String level = null;
        boolean hasUserPrefix = false;
        String messageSeparator = null;
        String message = null;

        boolean isValid() {
            // Must have at least timestamp and level
            return timestampIndex >= 0 && levelIndex >= 0 && timestampFormat != null;
        }

        String getStructureDescription() {
            List<String> parts = new ArrayList<>();
            if (timestampIndex >= 0) parts.add("timestamp");
            if (threadIndex >= 0) parts.add("thread");
            if (levelIndex >= 0) parts.add("level");
            if (loggerIndex >= 0) parts.add("logger");
            if (userIndex >= 0) parts.add("user");
            parts.add("message");
            return String.join("-", parts);
        }
    }
}
