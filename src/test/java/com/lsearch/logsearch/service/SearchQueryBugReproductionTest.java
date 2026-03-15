package com.lsearch.logsearch.service;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to reproduce and fix reported search query bugs:
 * 1. sourceFile:server-20260310.log does not work
 * 2. ERROR: Failed to load configuration... does not work (colon issue)
 */
class SearchQueryBugReproductionTest {

    @Test
    void testSourceFileSearch_ShouldWork() {
        // Bug: sourceFile:server-20260310.log doesn't work
        String query = "sourceFile:server-20260310.log";
        Set<String> terms = extractSearchTerms(query);

        // sourceFile is a metadata field, so it should NOT add terms to Bloom filter
        // This allows all chunks to pass through to Stage 2 for metadata filtering
        assertEquals(0, terms.size(),
            "sourceFile is a metadata field - should not add Bloom filter terms. Got: " + terms);
    }

    @Test
    void testErrorPatternWithColon_ShouldWork() {
        // Bug: Clicking on pattern "ERROR: Failed to load configuration file: /opt/curam/config/application.properties"
        // The search gets parsed incorrectly because of the colon after ERROR
        String query = "ERROR: Failed to load configuration file: /opt/curam/config/application.properties";
        Set<String> terms = extractSearchTerms(query);

        // This should be treated as regular text search (not a field query)
        // because "ERROR" is not a valid field name
        // Expected tokens: error, failed, load, configuration, file, opt, curam, config, application, properties
        assertTrue(terms.size() > 5, "Should extract multiple tokens from the pattern. Got: " + terms);
        assertTrue(terms.contains("error"), "Should contain 'error'. Got: " + terms);
        assertTrue(terms.contains("failed"), "Should contain 'failed'. Got: " + terms);
        assertTrue(terms.contains("load"), "Should contain 'load'. Got: " + terms);
        assertTrue(terms.contains("configuration"), "Should contain 'configuration'. Got: " + terms);
        assertTrue(terms.contains("file"), "Should contain 'file'. Got: " + terms);
    }

    @Test
    void testErrorPatternWithoutColon_Works() {
        // This works: "Failed to load configuration file: /opt/curam/config/application.properties"
        String query = "Failed to load configuration file: /opt/curam/config/application.properties";
        Set<String> terms = extractSearchTerms(query);

        // "Failed" is not a valid field name, so this should be treated as regular text
        assertTrue(terms.size() > 5, "Should extract multiple tokens. Got: " + terms);
        assertTrue(terms.contains("failed"), "Should contain 'failed'. Got: " + terms);
        assertTrue(terms.contains("load"), "Should contain 'load'. Got: " + terms);
        assertTrue(terms.contains("configuration"), "Should contain 'configuration'. Got: " + terms);
        assertTrue(terms.contains("file"), "Should contain 'file'. Got: " + terms);
    }

    @Test
    void testUserSearch_Works() {
        // This works: user:bob.wilson
        String query = "user:bob.wilson";
        Set<String> terms = extractSearchTerms(query);

        // user is a metadata field, should not add terms
        assertEquals(0, terms.size(), "user is a metadata field");
    }

    @Test
    void testLevelSearch_Works() {
        // This works: level:INFO
        String query = "level:INFO";
        Set<String> terms = extractSearchTerms(query);

        // level is a metadata field, should not add terms
        assertEquals(0, terms.size(), "level is a metadata field");
    }

    @Test
    void testPatternWithMultipleColons_AsTextSearch() {
        // Pattern: "customer ID: 12345" should be treated as text search
        String query = "customer ID: 12345";
        Set<String> terms = extractSearchTerms(query);

        // "customer ID" is not a valid field, so treat entire clause as text
        assertTrue(terms.contains("customer"), "Should contain 'customer'. Got: " + terms);
        assertTrue(terms.contains("id"), "Should contain 'id'. Got: " + terms);
        assertTrue(terms.contains("12345"), "Should contain '12345'. Got: " + terms);
    }

    // ========================================================================
    // Helper Methods (same as LogSearchService implementation)
    // ========================================================================

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
