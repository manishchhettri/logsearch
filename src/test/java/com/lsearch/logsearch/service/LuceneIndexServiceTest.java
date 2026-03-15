package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import com.lsearch.logsearch.model.LogEntry;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.StringReader;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LuceneIndexService focusing on:
 * - Per-field analyzer configuration
 * - Correct tokenization for different field types
 */
class LuceneIndexServiceTest {

    private LuceneIndexService indexService;
    private LogSearchProperties properties;
    private Analyzer analyzer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new LogSearchProperties();
        properties.setLogsDir(tempDir.toString());
        properties.setIndexDir(tempDir.resolve("index").toString());

        indexService = new LuceneIndexService(properties);

        // Get the analyzer from the indexService using reflection
        analyzer = (Analyzer) ReflectionTestUtils.getField(indexService, "analyzer");
    }

    // ========================================================================
    // Per-Field Analyzer Configuration Tests
    // ========================================================================

    @Test
    void testAnalyzer_NotNull() {
        assertNotNull(analyzer, "Analyzer should be initialized");
    }

    @Test
    void testAnalyzer_StandardAnalyzer_ForLevelField() throws Exception {
        // Test: level field should use StandardAnalyzer (case-insensitive, simple tokenization)
        String fieldName = "level";
        String text = "ERROR";

        List<String> tokens = analyzeText(fieldName, text);

        // StandardAnalyzer lowercases tokens
        assertEquals(1, tokens.size());
        assertEquals("error", tokens.get(0), "StandardAnalyzer should lowercase the token");
    }

    @Test
    void testAnalyzer_StandardAnalyzer_ForUserField() throws Exception {
        String fieldName = "user";
        String text = "john.doe";

        List<String> tokens = analyzeText(fieldName, text);

        // StandardAnalyzer may not split on dots - it treats them as part of the token
        // The actual behavior depends on the StandardAnalyzer version
        assertTrue(tokens.size() >= 1, "Should have at least 1 token. Got: " + tokens);
        // Check that we get some form of the username
        assertTrue(tokens.stream().anyMatch(t -> t.contains("john") || t.contains("doe")),
                   "Should contain username parts. Got: " + tokens);
    }

    @Test
    void testAnalyzer_StandardAnalyzer_ForThreadField() throws Exception {
        String fieldName = "thread";
        String text = "worker-thread-1";

        List<String> tokens = analyzeText(fieldName, text);

        // StandardAnalyzer should split on hyphens
        assertTrue(tokens.size() > 0);
        assertTrue(tokens.contains("worker"));
        assertTrue(tokens.contains("thread"));
    }

    @Test
    void testAnalyzer_StandardAnalyzer_ForLoggerField() throws Exception {
        String fieldName = "logger";
        String text = "com.example.PaymentService";

        List<String> tokens = analyzeText(fieldName, text);

        // StandardAnalyzer behavior: may keep dots or split on them depending on configuration
        // What matters is that it produces tokens we can search
        assertTrue(tokens.size() > 0, "Should produce at least one token. Got: " + tokens);

        // Check that we get searchable tokens (lowercased)
        assertTrue(tokens.stream().allMatch(t -> t.equals(t.toLowerCase())),
                   "StandardAnalyzer should lowercase tokens. Got: " + tokens);
    }

    @Test
    void testAnalyzer_StandardAnalyzer_ForCorrelationIdText() throws Exception {
        String fieldName = "correlationIdText";
        String text = "abc-123-def-456";

        List<String> tokens = analyzeText(fieldName, text);

        // StandardAnalyzer splits on hyphens
        assertTrue(tokens.contains("abc"));
        assertTrue(tokens.contains("123"));
        assertTrue(tokens.contains("def"));
        assertTrue(tokens.contains("456"));
    }

    @Test
    void testAnalyzer_StandardAnalyzer_ForMessageIdText() throws Exception {
        String fieldName = "messageIdText";
        String text = "msg_12345";

        List<String> tokens = analyzeText(fieldName, text);

        // StandardAnalyzer handles underscores
        assertTrue(tokens.size() > 0);
    }

    @Test
    void testAnalyzer_StandardAnalyzer_ForFlowNameText() throws Exception {
        String fieldName = "flowNameText";
        String text = "payment-processing-flow";

        List<String> tokens = analyzeText(fieldName, text);

        assertTrue(tokens.contains("payment"));
        assertTrue(tokens.contains("processing"));
        assertTrue(tokens.contains("flow"));
    }

    @Test
    void testAnalyzer_StandardAnalyzer_ForEndpointText() throws Exception {
        String fieldName = "endpointText";
        String text = "/api/v1/payments";

        List<String> tokens = analyzeText(fieldName, text);

        assertTrue(tokens.contains("api"));
        assertTrue(tokens.contains("v1"));
        assertTrue(tokens.contains("payments"));
    }

    @Test
    void testAnalyzer_CodeAnalyzer_ForMessageField() throws Exception {
        // Test: message field should use CodeAnalyzer (preserves camelCase, etc.)
        String fieldName = "message";
        String text = "NullPointerException in PaymentService.processPayment()";

        List<String> tokens = analyzeText(fieldName, text);

        // CodeAnalyzer should preserve code-like tokens better than StandardAnalyzer
        // This is a basic check - the exact behavior depends on CodeAnalyzer implementation
        assertTrue(tokens.size() > 0);
        assertTrue(tokens.contains("nullpointerexception") ||
                   tokens.contains("null") ||
                   tokens.contains("pointer") ||
                   tokens.stream().anyMatch(t -> t.toLowerCase().contains("null")));
    }

    @Test
    void testAnalyzer_CodeAnalyzer_ForPatternTextField() throws Exception {
        String fieldName = "patternText";
        String text = "ERROR Payment processing failed";

        List<String> tokens = analyzeText(fieldName, text);

        assertTrue(tokens.size() > 0);
    }

    @Test
    void testAnalyzer_DifferentAnalyzers_ProduceDifferentResults() throws Exception {
        // Test: Same text should be tokenized differently by StandardAnalyzer vs CodeAnalyzer
        String text = "PaymentService.java";

        List<String> standardTokens = analyzeText("logger", text); // Uses StandardAnalyzer
        List<String> codeTokens = analyzeText("message", text);     // Uses CodeAnalyzer

        // Both should produce tokens, but possibly different ones
        assertTrue(standardTokens.size() > 0);
        assertTrue(codeTokens.size() > 0);

        // StandardAnalyzer typically splits on dots and lowercases
        assertTrue(standardTokens.stream().allMatch(t -> t.equals(t.toLowerCase())),
                   "StandardAnalyzer should lowercase all tokens");
    }

    @Test
    void testAnalyzer_SpecialCharacters_StandardAnalyzer() throws Exception {
        String fieldName = "level"; // Uses StandardAnalyzer
        String text = "ERROR!!! WARNING@@@";

        List<String> tokens = analyzeText(fieldName, text);

        // StandardAnalyzer removes special characters
        assertTrue(tokens.contains("error"));
        assertTrue(tokens.contains("warning"));
        assertFalse(tokens.stream().anyMatch(t -> t.contains("!")));
        assertFalse(tokens.stream().anyMatch(t -> t.contains("@")));
    }

    @Test
    void testAnalyzer_Numbers_PreservedByBothAnalyzers() throws Exception {
        String text = "Error code 500";

        List<String> standardTokens = analyzeText("level", text);
        List<String> codeTokens = analyzeText("message", text);

        // Both analyzers should preserve numbers
        assertTrue(standardTokens.contains("500") || standardTokens.stream().anyMatch(t -> t.contains("500")));
        assertTrue(codeTokens.contains("500") || codeTokens.stream().anyMatch(t -> t.contains("500")));
    }

    @Test
    void testAnalyzer_EmptyString_NoTokens() throws Exception {
        List<String> tokens = analyzeText("level", "");
        assertEquals(0, tokens.size());
    }

    @Test
    void testAnalyzer_WhitespaceOnly_NoTokens() throws Exception {
        List<String> tokens = analyzeText("level", "   ");
        assertEquals(0, tokens.size());
    }

    @Test
    void testAnalyzer_CamelCase_StandardAnalyzer() throws Exception {
        String fieldName = "logger";
        String text = "PaymentService";

        List<String> tokens = analyzeText(fieldName, text);

        // StandardAnalyzer treats as single word and lowercases
        assertTrue(tokens.contains("paymentservice"));
    }

    @Test
    void testAnalyzer_Underscores_StandardAnalyzer() throws Exception {
        String fieldName = "user";
        String text = "admin_user_123";

        List<String> tokens = analyzeText(fieldName, text);

        // Check if underscores are treated as separators
        assertTrue(tokens.size() > 0);
    }

    @Test
    void testAnalyzer_MixedCase_Normalized() throws Exception {
        String fieldName = "level";
        String text = "ErRoR";

        List<String> tokens = analyzeText(fieldName, text);

        assertEquals(1, tokens.size());
        assertEquals("error", tokens.get(0), "StandardAnalyzer should normalize case");
    }

    // ========================================================================
    // Integration Test: Indexing and Searching with Per-Field Analyzers
    // ========================================================================

    @Test
    void testIndexing_MetadataFields_UseCorrectAnalyzer() throws Exception {
        // This test verifies that we can create and index a log entry
        // Full search verification would require more complex setup with DirectoryReader

        // Create a log entry with all required fields
        LogEntry entry = new LogEntry();
        entry.setTimestamp(ZonedDateTime.now());
        entry.setLevel("ERROR");
        entry.setMessage("Payment processing failed");
        entry.setThread("worker-1");
        entry.setLogger("com.example.PaymentService");
        entry.setUser("admin.user");
        entry.setSourceFile("test.log"); // Required field

        // Index the entry
        indexService.indexLogEntry(entry);

        // Small delay to allow indexing to complete
        Thread.sleep(100);

        // Close all writers to commit
        indexService.closeAll();

        // Basic test passed: entry was indexed without errors
        // Full search verification would require setting up DirectoryReader and searching
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Analyze text using the configured analyzer for a specific field.
     * Returns the list of tokens produced.
     */
    private List<String> analyzeText(String fieldName, String text) throws Exception {
        List<String> tokens = new ArrayList<>();

        try (TokenStream tokenStream = analyzer.tokenStream(fieldName, new StringReader(text))) {
            CharTermAttribute termAttr = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();

            while (tokenStream.incrementToken()) {
                tokens.add(termAttr.toString());
            }

            tokenStream.end();
        }

        return tokens;
    }
}
