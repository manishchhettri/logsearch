package com.lsearch.logsearch.service;

import com.lsearch.logsearch.model.LogEntry;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a log format pattern template with parsing logic.
 * Each pattern knows how to parse a specific log format.
 */
public class LogFormatPattern {

    private final String name;
    private final Pattern pattern;
    private final DateTimeFormatter dateTimeFormatter;
    private final FieldExtractor extractor;

    public LogFormatPattern(String name, String regex, String dateTimeFormat, FieldExtractor extractor) {
        this.name = name;
        this.pattern = Pattern.compile(regex);
        this.dateTimeFormatter = dateTimeFormat != null ? DateTimeFormatter.ofPattern(dateTimeFormat) : null;
        this.extractor = extractor;
    }

    /**
     * Try to parse a log line with this pattern.
     * Returns null if the line doesn't match this pattern.
     */
    public LogEntry tryParse(String line, String sourceFile, long lineNumber, ZoneId zoneId) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = pattern.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        try {
            return extractor.extract(matcher, dateTimeFormatter, zoneId, sourceFile, lineNumber);
        } catch (Exception e) {
            // Parsing failed, return null
            return null;
        }
    }

    public String getName() {
        return name;
    }

    /**
     * Functional interface for extracting fields from matched groups.
     */
    @FunctionalInterface
    public interface FieldExtractor {
        LogEntry extract(Matcher matcher, DateTimeFormatter formatter, ZoneId zoneId,
                        String sourceFile, long lineNumber) throws Exception;
    }

    /**
     * Common extractors for different log formats
     */
    public static class Extractors {

        /**
         * WebLogic format:
         * [timestamp] [thread] [level] [logger] [] [user:xxx] - message
         * Groups: 1=timestamp, 2=thread, 3=level, 4=logger, 5=user, 6=message
         */
        public static final FieldExtractor WEBLOGIC = (matcher, formatter, zoneId, sourceFile, lineNumber) -> {
            String timestampStr = matcher.group(1);
            LocalDateTime localDateTime = LocalDateTime.parse(timestampStr, formatter);
            ZonedDateTime timestamp = ZonedDateTime.of(localDateTime, zoneId);

            return LogEntry.builder()
                    .timestamp(timestamp)
                    .thread(matcher.group(2).trim())
                    .level(matcher.group(3).trim())
                    .logger(matcher.group(4).trim())
                    .user(matcher.group(5).trim())
                    .message(matcher.group(6))
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber)
                    .build();
        };

        /**
         * WebSphere format:
         * [timestamp] [thread] level logger [user] message
         * Groups: 1=timestamp, 2=thread, 3=level, 4=logger, 5=user, 6=message
         */
        public static final FieldExtractor WEBSPHERE = (matcher, formatter, zoneId, sourceFile, lineNumber) -> {
            String timestampStr = matcher.group(1);
            LocalDateTime localDateTime = LocalDateTime.parse(timestampStr, formatter);
            ZonedDateTime timestamp = ZonedDateTime.of(localDateTime, zoneId);

            return LogEntry.builder()
                    .timestamp(timestamp)
                    .thread(matcher.group(2).trim())
                    .level(matcher.group(3).trim())
                    .logger(matcher.group(4).trim())
                    .user(matcher.group(5).trim())
                    .message(matcher.group(6))
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber)
                    .build();
        };

        /**
         * Log4j/Tomcat format:
         * timestamp [thread] level logger - message
         * Groups: 1=timestamp, 2=thread, 3=level, 4=logger, 5=message
         */
        public static final FieldExtractor LOG4J = (matcher, formatter, zoneId, sourceFile, lineNumber) -> {
            String timestampStr = matcher.group(1);
            LocalDateTime localDateTime = LocalDateTime.parse(timestampStr, formatter);
            ZonedDateTime timestamp = ZonedDateTime.of(localDateTime, zoneId);

            return LogEntry.builder()
                    .timestamp(timestamp)
                    .thread(matcher.group(2).trim())
                    .level(matcher.group(3).trim())
                    .logger(matcher.group(4).trim())
                    .message(matcher.group(5))
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber)
                    .build();
        };

        /**
         * Simple format:
         * [timestamp] [user] message
         * Groups: 1=timestamp, 2=user, 3=message
         */
        public static final FieldExtractor SIMPLE = (matcher, formatter, zoneId, sourceFile, lineNumber) -> {
            String timestampStr = matcher.group(1);
            LocalDateTime localDateTime = LocalDateTime.parse(timestampStr, formatter);
            ZonedDateTime timestamp = ZonedDateTime.of(localDateTime, zoneId);

            return LogEntry.builder()
                    .timestamp(timestamp)
                    .user(matcher.group(2).trim())
                    .message(matcher.group(3))
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber)
                    .build();
        };

        /**
         * ISO timestamp with level:
         * timestamp level message
         * Groups: 1=timestamp, 2=level, 3=message
         */
        public static final FieldExtractor ISO_WITH_LEVEL = (matcher, formatter, zoneId, sourceFile, lineNumber) -> {
            String timestampStr = matcher.group(1);
            ZonedDateTime timestamp = ZonedDateTime.parse(timestampStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            return LogEntry.builder()
                    .timestamp(timestamp)
                    .level(matcher.group(2).trim())
                    .message(matcher.group(3))
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber)
                    .build();
        };
    }
}
