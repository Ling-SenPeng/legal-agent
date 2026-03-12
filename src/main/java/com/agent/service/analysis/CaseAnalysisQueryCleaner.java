package com.agent.service.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Strips analysis framing noise from CASE_ANALYSIS queries.
 *
 * Analysis framing phrases like "based on these facts", "do I have",
 * "how strong is my claim" are useful for routing but harmful for retrieval.
 *
 * This cleaner removes such phrases to produce retrieval-optimized queries.
 *
 * V1 Implementation: Rule-based phrase removal, deterministic, no NLP.
 */
@Service
public class CaseAnalysisQueryCleaner {
    private static final Logger logger = LoggerFactory.getLogger(CaseAnalysisQueryCleaner.class);

    /**
     * List of analysis framing phrases to remove from queries.
     * These phrases are useful for routing intent but add noise to retrieval.
     */
    private static final List<String> ANALYSIS_NOISE_PHRASES = Arrays.asList(
        // Fact-presentation frames
        "based on these facts",
        "given these facts",
        "considering these facts",
        "under these facts",
        "with these facts",
        "these facts show",
        "from these facts",

        // Claim strength questions
        "do i have a",
        "do i have",
        "do i really have",
        "would i have a",
        "could i have a",
        "might i have a",
        "how strong is my",
        "how strong is my case",
        "how strong is my claim",
        "how strong is my position",
        "is my position strong",
        "is my claim strong",

        // Analysis request frames
        "analyze my",
        "analyze this",
        "what would happen if",
        "what is the likely outcome",
        "what is my best argument",
        "what are my best arguments",
        "evaluate my",
        "evaluate my claim",
        "assess my",
        "what would be the",

        // Legal analysis questions
        "how would this be analyzed",
        "how would a court analyze",
        "what does the law say about",
        "under the law",
        "legally speaking",
        "in legal terms",

        // Other common frames
        "based on",
        "given that",
        "assuming that",
        "if we consider"
    );

    /**
     * Strip analysis noise phrases from a query.
     *
     * Process:
     * 1. Convert to lowercase for matching
     * 2. Remove each known analysis phrase (as complete substrings)
     * 3. Normalize whitespace (trim, collapse multiple spaces)
     * 4. Return cleaned query
     *
     * @param query The original user query
     * @return Query with analysis framing removed, ready for retrieval
     */
    public String stripAnalysisNoise(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }

        String cleaned = query.toLowerCase();

        // Remove each noise phrase
        for (String noisePhrase : ANALYSIS_NOISE_PHRASES) {
            cleaned = cleaned.replace(noisePhrase, " ");
        }

        // Normalize whitespace: trim and collapse multiple spaces
        cleaned = cleaned.trim().replaceAll("\\s+", " ");

        logger.debug("[CaseAnalysisQueryCleaner] Original: '{}' → Cleaned: '{}'",
            query, cleaned);

        return cleaned;
    }

    /**
     * Check if a query contains significant fact-bearing terms.
     *
     * @param cleanedQuery The query after noise stripping
     * @return true if query has meaningful content for retrieval
     */
    public boolean hasSignificantContent(String cleanedQuery) {
        // At least 2 words remaining after cleaning
        return cleanedQuery != null &&
            cleanedQuery.split("\\s+").length >= 2;
    }
}
