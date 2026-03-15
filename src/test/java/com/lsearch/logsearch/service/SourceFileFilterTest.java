package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that sourceFile field queries work correctly in Lucene search.
 * This tests the fix for sourceFile:server-20260312.log filter not working.
 */
class SourceFileFilterTest {

    private LuceneIndexService indexService;
    private LogSearchProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new LogSearchProperties();
        properties.setLogsDir(tempDir.toString());
        properties.setIndexDir(tempDir.resolve("index").toString());
        properties.setTimezone("Pacific/Auckland");  // Set timezone for test

        indexService = new LuceneIndexService(properties);
    }

    @Test
    void testSourceFileFilter_FindsMatchingEntries() throws IOException, ParseException, InterruptedException {
        // Generate chunkId once to avoid hour rollover race condition
        ZonedDateTime now = ZonedDateTime.now();
        String chunkId = "default::" + now.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "::chunk-" +
            String.format("%02d", now.getHour());

        // Create log entries from different source files with consistent timestamp
        LogEntry entry1 = createLogEntryWithChunkId("server-20260312.log", "Error in payment service", now, chunkId);
        LogEntry entry2 = createLogEntryWithChunkId("server-20260313.log", "Warning in order service", now, chunkId);
        LogEntry entry3 = createLogEntryWithChunkId("server-20260312.log", "Info message", now, chunkId);

        // Index the entries
        indexService.indexLogEntry(entry1);
        indexService.indexLogEntry(entry2);
        indexService.indexLogEntry(entry3);

        // Give indexing time to complete
        Thread.sleep(100);

        // Close writers to commit
        indexService.closeAll();

        // Small delay for index to be fully written
        Thread.sleep(100);

        // Search for entries from specific source file
        List<LogEntry> results = indexService.searchChunk(chunkId, "sourceFile:server-20260312.log", 100);

        // Should find 2 entries from server-20260312.log
        assertEquals(2, results.size(), "Should find exactly 2 entries from server-20260312.log");

        // Verify all results are from the correct source file
        for (LogEntry result : results) {
            assertEquals("server-20260312.log", result.getSourceFile(),
                "All results should be from server-20260312.log");
        }
    }

    @Test
    void testSourceFileFilter_NoMatchReturnsEmpty() throws IOException, ParseException, InterruptedException {
        // Generate chunkId once to avoid hour rollover race condition
        ZonedDateTime now = ZonedDateTime.now();
        String chunkId = "default::" + now.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "::chunk-" +
            String.format("%02d", now.getHour());

        // Create log entry with consistent timestamp
        LogEntry entry = createLogEntryWithChunkId("server-20260312.log", "Test message", now, chunkId);

        // Index the entry
        indexService.indexLogEntry(entry);
        Thread.sleep(100);
        indexService.closeAll();
        Thread.sleep(100);

        // Search for non-existent source file
        List<LogEntry> results = indexService.searchChunk(chunkId, "sourceFile:nonexistent.log", 100);

        // Should return empty results
        assertEquals(0, results.size(), "Should find no entries for non-existent source file");
    }

    @Test
    void testSourceFileFilter_WithOtherFilters() throws IOException, ParseException, InterruptedException {
        // Generate chunkId once to avoid hour rollover race condition
        ZonedDateTime now = ZonedDateTime.now();
        String chunkId = "default::" + now.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "::chunk-" +
            String.format("%02d", now.getHour());

        // Create log entries with different levels and consistent timestamp
        LogEntry entry1 = createLogEntryWithChunkId("server-20260312.log", "ERROR", "Payment failed", now, chunkId);
        LogEntry entry2 = createLogEntryWithChunkId("server-20260312.log", "INFO", "Payment succeeded", now, chunkId);
        LogEntry entry3 = createLogEntryWithChunkId("server-20260313.log", "ERROR", "Order failed", now, chunkId);

        // Index entries
        indexService.indexLogEntry(entry1);
        indexService.indexLogEntry(entry2);
        indexService.indexLogEntry(entry3);
        Thread.sleep(100);
        indexService.closeAll();
        Thread.sleep(100);

        // Search with combined sourceFile and level filter
        List<LogEntry> results = indexService.searchChunk(chunkId,
            "sourceFile:server-20260312.log AND level:ERROR", 100);

        // Should find only entry1 (server-20260312.log with ERROR level)
        assertEquals(1, results.size(), "Should find 1 ERROR entry from server-20260312.log");
        assertEquals("server-20260312.log", results.get(0).getSourceFile());
        assertEquals("ERROR", results.get(0).getLevel());
    }

    // Helper methods

    private LogEntry createLogEntry(String sourceFile, String message) {
        return createLogEntry(sourceFile, "INFO", message);
    }

    private LogEntry createLogEntry(String sourceFile, String level, String message) {
        ZonedDateTime now = ZonedDateTime.now();
        LogEntry entry = new LogEntry();
        entry.setTimestamp(now);
        entry.setLevel(level);
        entry.setMessage(message);
        entry.setThread("main");
        entry.setLogger("com.test.Logger");
        entry.setUser("testuser");
        entry.setSourceFile(sourceFile);
        entry.setLineNumber(1);

        // Set chunk ID based on current time
        String chunkId = "default::" + now.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
            "::chunk-" + String.format("%02d", now.getHour());
        entry.setChunkId(chunkId);

        return entry;
    }

    private LogEntry createLogEntryWithChunkId(String sourceFile, String message, ZonedDateTime timestamp, String chunkId) {
        return createLogEntryWithChunkId(sourceFile, "INFO", message, timestamp, chunkId);
    }

    private LogEntry createLogEntryWithChunkId(String sourceFile, String level, String message, ZonedDateTime timestamp, String chunkId) {
        LogEntry entry = new LogEntry();
        entry.setTimestamp(timestamp);
        entry.setLevel(level);
        entry.setMessage(message);
        entry.setThread("main");
        entry.setLogger("com.test.Logger");
        entry.setUser("testuser");
        entry.setSourceFile(sourceFile);
        entry.setLineNumber(1);
        entry.setChunkId(chunkId);

        return entry;
    }
}
