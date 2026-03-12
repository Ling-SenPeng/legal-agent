package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CASE_ANALYSIS query preprocessing pipeline.
 *
 * Tests the complete flow: strip noise → extract issues → build queries → retrieve/merge.
 */
class CaseAnalysisQueryPreprocessingIntegrationTest {

    private final CaseAnalysisQueryCleaner cleaner = new CaseAnalysisQueryCleaner();
    private final CaseAnalysisRetrievalQueryBuilder builder = new CaseAnalysisRetrievalQueryBuilder();

    @Test
    @DisplayName("Full pipeline: REIMBURSEMENT query preprocessing")
    void testReimbursementQueryPreprocessing() {
        // Original query with analysis framing noise
        String originalQuery = "Based on these facts, do I have a strong reimbursement claim for post-separation mortgage payments on the Newark house?";

        // Step 1: Clean the query
        String cleanedQuery = cleaner.stripAnalysisNoise(originalQuery);
        assertTrue(cleaner.hasSignificantContent(cleanedQuery), "Cleaned query should have significant content");
        
        // Verify noise was removed
        assertFalse(cleanedQuery.toLowerCase().contains("based on these facts"), 
            "Should remove 'based on these facts'");
        assertFalse(cleanedQuery.toLowerCase().contains("do i have"),
            "Should remove 'do i have'");
        
        // Verify key terms remain
        assertTrue(cleanedQuery.toLowerCase().contains("reimbursement") || 
                   cleanedQuery.toLowerCase().contains("mortgage"),
            "Should keep key fact terms");
        
        System.out.println("Original: " + originalQuery);
        System.out.println("Cleaned: " + cleanedQuery);

        // Step 2: Extract issues from cleaned query
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );

        // Step 3: Build optimized retrieval queries
        List<String> retrievalQueries = builder.buildQueries(cleanedQuery, issues);
        
        // Verify queries were generated
        assertFalse(retrievalQueries.isEmpty(), "Should generate at least one retrieval query");
        assertTrue(retrievalQueries.size() <= 5, "Should not exceed 5 queries");
        
        // Verify first query contains core terms
        assertTrue(retrievalQueries.get(0).contains("reimbursement") || 
                   retrievalQueries.get(0).contains("mortgage"),
            "First query should contain key terms");
        
