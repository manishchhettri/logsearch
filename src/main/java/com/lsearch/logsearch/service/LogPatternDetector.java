package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-detects log formats from various server types and log frameworks.
 * Maintains a library of common patterns and caches detected patterns per file.
 */
@Service
public class LogPatternDetector {

    private static final Logger log = LoggerFactory.getLogger(LogPatternDetector.class);

    private final LogSearchProperties properties;
    private final List<LogFormatPattern> patternLibrary;
    private final Map<String, LogFormatPattern> detectedPatterns;
    private final ZoneId zoneId;

    public LogPatternDetector(LogSearchProperties properties) {
        this.properties = properties;
        this.patternLibrary = buildPatternLibrary();
        this.detectedPatterns = new ConcurrentHashMap<>();
        this.zoneId = ZoneId.of(properties.getTimezone());
    }

    /**
     * Build library of known log formats
     */
    private List<LogFormatPattern> buildPatternLibrary() {
        List<LogFormatPattern> library = new ArrayList<>();

        // WebLogic format (most specific first)
        // [13 Mar 2026 17:19:25,027] [[STANDBY] ExecuteThread...] [INFO ] [logger] [] [user:xxx] - message
        library.add(new LogFormatPattern(
                "WebLogic",
                "^\\[([^\\]]+)\\]\\s+\\[(.+?)\\]\\s+\\[([A-Z]+)\\s*\\]\\s+\\[([^\\]]+)\\]\\s+\\[\\]\\s+\\[user:([^\\]]+)\\]\\s+-\\s+(.*)$",
                "dd MMM yyyy HH:mm:ss,SSS",
                LogFormatPattern.Extractors.WEBLOGIC
        ));

        // WebLogic alternative (without empty brackets)
        library.add(new LogFormatPattern(
                "WebLogic-Alt",
                "^\\[([^\\]]+)\\]\\s+\\[(.+?)\\]\\s+\\[([A-Z]+)\\s*\\]\\s+\\[([^\\]]+)\\]\\s+\\[user:([^\\]]+)\\]\\s+-\\s+(.*)$",
                "dd MMM yyyy HH:mm:ss,SSS",
                LogFormatPattern.Extractors.WEBLOGIC
        ));

        // WebSphere format
        // [13/03/2026 17:19:25:027 NZDT] [thread] level logger [user] message
        library.add(new LogFormatPattern(
                "WebSphere",
                "^\\[([^\\]]+)\\]\\s+\\[([^\\]]+)\\]\\s+([A-Z]+)\\s+([^\\s]+)\\s+\\[([^\\]]+)\\]\\s+(.*)$",
                "dd/MM/yyyy HH:mm:ss:SSS z",
                LogFormatPattern.Extractors.WEBSPHERE
        ));

        // Log4j/Tomcat format
        // 2026-03-13 17:19:25,027 [thread] INFO logger - message
        library.add(new LogFormatPattern(
                "Log4j",
                "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\s+\\[([^\\]]+)\\]\\s+([A-Z]+)\\s+([^\\s]+)\\s+-\\s+(.*)$",
                "yyyy-MM-dd HH:mm:ss,SSS",
                LogFormatPattern.Extractors.LOG4J
        ));

        // ISO 8601 with level
        // 2026-03-13T17:19:25.027+13:00 ERROR message
        library.add(new LogFormatPattern(
                "ISO-8601",
                "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2})\\s+([A-Z]+)\\s+(.*)$",
                null, // Uses ZonedDateTime.parse directly
                LogFormatPattern.Extractors.ISO_WITH_LEVEL
        ));

        // Simple format with user
        // [2026-03-13T17:19:25.027+13:00] [username] message
        library.add(new LogFormatPattern(
                "Simple",
                "^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                LogFormatPattern.Extractors.SIMPLE
        ));

        return library;
    }

    /**
     * Parse a log line using auto-detection.
     * Uses cached pattern for file if available, otherwise tries all patterns.
     */
    public LogEntry parseLine(String line, String sourceFile, long lineNumber) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // Try cached pattern for this file first (fast path)
        LogFormatPattern cachedPattern = detectedPatterns.get(sourceFile);
        if (cachedPattern != null) {
            LogEntry entry = cachedPattern.tryParse(line, sourceFile, lineNumber, zoneId);
            if (entry != null) {
                return entry;
            }
            // Pattern failed, clear cache and try detection again
            detectedPatterns.remove(sourceFile);
        }

        // Try all patterns until one matches
        for (LogFormatPattern pattern : patternLibrary) {
            LogEntry entry = pattern.tryParse(line, sourceFile, lineNumber, zoneId);
            if (entry != null) {
                // Cache the detected pattern for this file
                detectedPatterns.put(sourceFile, pattern);
                log.info("Auto-detected log format '{}' for file: {}", pattern.getName(), sourceFile);
                return entry;
            }
        }

        // No pattern matched
        return null;
    }

    /**
     * Get the detected pattern name for a file (for debugging/monitoring)
     */
    public String getDetectedPatternName(String sourceFile) {
        LogFormatPattern pattern = detectedPatterns.get(sourceFile);
        return pattern != null ? pattern.getName() : "Not detected";
    }

    /**
     * Clear cached patterns (useful for testing or when log format changes)
     */
    public void clearCache() {
        detectedPatterns.clear();
        log.info("Cleared all cached log format patterns");
    }

    /**
     * Get statistics about detected patterns
     */
    public Map<String, LogFormatPattern> getDetectedPatterns() {
        return new ConcurrentHashMap<>(detectedPatterns);
    }
}
