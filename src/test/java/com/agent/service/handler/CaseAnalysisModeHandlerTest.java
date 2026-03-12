package com.agent.service.handler;

import com.agent.model.EvidenceChunk;
import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.analysis.*;
import com.agent.service.analysis.CaseAnalysisContextBuilder;
import com.agent.service.analysis.CaseAnalysisQueryCleaner;
import com.agent.service.analysis.CaseAnalysisRetrievalQueryBuilder;
import com.agent.service.analysis.CaseIssueExtractor;
import com.agent.service.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CaseAnalysisModeHandler.
 * 
 * Tests the complete CASE_ANALYSIS V1 pipeline using test doubles
 * instead of Mockito (to avoid Spring service class mocking issues).
 */
class CaseAnalysisModeHandlerTest {
    
    private CaseAnalysisModeHandler handler;
    private TestRetrievalService testRetrievalService;
    private TestCaseAnalysisContextBuilder testContextBuilder;
    private TestQueryCleaner testQueryCleaner;
    private TestQueryBuilder testQueryBuilder;
    private TestIssueExtractor testIssueExtractor;

    @BeforeEach
    void setUp() {
        testRetrievalService = new TestRetrievalService();
        testContextBuilder = new TestCaseAnalysisContextBuilder();
        testQueryCleaner = new TestQueryCleaner();
        testQueryBuilder = new TestQueryBuilder();
        testIssueExtractor = new TestIssueExtractor();
        handler = new CaseAnalysisModeHandler(
            testRetrievalService, 
            testContextBuilder,
            testQueryCleaner,
            testQueryBuilder,
            testIssueExtractor
        );
    }

    // ==================== CORE FUNCTIONALITY Tests ====================
    
    @Test
    @DisplayName("Handler returns correct TaskMode")
    void testGetMode() {
        assertEquals(TaskMode.CASE_ANALYSIS, handler.getMode());
    }
    
    @Test
    @DisplayName("Execute returns analysis with all required sections")
    void testExecuteReturnsCompleteAnalysis() {
        // Given
        String query = "Do I have a strong reimbursement claim?";
        EvidenceChunk chunk = createTestChunk(
            "I paid $20,000 in post-separation mortgage payments.",
            1L, 1, "Page 1"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("I paid $20,000 in mortgage payments", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement evaluated under Epstein factors."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess());
        
        String answer = result.getAnswer();
        assertNotNull(answer);
        assertTrue(answer.contains("CASE ANALYSIS REPORT"));
        assertTrue(answer.contains("ISSUE SUMMARY"));
        assertTrue(answer.contains("APPLICATION SUMMARY"));
        assertTrue(answer.contains("COUNTERARGUMENTS"));
        assertTrue(answer.contains("MISSING EVIDENCE"));
        assertTrue(answer.contains("TENTATIVE CONCLUSION"));
    }
    
    @Test
    @DisplayName("Metadata includes issue count, fact count, and strength")
    void testMetadataFormat() {
        // Given
        String query = "What is the strength of my claim?";
        EvidenceChunk chunk = createTestChunk("I worked stable hours", 1L, 1, "P1");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.80, "custody")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("I work stable hours", true, "source", LegalIssueType.CUSTODY),
            new CaseFact("Children ages 7 and 10", true, "source", LegalIssueType.CUSTODY)
        );
        
        List<MissingFact> missingFacts = List.of(
            new MissingFact("Parenting schedule", LegalIssueType.CUSTODY, "Not found in evidence")
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(query, issues, facts, missingFacts,
            "Best interests standard applies.");
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("Issues: 1"));
        assertTrue(result.getMetadata().contains("Facts: 2"));  // Only retrieved facts, not missing
        assertTrue(result.getMetadata().contains("Mode: CASE_ANALYSIS"));
        assertTrue(result.getMetadata().contains("Strength:"));
        assertTrue(result.getMetadata().contains("Confidence:"));
    }
    