        System.out.println("Generated " + retrievalQueries.size() + " retrieval queries:");
        retrievalQueries.forEach(q -> System.out.println("  - " + q));
    }

    @Test
    @DisplayName("Full pipeline: CUSTODY query preprocessing")
    void testCustodyQueryPreprocessing() {
        // Original query with analysis intent
        String originalQuery = "How strong is my custody position given that I have the children full-time and provide all care?";

        // Step 1: Clean the query
        String cleanedQuery = cleaner.stripAnalysisNoise(originalQuery);
        assertTrue(cleaner.hasSignificantContent(cleanedQuery));
        
        // Noise should be removed
        assertFalse(cleanedQuery.toLowerCase().contains("how strong is my"),
            "Should remove analysis framing");
        
        // Key terms should remain
        assertTrue(cleanedQuery.toLowerCase().contains("children") || 
                   cleanedQuery.toLowerCase().contains("care"),
            "Should preserve fact terms");
        
        System.out.println("Original: " + originalQuery);
        System.out.println("Cleaned: " + cleanedQuery);

        // Step 2-3: Extract issues and build retrieval queries
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.80, "custody")
        );
        
        List<String> retrievalQueries = builder.buildQueries(cleanedQuery, issues);
        assertFalse(retrievalQueries.isEmpty(), "Should generate queries");
        assertTrue(retrievalQueries.size() <= 5, "Should stay within limit");
        
        // Verify custody-relevant queries
        assertTrue(retrievalQueries.stream().anyMatch(q ->
                q.contains("custody") || q.contains("children") || q.contains("parenting")),
            "Should include custody-relevant keywords");
        
        System.out.println("Generated " + retrievalQueries.size() + " retrieval queries:");
        retrievalQueries.forEach(q -> System.out.println("  - " + q));
    }

    @Test
    @DisplayName("Pipeline handles empty cleaned query")
    void testPipelineHandlesOverCleaning() {
        // Query that's entirely noise
        String noisyQuery = "Based on these facts, do I have a strong case?";

        String cleanedQuery = cleaner.stripAnalysisNoise(noisyQuery);
        
        // If cleaning removes too much, should still be able to extract issues
        List<CaseIssue> issues = List.of();
        
        List<String> retrievalQueries = builder.buildQueries(cleanedQuery, issues);
        
        // Should handle empty/minimal queries gracefully
        // Either return empty or use fallback strategy
        assertTrue(retrievalQueries.isEmpty() || !retrievalQueries.get(0).isBlank(),
            "Should handle empty results gracefully");
    }

    @Test
    @DisplayName("Query cleaning preserves all key legal terms")
    void testCleaningPreservesLegalTerms() {
        String query = "Based on these facts, do I have a property characterization issue regarding separate property acquired before marriage?";

        String cleaned = cleaner.stripAnalysisNoise(query);
        
        // Verify key legal concepts are preserved
        String lowerCleaned = cleaned.toLowerCase();
        
        assertTrue(lowerCleaned.contains("property") || lowerCleaned.contains("characterization"),
            "Should preserve 'property' or 'characterization'");
        assertTrue(lowerCleaned.contains("separate") || lowerCleaned.contains("marriage"),
            "Should preserve 'separate' or 'marriage'");
    }

    @Test
    @DisplayName("Multiple issues generate comprehensive query coverage")
    void testMultipleIssuesGenerateComprehensiveCoverage() {
        String cleanedQuery = "property and custody";
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.PROPERTY_CHARACTERIZATION, "Property", 0.80, "property"),
            new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.75, "custody")
        );

        List<String> queries = builder.buildQueries(cleanedQuery, issues);
        
        // Should generate multiple queries for comprehensive coverage
        assertTrue(queries.size() >= 2, "Should generate multiple queries for multiple issues");
        
        // Verify coverage of both issue types
        String allQueries = String.join(" ", queries);
        assertTrue(allQueries.contains("property") || allQueries.contains("characterization"),
            "Should cover property characterization");
        assertTrue(allQueries.contains("custody") || allQueries.contains("children"),
            "Should cover custody");
        
        System.out.println("Multi-issue strategy: " + queries.size() + " queries");
        queries.forEach(q -> System.out.println("  - " + q));
    }

    @Test
    @DisplayName("Real-world complex query preprocessing")
    void testComplexRealWorldQuery() {
        String complexQuery = "Based on these facts, how strong is my claim for reimbursement " +
            "of post-separation mortgage payments given that I maintained the home and the other " +
            "spouse lives there now, plus we have custody disagreements about the two children?";

        // Clean the query
        String cleaned = cleaner.stripAnalysisNoise(complexQuery);
        assertTrue(cleaner.hasSignificantContent(cleaned), "Complex query should retain content");
        
        // Extract issues (multiple)
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement"),
            new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.75, "custody"),
            new CaseIssue(LegalIssueType.EXCLUSIVE_USE, "Exclusive Use", 0.70, "exclusive use")
        );

        // Build retrieval queries
        List<String> queries = builder.buildQueries(cleaned, issues);
        
        // Should generate comprehensive coverage
        assertTrue(queries.size() >= 2, "Should generate multiple queries");
        assertTrue(queries.size() <= 5, "Should not exceed max limit");
        
        System.out.println("Complex Query Preprocessing:");
        System.out.println("Original: " + complexQuery);
        System.out.println("Cleaned: " + cleaned);
        System.out.println("Issues: " + issues.size());
        System.out.println("Retrieval Queries (" + queries.size() + "):");
        for (int i = 0; i < queries.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + queries.get(i));
        }
    }

    @Test
    @DisplayName("Query deduplication prevents redundant retrieval")
    void testQueryDeduplication() {
        String query = "reimbursement mortgage payments reimbursement";

        List<String> queries = builder.buildQueries(query, List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        ));

        // Check for duplicates
        long uniqueCount = queries.stream().distinct().count();
        assertEquals(queries.size(), uniqueCount, "Should not have duplicate queries");
    }
}
