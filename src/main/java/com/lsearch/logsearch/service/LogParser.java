package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogParser {

    private static final Logger log = LoggerFactory.getLogger(LogParser.class);

    private final LogSearchProperties properties;
    private final Pattern logPattern;

    public LogParser(LogSearchProperties properties) {
        this.properties = properties;

        // Compile the log line pattern from configuration
        // Default: [timestamp] [user] message
        this.logPattern = Pattern.compile(properties.getLogLinePattern());
        log.info("Initialized LogParser with pattern: {}", properties.getLogLinePattern());
    }

    public LogEntry parseLine(String line, String sourceFile, long lineNumber) {
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
}
