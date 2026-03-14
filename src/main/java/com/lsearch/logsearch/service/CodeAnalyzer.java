package com.lsearch.logsearch.service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Custom analyzer optimized for Java code and stack traces with camelCase splitting.
 *
 * This analyzer tokenizes on dots, parentheses, colons, and other code delimiters,
 * AND splits camelCase words to enable intuitive searching.
 *
 * Examples:
 * - "com.example.DataValidator" → ["com", "example", "data", "validator"]
 * - "NullPointerException" → ["null", "pointer", "exception"]
 * - "at DataValidator.validate(DataValidator.java:123)" → ["data", "validator", "validate", "java", "123"]
 *
 * This allows searches like:
 * - "NullPointer" to match "NullPointerException"
 * - "Pointer" to match "NullPointerException"
 * - "DataValidator" or just "Validator" to match the class
 */
public class CodeAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        // Use StandardTokenizer as the base (handles basic whitespace and punctuation)
        Tokenizer tokenizer = new StandardTokenizer();

        // WordDelimiterGraphFilter configuration:
        // GENERATE_WORD_PARTS: Split camelCase (NullPointerException → Null, Pointer, Exception)
        // GENERATE_NUMBER_PARTS: Split numbers from letters (test123 → test, 123)
        // SPLIT_ON_CASE_CHANGE: Split when case changes (camelCase boundaries)
        // SPLIT_ON_NUMERICS: Split letters from numbers
        // STEM_ENGLISH_POSSESSIVE: Remove 's from words
        int flags = WordDelimiterGraphFilter.GENERATE_WORD_PARTS |
                    WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS |
                    WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE |
                    WordDelimiterGraphFilter.SPLIT_ON_NUMERICS |
                    WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE;

        TokenStream tokenStream = new WordDelimiterGraphFilter(tokenizer, flags, null);

        // Apply lowercase filter
        tokenStream = new LowerCaseFilter(tokenStream);

        // Apply English stop words filter (removes common words like "the", "a", "is", etc.)
        tokenStream = new StopFilter(tokenStream, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);

        return new TokenStreamComponents(tokenizer, tokenStream);
    }
}
