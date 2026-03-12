package com.agent.service;

import com.agent.model.TaskMode;
import com.agent.model.TaskRoutingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleBasedTaskRouter.
 * 
 * Test Coverage:
 * 1. DOCUMENT_QA routing - extraction and explanation queries
 * 2. LEGAL_RESEARCH routing - case finding and precedent search
 * 3. CASE_ANALYSIS routing - evaluative and predictive queries
 * 4. DRAFTING routing - document generation requests
 * 5. Default routing - ambiguous queries default to DOCUMENT_QA
 * 6. Confidence scoring - higher match count = higher confidence
 * 7. Edge cases - empty/null queries, case insensitivity
 */
class RuleBasedTaskRouterTest {
    
    private TaskRouter router;
    
    @BeforeEach
    void setUp() {
        router = new RuleBasedTaskRouter();
    }

    // ==================== DOCUMENT_QA Tests ====================
    
    @Test
    @DisplayName("Route DOCUMENT_QA: What does the declaration say about Newark?")
    void testDocumentQaExample1() {
        String query = "What does this declaration say about Newark occupancy?";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
        assertTrue(result.getConfidence() >= 0.7);
        assertTrue(result.getReasoning().contains("QA"));
    }
    
    @Test
    @DisplayName("Route DOCUMENT_QA: Explain the contractual terms")
    void testDocumentQaExample2() {
        String query = "Explain the contractual terms in the agreement.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
        assertTrue(result.getConfidence() >= 0.7);
    }
    
    @Test
    @DisplayName("Route DOCUMENT_QA: List all dates mentioned")
    void testDocumentQaExample3() {
        String query = "List all dates mentioned in the document.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
        assertTrue(result.getConfidence() >= 0.7);
    }
    
    @Test
    @DisplayName("Route DOCUMENT_QA: Show the defendant's statement")
    void testDocumentQaExample4() {
        String query = "Show me what the defendant's statement says about damages.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
    }
    
    @Test
    @DisplayName("Route DOCUMENT_QA: Summarize the evidence")
    void testDocumentQaExample5() {
        String query = "Summarize the key evidence presented.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
    }

    // ==================== LEGAL_RESEARCH Tests ====================
    
    @Test
    @DisplayName("Route LEGAL_RESEARCH: Find cases about mortgage reimbursement")
    void testLegalResearchExample1() {
        String query = "Find cases about post-separation mortgage reimbursement.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.LEGAL_RESEARCH, result.getMode());
        assertTrue(result.getConfidence() >= 0.7);
        assertTrue(result.getReasoning().contains("research"));
    }
    
    @Test
    @DisplayName("Route LEGAL_RESEARCH: Search for precedents")
    void testLegalResearchExample2() {
        String query = "Search for precedents on equitable distribution.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.LEGAL_RESEARCH, result.getMode());
        assertTrue(result.getConfidence() >= 0.7);
    }
    
    @Test
    @DisplayName("Route LEGAL_RESEARCH: Identify applicable case law")
    void testLegalResearchExample3() {
        String query = "Identify applicable case law on tort liability.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.LEGAL_RESEARCH, result.getMode());
    }
    
    @Test
    @DisplayName("Route LEGAL_RESEARCH: Locate statute sections")
    void testLegalResearchExample4() {
        String query = "Locate statute sections addressing breach of contract.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.LEGAL_RESEARCH, result.getMode());
    }

    // ==================== CASE_ANALYSIS Tests ====================
    
