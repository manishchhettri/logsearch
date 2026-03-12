package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogParser {

    private static final Logger log = LoggerFactory.getLogger(LogParser.class);

    private final LogSearchProperties properties;

    public LogParser(LogSearchProperties properties) {
        this.properties = properties;
    }

    // Pattern to match: [timestamp] [user] message
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*(.*)$"
    );

    public LogEntry parseLine(String line, String sourceFile, long lineNumber) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = LOG_PATTERN.matcher(line);
        if (!matcher.matches()) {
            log.debug("Line {} in {} does not match expected pattern", lineNumber, sourceFile);
            return null;
        }

        try {
            String timestampStr = matcher.group(1);
            String user = matcher.group(2);
            String message = matcher.group(3);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(properties.getLogDatetimeFormat());
            ZonedDateTime timestamp = ZonedDateTime.parse(timestampStr, formatter);

            return LogEntry.builder()
                    .timestamp(timestamp)
                    .user(user)
                    .message(message)
                    .sourceFile(sourceFile)
                    .lineNumber(lineNumber)
                    .build();

        } catch (DateTimeParseException e) {
            log.warn("Failed to parse timestamp in line {} of {}: {}", lineNumber, sourceFile, e.getMessage());
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
