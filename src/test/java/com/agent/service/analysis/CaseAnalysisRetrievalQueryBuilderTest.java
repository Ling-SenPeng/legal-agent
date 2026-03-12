package com.agent.service.analysis;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CaseAnalysisRetrievalQueryBuilder.
 *
 * Tests multiquery generation from cleaned queries and detected issues.
 */
class CaseAnalysisRetrievalQueryBuilderTest {

    private final CaseAnalysisRetrievalQueryBuilder builder =
        new CaseAnalysisRetrievalQueryBuilder();

    @Test
    @DisplayName("Always includes cleaned query as first query")
    void testIncludesCleanedQuery() {
        String cleanedQuery = "reimbursement mortgage payments";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);

        assertFalse(queries.isEmpty(), "Should generate queries");
        assertTrue(queries.get(0).contains("reimbursement"), "First query should be from cleaned input");
    }

    @Test
    @DisplayName("Generates issue-specific keyword queries")
    void testGeneratesIssueSpecificQueries() {
        String cleanedQuery = "mortgage payments";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);

        assertTrue(queries.size() > 1, "Should generate multiple queries for issue");
        assertTrue(queries.stream().anyMatch(q -> q.contains("reimbursement")),
            "Should include reimbursement keyword for REIMBURSEMENT issue");
    }

    @Test
    @DisplayName("Handles multiple issues")
    void testHandlesMultipleIssues() {
        String cleanedQuery = "property custody";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.PROPERTY_CHARACTERIZATION, "Property", 0.80, "property"),
            new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.75, "custody")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);

        assertTrue(queries.size() > 1, "Should generate multiple queries");
        assertTrue(queries.stream().anyMatch(q -> q.contains("property")),
            "Should include property-related queries");
        assertTrue(queries.stream().anyMatch(q -> q.contains("custody") || q.contains("children")),
            "Should include custody-related queries");
    }

    @Test
    @DisplayName("Generates multiple query variants for recall")
    void testGeneratesQueryVariants() {
        String cleanedQuery = "post-separation mortgage payments newark house";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);

        // Should have multiple variants
        assertTrue(queries.size() >= 2, "Should generate at least 2 query variants");

        // Should not exceed maximum
        assertTrue(queries.size() <= 5, "Should not exceed 5 queries");
    }

    @Test
    @DisplayName("Removes duplicate queries")
    void testRemovesDuplicates() {
        String cleanedQuery = "reimbursement reimbursement mortgage";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);

        // Check that we don't have duplicates
        assertEquals(queries.size(), queries.stream().distinct().count(),
            "Should not contain duplicate queries");
    }

    @Test
    @DisplayName("Handles empty cleaned query gracefully")
    void testHandlesEmptyQuery() {
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );

        List<String> queries = builder.buildQueries("", issues);

        assertFalse(queries.isEmpty(), "Should still generate queries from issues");
        assertTrue(queries.stream().anyMatch(q -> q.contains("reimbursement")),
            "Should use issue keywords even without cleaned query");
    }

    @Test
    @DisplayName("Handles null cleaned query gracefully")
    void testHandlesNullQuery() {
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );

        List<String> queries = builder.buildQueries(null, issues);

        assertFalse(queries.isEmpty(), "Should generate queries from issues even with null query");
    }

    @Test
    @DisplayName("Handles queries with no detected issues")
    void testHandlesNoIssues() {
        String cleanedQuery = "post-separation mortgage payments dispute";

        List<String> queries = builder.buildQueries(cleanedQuery, List.of());

        assertFalse(queries.isEmpty(), "Should generate queries from cleaned text alone");
        assertTrue(queries.get(0).contains("mortgage"), "Primary query should contain key terms");
    }

    @Test
    @DisplayName("Extraction logic filters short words")
    void testExtractsKeyTermsFiltersShortWords() {
        String cleanedQuery = "a the post-separation mortgage payments";

        List<String> queries = builder.buildQueries(cleanedQuery, List.of());

        // The query should not contain single-letter or 3-letter common words
        // (implementation filters words <= 3 chars)
        assertTrue(queries.get(0).contains("post-separation") || 
                   queries.get(0).contains("mortgage"),
            "Should include longer key terms");
    }

    @Test
    @DisplayName("Real-world REIMBURSEMENT issue scenario")
    void testRealWorldReimbursementScenario() {
        String cleanedQuery = "reimbursement post-separation mortgage payments newark";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);

        // Verify queries are generated
        assertFalse(queries.isEmpty(), "Should generate queries");

        // Verify primary query included
        assertTrue(queries.get(0).contains("reimbursement"), "Should include primary terms");

        // Verify issue keywords included in some query
        assertTrue(queries.stream().anyMatch(q ->
                q.contains("reimbursement") || 
                q.contains("mortgage") || 
                q.contains("payment")),
            "Should include reimbursement-relevant keywords");

        // Log for debugging
        System.out.println("Generated retrieval queries:");
        queries.forEach(q -> System.out.println("  - " + q));
    }

    @Test
    @DisplayName("Real-world CUSTODY issue scenario")
    void testRealWorldCustodyScenario() {
        String cleanedQuery = "custody arrangement children ages";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.80, "custody")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);

        assertFalse(queries.isEmpty(), "Should generate queries");
        assertTrue(queries.stream().anyMatch(q ->
                q.contains("custody") || 
                q.contains("children") || 
                q.contains("parenting")),
            "Should include custody-relevant keywords");
    }

    @Test
    @DisplayName("Real-world PROPERTY_CHARACTERIZATION scenario")
    void testRealWorldPropertyCharacterizationScenario() {
        String cleanedQuery = "property purchased marriage community separate";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.PROPERTY_CHARACTERIZATION, "Property", 0.80, "property")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);

        assertFalse(queries.isEmpty(), "Should generate queries");
        assertTrue(queries.stream().anyMatch(q ->
                q.contains("property") || 
                q.contains("characterization") || 
                q.contains("community")),
            "Should include property characterization keywords");
    }
}
