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
 *
 * Detection Strategy:
 * 1. Try cached pattern (fast path)
 * 2. Try hardcoded pattern library (WebLogic, Log4j, etc.)
 * 3. Try adaptive pattern builder (analyzes structure dynamically)
 */
@Service
public class LogPatternDetector {

    private static final Logger log = LoggerFactory.getLogger(LogPatternDetector.class);

    private final LogSearchProperties properties;
    private final List<LogFormatPattern> patternLibrary;
    private final Map<String, LogFormatPattern> detectedPatterns;
    private final Map<String, List<String>> sampleLinesCache;
    private final AdaptivePatternBuilder adaptiveBuilder;
    private final ZoneId zoneId;

    public LogPatternDetector(LogSearchProperties properties) {
        this.properties = properties;
        this.patternLibrary = buildPatternLibrary();
        this.detectedPatterns = new ConcurrentHashMap<>();
        this.sampleLinesCache = new ConcurrentHashMap<>();
        this.adaptiveBuilder = new AdaptivePatternBuilder();
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
                log.trace("Parsed with cached pattern '{}': level={}, user={}, message='{}'",
                    cachedPattern.getName(),
                    entry.getLevel(),
                    entry.getUser(),
                    entry.getMessage() != null && entry.getMessage().length() > 50
                        ? entry.getMessage().substring(0, 50) + "..."
                        : entry.getMessage());
                return entry;
            }
            // Pattern failed, clear cache and try detection again
            detectedPatterns.remove(sourceFile);
            log.warn("Cached pattern '{}' failed for file {} at line {}. Line: '{}'",
                cachedPattern.getName(), sourceFile, lineNumber,
                line.length() > 100 ? line.substring(0, 100) + "..." : line);
        }

        // Tier 1: Try all hardcoded patterns in library
        log.debug("Trying {} hardcoded patterns for line {} in {}", patternLibrary.size(), lineNumber, sourceFile);
        for (LogFormatPattern pattern : patternLibrary) {
            LogEntry entry = pattern.tryParse(line, sourceFile, lineNumber, zoneId);
            if (entry != null) {
                // Cache the detected pattern for this file
                detectedPatterns.put(sourceFile, pattern);
                log.info("Auto-detected log format '{}' for file: {}. Extracted: level={}, user={}, thread={}",
                    pattern.getName(), sourceFile, entry.getLevel(), entry.getUser(), entry.getThread());
                return entry;
            }
        }
        log.debug("No hardcoded pattern matched for line {} in {}", lineNumber, sourceFile);

        // Tier 2: Try adaptive pattern builder (analyzes structure dynamically)
        LogEntry adaptiveEntry = tryAdaptivePattern(line, sourceFile, lineNumber);
        if (adaptiveEntry != null) {
            return adaptiveEntry;
        }

        // No pattern matched
        return null;
    }

    /**
     * Try to build an adaptive pattern by analyzing the log line structure.
     * Collects sample lines and uses AdaptivePatternBuilder to detect format.
     */
    private LogEntry tryAdaptivePattern(String line, String sourceFile, long lineNumber) {
        // Collect sample lines for this file (up to 10 lines)
        List<String> samples = sampleLinesCache.computeIfAbsent(sourceFile, k -> new ArrayList<>());

        // Add current line to samples
        if (samples.size() < 10) {
            samples.add(line);
        }

        // Need at least 3 lines to build a reliable pattern
        if (samples.size() < 3) {
            log.debug("Collecting samples for adaptive pattern detection: {}/{} for file: {}",
                     samples.size(), 3, sourceFile);
            return null;
        }

        // Try to build adaptive pattern
        log.info("Attempting adaptive pattern detection for file: {} ({} sample lines)", sourceFile, samples.size());
        LogFormatPattern adaptivePattern = adaptiveBuilder.buildPattern(samples, zoneId);

        if (adaptivePattern != null) {
            // Cache the adaptive pattern
            detectedPatterns.put(sourceFile, adaptivePattern);
            log.info("Successfully built adaptive pattern '{}' for file: {}", adaptivePattern.getName(), sourceFile);

            // Clear sample cache to save memory
            sampleLinesCache.remove(sourceFile);

            // Parse the current line with the new pattern
            return adaptivePattern.tryParse(line, sourceFile, lineNumber, zoneId);
        }

        // Adaptive pattern building failed
        log.debug("Adaptive pattern detection failed for file: {}", sourceFile);
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
        sampleLinesCache.clear();
        log.info("Cleared all cached log format patterns and sample lines");
    }

    /**
     * Get statistics about detected patterns
     */
    public Map<String, LogFormatPattern> getDetectedPatterns() {
        return new ConcurrentHashMap<>(detectedPatterns);
    }
}
