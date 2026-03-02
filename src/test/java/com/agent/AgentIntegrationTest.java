package com.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for citation format validation and regex patterns.
 * These tests verify citation parsing and pattern matching logic.
 */
class AgentIntegrationTest {

    @Test
    void testCitationFormatValidation() {
        // Test that citations can be parsed from text
        String text = "The payment was [CIT doc=1 chunk=5 p=10-11] made on time.";
        String citationPattern = "\\[CIT\\s+doc=\\d+\\s+chunk=\\d+\\s+p=\\d+-\\d+\\]";
        assertTrue(text.matches(".*" + citationPattern + ".*"),
            "Citation format should match the pattern");
    }

    @Test
    void testValidCitationFormat() {
        String citation = "[CIT doc=1 chunk=5 p=10-11]";
        String pattern = "\\[CIT\\s+doc=\\d+\\s+chunk=\\d+\\s+p=\\d+-\\d+\\]";
        assertTrue(citation.matches(pattern), "Citation should match valid format");
    }

    @Test
    void testMultipleCitationsInText() {
        String text = "First fact [CIT doc=1 chunk=2 p=5-6]. Second fact [CIT doc=2 chunk=3 p=10-11]";
        String citationPattern = "\\[CIT\\s+doc=\\d+\\s+chunk=\\d+\\s+p=\\d+-\\d+\\]";
        int count = 0;
        int lastIndex = 0;
        while ((lastIndex = text.indexOf("[CIT", lastIndex)) != -1) {
            count++;
            lastIndex++;
        }
        assertEquals(2, count, "Should find 2 citations");
    }

    @Test
    void testInvalidCitationFormat() {
        String invalidCitation = "[CIT missing=format]";
        String pattern = "\\[CIT\\s+doc=\\d+\\s+chunk=\\d+\\s+p=\\d+-\\d+\\]";
        assertFalse(invalidCitation.matches(pattern), "Invalid citation should not match");
    }

    @Test
    void testCitationEdgeCases() {
        // Test citations with different numbers
        String[] validCitations = {
            "[CIT doc=1 chunk=0 p=0-0]",
            "[CIT doc=999 chunk=888 p=100-200]",
            "[CIT doc=1 chunk=1 p=1-1]"
        };
        String pattern = "\\[CIT\\s+doc=\\d+\\s+chunk=\\d+\\s+p=\\d+-\\d+\\]";
        
        for (String citation : validCitations) {
            assertTrue(citation.matches(pattern), 
                "Citation " + citation + " should match pattern");
        }
    }
}
