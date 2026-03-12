package com.agent.service.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CaseAnalysisQueryCleaner.
 *
 * Tests analysis noise phrase removal and whitespace normalization.
 */
class CaseAnalysisQueryCleanerTest {

    private final CaseAnalysisQueryCleaner cleaner = new CaseAnalysisQueryCleaner();

    @Test
    @DisplayName("Removes 'based on these facts' phrase")
    void testRemoveBasedOnTheseFacts() {
        String query = "Based on these facts, do I have a reimbursement claim?";
        String cleaned = cleaner.stripAnalysisNoise(query);
        assertFalse(cleaned.contains("based"), "Should remove 'based'");
        assertTrue(cleaned.contains("reimbursement"), "Should keep 'reimbursement'");
    }

    @Test
    @DisplayName("Removes 'do I have' phrase")
    void testRemoveDoIHave() {
        String query = "Do I have a reimbursement claim for post-separation mortgage payments?";
        String cleaned = cleaner.stripAnalysisNoise(query);
        assertFalse(cleaned.contains("do i have"), "Should remove 'do i have'");
        assertTrue(cleaned.contains("reimbursement"), "Should keep fact terms");
    }

    @Test
    @DisplayName("Removes 'how strong is my' phrase")
    void testRemoveHowStrongIsMyPhrase() {
        String query = "How strong is my claim for property characterization?";
        String cleaned = cleaner.stripAnalysisNoise(query);
        assertFalse(cleaned.contains("how strong"), "Should remove 'how strong'");
        assertTrue(cleaned.contains("property"), "Should keep relevant terms");
    }

    @Test
    @DisplayName("Removes multiple noise phrases from complex query")
    void testRemoveMultiplePhrases() {
        String query = "Based on these facts, do I have a strong case for post-separation mortgage reimbursement?";
        String cleaned = cleaner.stripAnalysisNoise(query);
        assertFalse(cleaned.contains("based"), "Should remove 'based'");
        assertFalse(cleaned.contains("do i have"), "Should remove 'do i have'");
        assertTrue(cleaned.contains("reimbursement"), "Should keep 'reimbursement'");
        assertTrue(cleaned.contains("mortgage"), "Should keep 'mortgage'");
    }

    @Test
    @DisplayName("Collapses multiple spaces into single space")
    void testWhitespaceNormalization() {
        String query = "reimbursement   claim    post-separation   mortgage";
        String cleaned = cleaner.stripAnalysisNoise(query);
        assertFalse(cleaned.contains("   "), "Should not have multiple spaces");
        assertEquals("reimbursement claim post-separation mortgage", cleaned);
    }

    @Test
    @DisplayName("Trims leading and trailing whitespace")
    void testWhitespaceTrimming() {
        String query = "  reimbursement claim mortgage   ";
        String cleaned = cleaner.stripAnalysisNoise(query);
        assertFalse(cleaned.startsWith(" "), "Should not start with space");
        assertFalse(cleaned.endsWith(" "), "Should not end with space");
    }

    @Test
    @DisplayName("Case-insensitive phrase removal")
    void testCaseInsensitiveRemoval() {
        String query = "BASED ON THESE FACTS, do I have a claim?";
        String cleaned = cleaner.stripAnalysisNoise(query);
        assertFalse(cleaned.toLowerCase().contains("based on these facts"));
        assertTrue(cleaned.contains("claim"));
    }

    @Test
    @DisplayName("Handles empty or null queries gracefully")
    void testNullAndEmptyQueries() {
        assertEquals("", cleaner.stripAnalysisNoise(null));
        assertEquals("", cleaner.stripAnalysisNoise(""));
        assertEquals("", cleaner.stripAnalysisNoise("   "));
    }

    @Test
    @DisplayName("Handles queries with only noise phrases")
    void testQueryWithOnlyNoisePhrases() {
        String query = "Based on these facts do I have";
        String cleaned = cleaner.stripAnalysisNoise(query);
        assertTrue(cleaned.isBlank() || cleaned.length() < 5,
            "Query with only noise should result in empty or minimal output");
    }

    @Test
    @DisplayName("Detects significant content after cleaning")
    void testHasSignificantContent() {
        assertTrue(cleaner.hasSignificantContent("reimbursement mortgage payments"));
        assertFalse(cleaner.hasSignificantContent("reimbursement")); // Single word
        assertFalse(cleaner.hasSignificantContent("")); // Empty
        assertFalse(cleaner.hasSignificantContent(null)); // Null
    }

    @Test
    @DisplayName("Complete real-world query example")
    void testRealWorldQueryExample() {
        String query = "Based on these facts, do I have a strong reimbursement claim " +
            "for post-separation mortgage payments on the Newark house?";
        String cleaned = cleaner.stripAnalysisNoise(query);

        // Should remove all analysis framing
        assertFalse(cleaned.contains("based on"));
        assertFalse(cleaned.contains("do i have"));

        // Should keep all fact-bearing terms
        assertTrue(cleaned.contains("reimbursement"));
        assertTrue(cleaned.contains("post-separation"));
        assertTrue(cleaned.contains("mortgage"));
        assertTrue(cleaned.contains("newark"));
    }
}
