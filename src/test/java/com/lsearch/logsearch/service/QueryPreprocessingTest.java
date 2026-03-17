package com.lsearch.logsearch.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for query preprocessing logic to ensure no PhraseQuery errors
 * on StringField fields like sourceFile.
 */
@SpringBootTest
public class QueryPreprocessingTest {

    @Autowired
    private LogSearchService logSearchService;

    @Test
    public void testSimpleQuotedPhrase() throws Exception {
        String input = "\"Handler dispatch failed\"";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // Should not add any field queries
        assertFalse(result.contains("sourceFile:"));
    }

    @Test
    public void testQuotedPhraseWithSpaces() throws Exception {
        String input = "\"Handler dispatch failed nested exception is java.lang.Exception Java heap space\"";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // Should not add any field queries
        assertFalse(result.contains("sourceFile:"));
    }

    @Test
    public void testSourceFileFieldQuery() throws Exception {
        String input = "sourceFile:/Users/manish/Java Projects/log_search/logs/server-20260308.log";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // Should NOT quote sourceFile values (StringField - no position data)
        assertFalse(result.contains("sourceFile:\""));
    }

    @Test
    public void testSourceFileFieldQueryWithSpaces() throws Exception {
        String input = "sourceFile:users manish java projects log search logs server 20260308 log";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // Should NOT quote sourceFile values (StringField)
        assertFalse(result.contains("sourceFile:\""));
        assertTrue(result.contains("sourceFile:"));
    }

    @Test
    public void testTextFieldQueryWithSpaces() throws Exception {
        String input = "user:john.doe@example.com";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // TextField fields CAN be quoted
        // May or may not add quotes depending on logic
    }

    @Test
    public void testPackageNameWithoutQuotes() throws Exception {
        String input = "framework.core";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // Auto-quoting is disabled, should remain unchanged
        assertEquals("framework.core", result);
    }

    @Test
    public void testPackageNameWithQuotes() throws Exception {
        String input = "\"framework.core\"";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // Should remain quoted
        assertTrue(result.contains("\"framework.core\""));
    }

    @Test
    public void testBooleanQuery() throws Exception {
        String input = "\"framework.core\" AND ERROR";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // Should preserve boolean operators
        assertTrue(result.contains("AND"));
    }

    @Test
    public void testFieldQueryWithDots() throws Exception {
        String input = "logger:com.example.service.PaymentService";
        String result = callPreprocessQuery(input);

        System.out.println("Input:  " + input);
        System.out.println("Output: " + result);

        // logger is TextField, may get quoted
        assertTrue(result.contains("logger:"));
    }

    /**
     * Helper method to call the private preprocessQuery method via reflection
     */
    private String callPreprocessQuery(String query) throws Exception {
        java.lang.reflect.Method method = LogSearchService.class.getDeclaredMethod("preprocessQuery", String.class);
        method.setAccessible(true);
        return (String) method.invoke(logSearchService, query);
    }
}
