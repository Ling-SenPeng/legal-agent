package com.agent.service.handler;

import com.agent.model.EvidenceChunk;
import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.analysis.*;
import com.agent.service.analysis.CaseAnalysisContextBuilder;
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

    @BeforeEach
    void setUp() {
        testRetrievalService = new TestRetrievalService();
        testContextBuilder = new TestCaseAnalysisContextBuilder();
        handler = new CaseAnalysisModeHandler(testRetrievalService, testContextBuilder);
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
            new CaseFact("Children ages 7 and 10", true, "source", LegalIssueType.CUSTODY),
            new CaseFact("[NEEDED FACT] Parenting schedule", false, "[Missing]", LegalIssueType.CUSTODY)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(query, issues, facts, 
            "Best interests standard applies.");
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("Issues: 1"));
        assertTrue(result.getMetadata().contains("Facts: 3"));
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
            new CaseFact("Purchased for $500k", true, "source", LegalIssueType.PROPERTY_CHARACTERIZATION),
            new CaseFact("[NEEDED FACT] Title status", false, "[Missing]", LegalIssueType.PROPERTY_CHARACTERIZATION)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(query, issues, facts,
            "Community property presumption applies.");
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        String answer = result.getAnswer();
        assertTrue(answer.contains("Supporting Facts:"), "Should list supporting facts");
        assertTrue(answer.contains("NEEDED FACT") || answer.contains("Missing"), 
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
        public CaseAnalysisContext buildContext(String query, List<EvidenceChunk> evidenceChunks) {
            return context;
        }
    }
}
