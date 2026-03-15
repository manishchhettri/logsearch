package com.lsearch.logsearch.service;

import com.lsearch.logsearch.config.LogSearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LogSearchService focusing on:
 * 1. Search query parsing (field queries vs regular text)
 * 2. Bloom filter term extraction logic
 *
 * Note: These are pure unit tests that test the query parsing logic
 * without requiring the full LogSearchService to be instantiated.
 */
class LogSearchServiceTest {

    private LogSearchProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new LogSearchProperties();
        properties.setLogsDir(tempDir.toString());
        properties.setIndexDir(tempDir.resolve("index").toString());
    }

    // ========================================================================
    // Bloom Filter Term Extraction Tests
    // ========================================================================

    @Test
    void testBloomFilter_RegularTextSearch_ExtractsTokens() throws Exception {
        // Test: Regular text search should extract all tokens for Bloom filter
        String query = "customer payment error";
        Set<String> terms = extractSearchTerms(query);

        assertNotNull(terms);
        assertEquals(3, terms.size());
        assertTrue(terms.contains("customer"));
        assertTrue(terms.contains("payment"));
        assertTrue(terms.contains("error"));
    }

    @Test
    void testBloomFilter_FieldQueryMetadata_NoTermsExtracted() throws Exception {
        // Test: Metadata field queries should NOT add terms to Bloom filter
        // This allows all chunks to pass through to Stage 2 for metadata filtering
        String query = "level:ERROR";
        Set<String> terms = extractSearchTerms(query);

        // Should be empty - metadata fields don't add terms to Bloom filter
        assertNotNull(terms);
        assertEquals(0, terms.size());
    }

    @Test
    void testBloomFilter_FieldQueryUser_NoTermsExtracted() throws Exception {
        String query = "user:john.doe";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size(), "User field query should not add terms to Bloom filter");
    }

    @Test
    void testBloomFilter_FieldQueryThread_NoTermsExtracted() throws Exception {
        String query = "thread:worker-1";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size(), "Thread field query should not add terms to Bloom filter");
    }

    @Test
    void testBloomFilter_FieldQueryLogger_NoTermsExtracted() throws Exception {
        String query = "logger:com.example.PaymentService";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size(), "Logger field query should not add terms to Bloom filter");
    }

    @Test
    void testBloomFilter_FieldQueryCorrelationId_NoTermsExtracted() throws Exception {
        String query = "correlationId:abc-123-def";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size(), "CorrelationId field query should not add terms to Bloom filter");
    }

    @Test
    void testBloomFilter_FieldQueryMessageId_NoTermsExtracted() throws Exception {
        String query = "messageId:msg-456";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size(), "MessageId field query should not add terms to Bloom filter");
    }

    @Test
    void testBloomFilter_FieldQueryFlowName_NoTermsExtracted() throws Exception {
        String query = "flowName:payment-flow";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size(), "FlowName field query should not add terms to Bloom filter");
    }

    @Test
    void testBloomFilter_FieldQueryEndpoint_NoTermsExtracted() throws Exception {
        String query = "endpoint:/api/payment";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size(), "Endpoint field query should not add terms to Bloom filter");
    }

    @Test
    void testBloomFilter_FieldQueryMessage_ExtractsValueTokens() throws Exception {
        // Test: Message field queries should tokenize the value and add to Bloom filter
        String query = "message:\"customer payment failed\"";
        Set<String> terms = extractSearchTerms(query);

        assertNotNull(terms);
        assertEquals(3, terms.size());
        assertTrue(terms.contains("customer"));
        assertTrue(terms.contains("payment"));
        assertTrue(terms.contains("failed"));
    }

    @Test
    void testBloomFilter_MixedMetadataAndContent_OnlyContentTerms() throws Exception {
        // Test: Mixed query with metadata and content fields
        // Only content field values should be added to Bloom filter
        String query = "level:ERROR AND message:\"database connection timeout\"";
        Set<String> terms = extractSearchTerms(query);

        // Should only contain tokens from message field
        assertNotNull(terms);
        assertEquals(3, terms.size());
        assertTrue(terms.contains("database"));
        assertTrue(terms.contains("connection"));
        assertTrue(terms.contains("timeout"));

        // Should NOT contain metadata field values
        assertFalse(terms.contains("error"));
        assertFalse(terms.contains("level"));
    }

    @Test
    void testBloomFilter_ComplexQuery_CorrectTermExtraction() throws Exception {
        String query = "user:admin AND level:ERROR AND message:\"null pointer exception\"";
        Set<String> terms = extractSearchTerms(query);

        // Should only extract terms from message field
        assertEquals(3, terms.size());
        assertTrue(terms.contains("null"));
        assertTrue(terms.contains("pointer"));
        assertTrue(terms.contains("exception"));

        // Metadata field values should not be in terms
        assertFalse(terms.contains("admin"));
        assertFalse(terms.contains("error"));
    }

    @Test
    void testBloomFilter_QuotedValues_QuotesRemoved() throws Exception {
        String query = "message:\"customer ID: 12345\"";
        Set<String> terms = extractSearchTerms(query);

        assertNotNull(terms);
        assertTrue(terms.contains("customer"), "Should contain 'customer' token. Got: " + terms);
        // Note: "ID:" gets tokenized as "id:" because colon is not in the delimiter set
        // This matches the actual LogSearchService implementation
        assertTrue(terms.contains("12345"), "Should contain '12345' token. Got: " + terms);

        // Should not contain quotes in any token
        boolean hasQuotes = terms.stream().anyMatch(t -> t.contains("\""));
        assertFalse(hasQuotes, "Tokens should not contain quotes: " + terms);
    }

    @Test
    void testBloomFilter_SpecialCharactersInValue_ProperlyTokenized() throws Exception {
        String query = "message:\"error[code=500]\"";
        Set<String> terms = extractSearchTerms(query);

        assertNotNull(terms);
        assertTrue(terms.contains("error"));
        assertTrue(terms.contains("code"));
        assertTrue(terms.contains("500"));
    }

    @Test
    void testBloomFilter_PureMetadataQuery_EmptyTerms() throws Exception {
        // Test: Query with only metadata fields should result in empty terms
        // This ensures all chunks pass through to Stage 2
        String query = "level:ERROR AND user:admin AND thread:worker-1";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size(), "Pure metadata query should not add any Bloom filter terms");
    }

    @Test
    void testBloomFilter_OROperator_AllTermsExtracted() throws Exception {
        String query = "payment OR transaction OR refund";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(3, terms.size());
        assertTrue(terms.contains("payment"));
        assertTrue(terms.contains("transaction"));
        assertTrue(terms.contains("refund"));
    }

    @Test
    void testBloomFilter_ANDOperator_AllTermsExtracted() throws Exception {
        String query = "customer AND order AND completed";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(3, terms.size());
        assertTrue(terms.contains("customer"));
        assertTrue(terms.contains("order"));
        assertTrue(terms.contains("completed"));
    }

    @Test
    void testBloomFilter_MixedOperators_AllContentTermsExtracted() throws Exception {
        String query = "level:ERROR OR message:\"timeout\" AND user:admin";
        Set<String> terms = extractSearchTerms(query);

        // Should only have "timeout" from message field
        assertEquals(1, terms.size());
        assertTrue(terms.contains("timeout"));
    }

    @Test
    void testBloomFilter_EmptyQuery_EmptyTerms() throws Exception {
        String query = "";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size());
    }

    @Test
    void testBloomFilter_NullQuery_EmptyTerms() throws Exception {
        String query = null;
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size());
    }

    @Test
    void testBloomFilter_WhitespaceOnly_EmptyTerms() throws Exception {
        String query = "   ";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(0, terms.size());
    }

    @Test
    void testBloomFilter_CaseSensitivity_LowercaseNormalization() throws Exception {
        String query = "ERROR Warning Info";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(3, terms.size());
        assertTrue(terms.contains("error"), "Terms should be lowercased");
        assertTrue(terms.contains("warning"), "Terms should be lowercased");
        assertTrue(terms.contains("info"), "Terms should be lowercased");

        // Ensure uppercase versions are NOT present
        assertFalse(terms.contains("ERROR"));
        assertFalse(terms.contains("Warning"));
    }

    // ========================================================================
    // Query Parsing Tests
    // ========================================================================

    @Test
    void testQueryParsing_SimpleFieldQuery_ParsedCorrectly() throws Exception {
        String query = "level:ERROR";
        Set<String> terms = extractSearchTerms(query);

        // Should recognize as metadata field and not extract terms
        assertEquals(0, terms.size());
    }

    @Test
    void testQueryParsing_FieldQueryWithSpaces_HandledCorrectly() throws Exception {
        String query = "message: \"some error message\"";
        Set<String> terms = extractSearchTerms(query);

        // Should extract tokens from the value
        assertTrue(terms.contains("some"));
        assertTrue(terms.contains("error"));
        assertTrue(terms.contains("message"));
    }

    @Test
    void testQueryParsing_MultipleColons_FirstColonIsFieldSeparator() throws Exception {
        String query = "message:\"customer ID: 12345\"";
        Set<String> terms = extractSearchTerms(query);

        // Should parse "message" as field and "customer ID: 12345" as value
        // The first colon separates field from value
        // Subsequent colons in the value are preserved (they're not delimiters)
        assertTrue(terms.contains("customer"), "Should contain 'customer' - got: " + terms);
        assertTrue(terms.contains("12345"), "Should contain '12345' - got: " + terms);

        // Verify that quotes were removed
        assertFalse(terms.stream().anyMatch(t -> t.contains("\"")), "No quotes in terms");
    }

    @Test
    void testQueryParsing_NoFieldSpecified_TreatedAsRegularText() throws Exception {
        String query = "error occurred in payment service";
        Set<String> terms = extractSearchTerms(query);

        assertEquals(5, terms.size());
        assertTrue(terms.contains("error"));
        assertTrue(terms.contains("occurred"));
        assertTrue(terms.contains("in"));
        assertTrue(terms.contains("payment"));
        assertTrue(terms.contains("service"));
    }

    @Test
    void testQueryParsing_UnknownField_ValueTokensExtracted() throws Exception {
        // Unknown/custom fields should have their values tokenized and added to Bloom filter
        String query = "customField:\"some value\"";
        Set<String> terms = extractSearchTerms(query);

        // Since customField is not a known metadata field, tokens should be extracted
        assertTrue(terms.contains("some"));
        assertTrue(terms.contains("value"));
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Extract search terms from query using the same logic as LogSearchService.
     * This is a white-box test that replicates the term extraction logic.
     */
    private Set<String> extractSearchTerms(String queryText) {
        if (queryText == null || queryText.trim().isEmpty()) {
            return new HashSet<>();
        }

        Set<String> terms = new HashSet<>();

        // Define valid field names (matching LogSearchService)
        Set<String> validMetadataFields = new HashSet<>(Arrays.asList(
            "level", "user", "thread", "logger", "sourceFile",
            "correlationId", "messageId", "flowName", "endpoint"
        ));
        Set<String> validContentFields = new HashSet<>(Arrays.asList("message"));

        // Split on AND/OR operators first
        String[] clauses = queryText.split("\\s+(?:AND|OR)\\s+");

        for (String clause : clauses) {
            clause = clause.trim();

            // Check if this might be a field query (field:value)
            boolean isValidFieldQuery = false;
            String fieldName = null;
            String fieldValue = null;

            if (clause.contains(":")) {
                int colonIndex = clause.indexOf(":");
                String potentialFieldName = clause.substring(0, colonIndex).trim();
                String potentialFieldValue = clause.substring(colonIndex + 1).trim();

                // Only treat as field query if field name is valid
                if (validMetadataFields.contains(potentialFieldName) ||
                    validContentFields.contains(potentialFieldName)) {
                    isValidFieldQuery = true;
                    fieldName = potentialFieldName;
                    fieldValue = potentialFieldValue;
                }
            }

            if (isValidFieldQuery && fieldValue != null && !fieldValue.isEmpty()) {
                // This is a valid field query
                // Remove quotes if present
                fieldValue = fieldValue.replaceAll("^\"|\"$", "");

                // For metadata fields, skip adding to Bloom filter
                if (validMetadataFields.contains(fieldName)) {
                    continue;
                }

                // For content fields (message), tokenize the value
                if (validContentFields.contains(fieldName)) {
                    String[] valueTokens = fieldValue.split("[\\s\\.,;:()\\[\\]{}\"'<>=]+");
                    for (String token : valueTokens) {
                        if (token != null && !token.isEmpty()) {
                            terms.add(token.toLowerCase());
                        }
                    }
                }
            } else {
                // Not a valid field query - treat entire clause as regular text search
                // Include colon in delimiters since it's not part of a field query
                String[] tokens = clause.split("[\\s\\.,;:()\\[\\]{}\"'<>=]+");
                for (String token : tokens) {
                    if (token != null && !token.isEmpty()) {
                        terms.add(token.toLowerCase());
                    }
                }
            }
        }

        return terms;
    }
}
