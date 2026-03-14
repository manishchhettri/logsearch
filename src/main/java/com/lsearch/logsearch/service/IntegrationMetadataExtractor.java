package com.lsearch.logsearch.service;

import com.lsearch.logsearch.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts integration platform metadata from log messages.
 * Supports IBM Integration Bus (IIB), MQ, ESB, and other enterprise integration platforms.
 */
@Service
public class IntegrationMetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(IntegrationMetadataExtractor.class);

    // Correlation ID patterns (various formats)
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile(
            "(?:correlationId|correlationID|correlation_id|corrId|txnId|transactionId)[=:\\s]+([a-zA-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Message ID patterns
    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile(
            "(?:messageId|msgId|message_id)[=:\\s]+([a-zA-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Flow name patterns (IIB/ESB specific)
    private static final Pattern FLOW_NAME_PATTERN = Pattern.compile(
            "(?:flow|flowName|message flow)[=:\\s]+([a-zA-Z0-9_.-]+)",
            Pattern.CASE_INSENSITIVE
    );

    // API endpoint patterns
    private static final Pattern ENDPOINT_PATTERN = Pattern.compile(
            "((?:https?://[^\\s]+)|(?:/api/[a-zA-Z0-9/_-]+)|(?:/[a-zA-Z0-9/_-]+/v\\d+/[a-zA-Z0-9/_-]+))"
    );

    // IBM Integration Bus (IIB) message pattern
    private static final Pattern IIB_PATTERN = Pattern.compile("BIP\\d+[A-Z]:");

    // IBM MQ message pattern
    private static final Pattern MQ_PATTERN = Pattern.compile("AMQ\\d+[A-Z]:");

    // ESB/WebSphere patterns
    private static final Pattern ESB_PATTERN = Pattern.compile("(?:WSWS|CWSWS)\\d+[A-Z]:");

    /**
     * Enriches log entry with integration platform metadata
     */
    public void enrichLogEntry(LogEntry entry) {
        if (entry == null || entry.getMessage() == null) {
            return;
        }

        String message = entry.getMessage();

        // Detect integration platform
        String platform = detectIntegrationPlatform(message);
        if (platform != null) {
            entry.setIntegrationPlatform(platform);
        }

        // Extract correlation ID
        String correlationId = extractFirst(CORRELATION_ID_PATTERN, message);
        if (correlationId != null) {
            entry.setCorrelationId(correlationId);
        }

        // Extract message ID
        String messageId = extractFirst(MESSAGE_ID_PATTERN, message);
        if (messageId != null) {
            entry.setMessageId(messageId);
        }

        // Extract flow name
        String flowName = extractFirst(FLOW_NAME_PATTERN, message);
        if (flowName != null) {
            entry.setFlowName(flowName);
        }

        // Extract endpoint
        String endpoint = extractFirst(ENDPOINT_PATTERN, message);
        if (endpoint != null) {
            entry.setEndpoint(endpoint);
        }
    }

    /**
     * Detects integration platform type from log message
     */
    private String detectIntegrationPlatform(String message) {
        if (IIB_PATTERN.matcher(message).find()) {
            return "IIB";
        }
        if (MQ_PATTERN.matcher(message).find()) {
            return "MQ";
        }
        if (ESB_PATTERN.matcher(message).find()) {
            return "ESB";
        }
        // Check for generic integration keywords
        if (message.toLowerCase().contains("integration bus") ||
            message.toLowerCase().contains("message broker")) {
            return "INTEGRATION";
        }
        return null;
    }

    /**
     * Extracts first match from pattern
     */
    private String extractFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Check if message contains correlation tracking
     */
    public boolean hasCorrelationId(String message) {
        return CORRELATION_ID_PATTERN.matcher(message).find();
    }

    /**
     * Check if message is from an integration platform
     */
    public boolean isIntegrationPlatformLog(String message) {
        return detectIntegrationPlatform(message) != null;
    }
}
