package com.lsearch.logsearch.service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pattern.PatternTokenizer;

import java.util.regex.Pattern;

/**
 * Custom analyzer optimized for Java code and stack traces.
 *
 * This analyzer tokenizes on dots, parentheses, colons, and other code delimiters
 * to enable searching for individual class names, method names, and package components.
 *
 * Examples:
 * - "com.example.DataValidator" → ["com", "example", "datavalidator"]
 * - "at DataValidator.validate(DataValidator.java:123)" → ["at", "datavalidator", "validate", "java", "123"]
 */
public class CodeAnalyzer extends Analyzer {

    /**
     * Pattern that splits on:
     * - Dots (.)
     * - Parentheses ( and )
     * - Colons (:)
     * - At-signs (@)
     * - Dollar signs ($)
     * - Square brackets [ and ]
     * - Angle brackets < and >
     * - Semicolons (;)
     * - Commas (,)
     * - Forward slashes (/)
     * - Backslashes (\)
     * - Whitespace
     *
     * This preserves words while splitting Java package names, class names, method signatures, etc.
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\s.():@$\\[\\]<>;,/\\\\]+");

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        // Use PatternTokenizer to split on our custom pattern
        Tokenizer tokenizer = new PatternTokenizer(TOKEN_PATTERN, -1);

        // Apply lowercase filter
        TokenStream tokenStream = new LowerCaseFilter(tokenizer);

        // Apply English stop words filter (removes common words like "the", "a", "at", etc.)
        // Note: "at" is a stop word, but in stack traces it's often not meaningful for search
        tokenStream = new StopFilter(tokenStream, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);

        return new TokenStreamComponents(tokenizer, tokenStream);
    }
}