    @Test
    @DisplayName("Handles missing evidence gracefully")
    void testHandlesNoEvidence() {
        // Given
        String query = "Analyze my case";
        testRetrievalService.setEvidenceChunks(List.of());
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess());
        assertTrue(result.getAnswer().contains("No relevant case facts found"));
    }
    
    @Test
    @DisplayName("Handles exceptions without crashing")
    void testHandlesException() {
        // Given
        String query = "My case?";
        testRetrievalService.setThrowException(true);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Error"));
    }
    
    @Test
    @DisplayName("Generates STRONG strength assessment when favorable facts dominate")
    void testAssessesStrongClaim() {
        // Given
        String query = "Strong claim?";
        EvidenceChunk chunk = createTestChunk(
            "Paid $40,000 post-separation. Property was community.", 1L, 1, "P1");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Strong", 0.95, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $40,000 post-separation", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Property was community property", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(query, issues, facts,
            "Strong Epstein factors.");
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        String answer = result.getAnswer();
        assertTrue(answer.contains("STRONG") || answer.contains("strong"),
            "Should indicate strong claim");
    }
    
    @Test
    @DisplayName("Provides comprehensive analysis sections with facts and recommendations")
    void testComprehensiveAnalysis() {
        // Given
        String query = "Property characterization?";
        EvidenceChunk chunk = createTestChunk("Purchased for $500k on marriage", 1L, 1, "P1");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.PROPERTY_CHARACTERIZATION, "Characterization", 0.75, "property")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Purchased for $500k", true, "source", LegalIssueType.PROPERTY_CHARACTERIZATION)
        );
        
        List<MissingFact> missingFacts = List.of(
            new MissingFact("Title status", LegalIssueType.PROPERTY_CHARACTERIZATION, "Not found in evidence")
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(query, issues, facts, missingFacts,
            "Community property presumption applies.");
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        String answer = result.getAnswer();
        assertTrue(answer.contains("Supporting Facts:"), "Should list supporting facts");
        assertTrue(answer.contains("Title status") || answer.contains("Missing"), 
            "Should show missing evidence");
        assertTrue(answer.contains("Priority actions"), "Should provide recommendations");
        assertTrue(answer.contains("PRELIMINARY ANALYSIS ONLY"), "Should have disclaimer");
    }
    
    // ==================== HELPER METHODS ====================
    
    private EvidenceChunk createTestChunk(String text, Long docId, Integer pageNo, String pageRef) {
        return new EvidenceChunk(
            1L + pageNo, docId, pageNo, pageNo, pageNo, text, 0.8,
            "[CIT doc=" + docId + " p=" + pageRef + "]", 0.8, 0.7, 0.75
        );
    }
    
    // ==================== TEST DOUBLES ====================
    
    /**
     * Test double for RetrievalService - allows setting evidence chunks
     * and optionally throwing exceptions for error testing.
     */
    static class TestRetrievalService extends RetrievalService {
        private List<EvidenceChunk> evidenceChunks = List.of();
        private boolean throwException = false;
        
        // We need a no-arg constructor for Spring injection simulation
        public TestRetrievalService() {
            super(null, null, null);
        }
        
        void setEvidenceChunks(List<EvidenceChunk> chunks) {
            this.evidenceChunks = chunks;
        }
        
        void setThrowException(boolean shouldThrow) {
            this.throwException = shouldThrow;
        }
        
        @Override
        public List<EvidenceChunk> retrieveEvidence(String query, int topK) {
            if (throwException) {
                throw new RuntimeException("Test service error");
            }
            return evidenceChunks;
        }
        
        @Override
        public List<EvidenceChunk> retrieveEvidenceWithoutPlanning(String preplannedQuery, int topK) {
            if (throwException) {
                throw new RuntimeException("Test service error");
            }
            return evidenceChunks;
        }
    }
    
    /**
     * Test double for CaseAnalysisContextBuilder - allows setting
     * the context that should be returned without building it.
     */
    static class TestCaseAnalysisContextBuilder implements CaseAnalysisContextBuilder {
        private CaseAnalysisContext context;
        
        void setContext(CaseAnalysisContext ctx) {
            this.context = ctx;
        }
        
        @Override
        public CaseAnalysisContext buildContext(
            String originalQuery,
            String cleanedQuery,
            List<CaseIssue> identifiedIssues,
            List<EvidenceChunk> evidenceChunks
        ) {
            return context;
        }
    }

    /**
     * Test double for CaseAnalysisQueryCleaner - returns cleaned version of query
     */
    static class TestQueryCleaner extends CaseAnalysisQueryCleaner {
        @Override
        public String stripAnalysisNoise(String query) {
            // Simple implementation - just remove common noise phrases
            if (query == null) return "";
            return query.toLowerCase()
                .replace("based on these facts", "")
                .replace("do i have", "")
                .replace("how strong is my", "")
                .trim()
                .replaceAll("\\s+", " ");
        }

        @Override
        public boolean hasSignificantContent(String cleanedQuery) {
            if (cleanedQuery == null || cleanedQuery.isBlank()) return false;
            return cleanedQuery.split("\\s+").length >= 2;
        }
    }

    /**
     * Test double for CaseAnalysisRetrievalQueryBuilder - generates multiple retrieval queries
     */
    static class TestQueryBuilder extends CaseAnalysisRetrievalQueryBuilder {
        @Override
        public List<String> buildQueries(String cleanedQuery, List<CaseIssue> issues) {
            // For testing, just return a couple of basic queries
            if (cleanedQuery == null || cleanedQuery.isBlank()) {
                return List.of();
            }
            
            List<String> queries = new java.util.ArrayList<>();
            queries.add(cleanedQuery); // Original cleaned query
            
            // Add issue-specific variations
            if (!issues.isEmpty()) {
                CaseIssue firstIssue = issues.get(0);
                queries.add(cleanedQuery + " " + firstIssue.getType().name().toLowerCase());
            }
            
            return queries;
        }
    }

    /**
     * Test double for CaseIssueExtractor - extracts issues from query
     */
    static class TestIssueExtractor implements CaseIssueExtractor {
        @Override
        public List<CaseIssue> extractIssues(String caseQuery) {
            // Simple keyword-based extraction for testing
            if (caseQuery == null) return List.of();
            
            List<CaseIssue> issues = new java.util.ArrayList<>();
            String queryLower = caseQuery.toLowerCase();
            
            if (queryLower.contains("reimbursement") || queryLower.contains("payment")) {
                issues.add(new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement"));
            }
            if (queryLower.contains("custody") || queryLower.contains("children")) {
                issues.add(new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.80, "custody"));
            }
            if (queryLower.contains("property") || queryLower.contains("characterization")) {
                issues.add(new CaseIssue(LegalIssueType.PROPERTY_CHARACTERIZATION, "Property", 0.75, "property"));
            }
            
            return issues;
        }

        @Override
        public List<CaseIssue> extractIssues(String caseQuery, String context) {
            return extractIssues(caseQuery);
        }
    }
}
