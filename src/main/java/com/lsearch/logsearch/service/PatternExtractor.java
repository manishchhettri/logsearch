package com.lsearch.logsearch.service;

import java.util.regex.Pattern;

/**
 * Extracts normalized patterns from log messages for fingerprinting and analytics.
 *
 * Converts specific log messages into generalized patterns by replacing:
 * - Numbers with *
 * - UUIDs with *
 * - IP addresses with *
 * - Timestamps with *
 * - File paths with *
 * - Hex values with *
 *
 * Example:
 *   Input:  "ERROR Payment failed for order 12345 at 192.168.1.100"
 *   Output: "ERROR Payment failed for order * at *"
 *
 * This enables grouping similar errors for analytics.
 */
public class PatternExtractor {

    // Pre-compiled patterns for performance
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");
    private static final Pattern UUID_PATTERN = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private static final Pattern TIMESTAMP_ISO_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?([+-]\\d{2}:\\d{2}|Z)?");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");
    private static final Pattern HEX_PATTERN = Pattern.compile("\\b0x[0-9a-fA-F]+\\b");
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("(/[\\w.-]+)+|([A-Z]:\\\\[\\w.-\\\\]+)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+");
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\\b[a-zA-Z0-9]{20,}\\b");
    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("\\b([a-z][a-z0-9_]*\\.)+[A-Z][a-zA-Z0-9_]*\\b");

    // Common error patterns to preserve
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b\\w+Exception\\b|\\b\\w+Error\\b");

    /**
     * Extract a normalized pattern from a log message.
     *
     * @param message The original log message
     * @return Normalized pattern with variables replaced by *
     */
    public static String extractPattern(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }

        String pattern = message;

        // Preserve exception names (do this first)
        // Mark exceptions with a placeholder so they don't get replaced
        pattern = EXCEPTION_PATTERN.matcher(pattern).replaceAll("__EXCEPTION__");

        // Replace URLs (before IP addresses to avoid partial replacements)
        pattern = URL_PATTERN.matcher(pattern).replaceAll("*");

        // Replace email addresses
        pattern = EMAIL_PATTERN.matcher(pattern).replaceAll("*");

        // Replace file paths
        pattern = FILE_PATH_PATTERN.matcher(pattern).replaceAll("*");

        // Replace UUIDs
        pattern = UUID_PATTERN.matcher(pattern).replaceAll("*");

        // Replace ISO timestamps
        pattern = TIMESTAMP_ISO_PATTERN.matcher(pattern).replaceAll("*");

        // Replace IP addresses
        pattern = IP_ADDRESS_PATTERN.matcher(pattern).replaceAll("*");

        // Replace hex values
        pattern = HEX_PATTERN.matcher(pattern).replaceAll("*");

        // Replace session IDs and long alphanumeric strings (likely IDs)
        pattern = SESSION_ID_PATTERN.matcher(pattern).replaceAll("*");

        // Replace Java class names (but preserve exception names)
        // Skip this for now - exceptions are already preserved above
        // pattern = JAVA_CLASS_PATTERN.matcher(pattern).replaceAll("*");

        // Replace numbers (but preserve exception names)
        pattern = NUMBER_PATTERN.matcher(pattern).replaceAll("*");

        // Restore exception placeholders
        pattern = pattern.replace("__EXCEPTION__", "Exception");

        // Clean up multiple consecutive asterisks
        pattern = pattern.replaceAll("\\*+", "*");

        // Trim whitespace
        pattern = pattern.trim();

        return pattern;
    }

    /**
     * Extract a short pattern for display (limit length).
     *
     * @param message The original log message
     * @param maxLength Maximum length of pattern
     * @return Truncated pattern
     */
    public static String extractShortPattern(String message, int maxLength) {
        String pattern = extractPattern(message);
        if (pattern.length() > maxLength) {
            return pattern.substring(0, maxLength - 3) + "...";
        }
        return pattern;
    }

    /**
     * Extract only the first line of a log message for display purposes.
     * This is useful for pattern summaries where we don't want to show full stack traces.
     *
     * @param message The original log message
     * @return Pattern from first line only
     */
    public static String extractFirstLinePattern(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }

        // Get the first line (before first newline)
        String firstLine = message.split("\\n")[0].trim();

        // Extract pattern from first line only
        String pattern = extractPattern(firstLine);

        // Limit to reasonable display length
        if (pattern.length() > 200) {
            return pattern.substring(0, 197) + "...";
        }

        return pattern;
    }

    /**
     * Check if a pattern is meaningful (not too generic).
     *
     * A pattern is considered meaningful if it has some specific text,
     * not just asterisks and common words.
     *
     * @param pattern The pattern to check
     * @return true if pattern is meaningful
     */
    public static boolean isMeaningfulPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return false;
        }

        // Remove asterisks and whitespace
        String withoutWildcards = pattern.replaceAll("[\\*\\s]+", "");

        // Pattern should have at least 5 meaningful characters
        return withoutWildcards.length() >= 5;
    }
}