    @Test
    @DisplayName("Route CASE_ANALYSIS: Do I have a strong reimbursement claim?")
    void testCaseAnalysisExample1() {
        String query = "Based on these facts, do I have a strong reimbursement claim?";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.getConfidence() >= 0.7);
        assertTrue(result.getReasoning().contains("analysis"));
    }
    
    @Test
    @DisplayName("Route CASE_ANALYSIS: What is the strength of this position?")
    void testCaseAnalysisExample2() {
        String query = "What is the strength of our legal position?";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
    }
    
    @Test
    @DisplayName("Route CASE_ANALYSIS: Evaluate the risk of proceeding")
    void testCaseAnalysisExample3() {
        String query = "Evaluate the risk of proceeding with this claim.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
    }
    
    @Test
    @DisplayName("Route CASE_ANALYSIS: Assess whether the claim is likely to succeed")
    void testCaseAnalysisExample4() {
        String query = "Based on this evidence, is the claim likely to succeed?";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
    }
    
    @Test
    @DisplayName("Route CASE_ANALYSIS: Multiple analysis indicators")
    void testCaseAnalysisMultipleKeywords() {
        String query = "Given these facts, evaluate the strength of our claim and assess the likely outcome.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.getConfidence() >= 0.8);  // Multiple keywords increase confidence
    }

    // ==================== DRAFTING Tests ====================
    
    @Test
    @DisplayName("Route DRAFTING: Draft a memo arguing for reimbursement")
    void testDraftingExample1() {
        String query = "Draft a short memo arguing for reimbursement.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DRAFTING, result.getMode());
        assertTrue(result.getConfidence() >= 0.7);
        assertTrue(result.getReasoning().contains("drafting"));
    }
    
    @Test
    @DisplayName("Route DRAFTING: Write a brief for the court")
    void testDraftingExample2() {
        String query = "Write a brief for the court summarizing the issues.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DRAFTING, result.getMode());
    }
    
    @Test
    @DisplayName("Route DRAFTING: Create a motion")
    void testDraftingExample3() {
        String query = "Create a motion to dismiss based on lack of standing.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DRAFTING, result.getMode());
    }
    
    @Test
    @DisplayName("Route DRAFTING: Prepare a settlement letter")
    void testDraftingExample4() {
        String query = "Prepare a settlement letter for the defendant.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DRAFTING, result.getMode());
    }

    // ==================== DEFAULT/EDGE CASES Tests ====================
    
    @Test
    @DisplayName("Route DEFAULT: Empty query defaults to DOCUMENT_QA")
    void testEmptyQuery() {
        TaskRoutingResult result = router.route("");
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
        assertTrue(result.getConfidence() < 0.7);  // Low confidence default
    }
    
    @Test
    @DisplayName("Route DEFAULT: Null query defaults to DOCUMENT_QA")
    void testNullQuery() {
        TaskRoutingResult result = router.route(null);
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
        assertTrue(result.getConfidence() <= 0.5);
    }
    
    @Test
    @DisplayName("Route DEFAULT: Ambiguous query defaults to DOCUMENT_QA")
    void testAmbiguousQuery() {
        String query = "Tell me about the contract.";
        TaskRoutingResult result = router.route(query);
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
        assertTrue(result.getConfidence() <= 0.5);  // Low confidence for no clear indicators
    }
    
    @Test
    @DisplayName("Case insensitivity: DRAFT vs draft")
    void testCaseInsensitivity() {
        String queryLower = "draft a memo";
        String queryUpper = "DRAFT A MEMO";
        String queryMixed = "Draft a Memo";
        
        TaskRoutingResult resultLower = router.route(queryLower);
        TaskRoutingResult resultUpper = router.route(queryUpper);
        TaskRoutingResult resultMixed = router.route(queryMixed);
        
        assertEquals(TaskMode.DRAFTING, resultLower.getMode());
        assertEquals(TaskMode.DRAFTING, resultUpper.getMode());
        assertEquals(TaskMode.DRAFTING, resultMixed.getMode());
    }

    // ==================== CONFIDENCE SCORING Tests ====================
    
    @Test
    @DisplayName("Confidence increases with multiple keyword matches")
    void testConfidenceWithMultipleMatches() {
        String query1 = "Find cases.";
        String query2 = "Find and search for cases and precedents.";
        
        TaskRoutingResult result1 = router.route(query1);
        TaskRoutingResult result2 = router.route(query2);
        
        assertTrue(result2.getConfidence() > result1.getConfidence());
    }
    
    @Test
    @DisplayName("Confidence is within valid range [0.0, 1.0]")
    void testConfidenceRange() {
        String[] queries = {
            "What does the document say?",
            "Find cases",
            "Do I have a strong claim?",
            "Draft a memo"
        };
        
        for (String query : queries) {
            TaskRoutingResult result = router.route(query);
            assertTrue(result.getConfidence() >= 0.0);
            assertTrue(result.getConfidence() <= 1.0);
        }
    }

    // ==================== RESULT STRUCTURE Tests ====================
    
    @Test
    @DisplayName("TaskRoutingResult contains all required fields")
    void testResultStructure() {
        TaskRoutingResult result = router.route("Draft a memo");
        
        assertNotNull(result.getMode());
        assertNotNull(result.getConfidence());
        assertNotNull(result.getReasoning());
        assertTrue(result.getReasoning().length() > 0);
    }
    
    @Test
    @DisplayName("TaskRoutingResult toString is informative")
    void testResultToString() {
        TaskRoutingResult result = router.route("Find cases");
        String str = result.toString();
        
        assertTrue(str.contains("LEGAL_RESEARCH"));
        assertTrue(str.contains("confidence="));
        assertTrue(str.contains("reasoning"));
    }
}
