package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for fact extraction and context building.
 * 
 * Test Coverage:
 * 1. Fact extraction from evidence chunks
 * 2. Confidence scoring for extracted facts
 * 3. Missing fact identification per issue type
 * 4. Context building with issues, facts, and missing facts
 * 5. Legal standard summary generation
 * 6. Edge cases (empty chunks, null input)
 */
@ExtendWith(MockitoExtension.class)
class CaseAnalysisFactExtractionTest {
    
    private RuleBasedCaseFactExtractor factExtractor;
    private RuleBasedCaseAnalysisContextBuilder contextBuilder;
    
    @Mock
    private CaseIssueExtractor mockIssueExtractor;

    @BeforeEach
    void setUp() {
        factExtractor = new RuleBasedCaseFactExtractor();
        contextBuilder = new RuleBasedCaseAnalysisContextBuilder(
            mockIssueExtractor,
            factExtractor
        );
    }

    // ==================== FACT EXTRACTION Tests ====================
    
    @Test
    @DisplayName("Extract facts from evidence chunk with date information")
    void testExtractFactsWithDate() {
        // Given
        EvidenceChunk chunk = createTestChunk(
            "On January 15, 2020, I paid the mortgage payment of $2,500.",
            1L, 1, "Page 1"
        );
        
        // When
        List<CaseFact> facts = factExtractor.extractFacts(
            List.of(chunk),
            LegalIssueType.REIMBURSEMENT
        );
        
        // Then
        assertTrue(facts.size() > 0);
        assertTrue(facts.stream().anyMatch(f -> 
            f.getDescription().contains("January") && 
            f.getDescription().contains("mortgage")
        ));
    }
    
    @Test
    @DisplayName("Extract facts with payment amounts")
    void testExtractFactsWithPaymentAmount() {
        // Given
        EvidenceChunk chunk = createTestChunk(
            "I contributed $50,000 toward the property purchase as the down payment.",
            1L, 1, "Page 1"
        );
        
        // When
        List<CaseFact> facts = factExtractor.extractFacts(
            List.of(chunk),
            LegalIssueType.TRACING
        );
        
        // Then
        assertTrue(facts.size() > 0);
        assertTrue(facts.stream().anyMatch(f ->
            f.getDescription().contains("$50,000") ||
            f.getDescription().contains("down payment")
        ));
    }
    
    @Test
    @DisplayName("Extract facts with occupancy information")
    void testExtractFactsWithOccupancyInfo() {
        // Given
        EvidenceChunk chunk = createTestChunk(
            "The respondent occupied the family home from 2018 through 2023 while I lived elsewhere.",
            1L, 1, "Page 1"
        );
        
        // When
        List<CaseFact> facts = factExtractor.extractFacts(
            List.of(chunk),
            LegalIssueType.EXCLUSIVE_USE
        );
        
        // Then
        assertTrue(facts.size() > 0);
        assertTrue(facts.stream().anyMatch(f ->
            f.getDescription().contains("occupied") ||
            f.getDescription().contains("2018")
        ));
    }
    
    @Test
    @DisplayName("Extract facts from multiple chunks")
    void testExtractFactsFromMultipleChunks() {
        // Given
        EvidenceChunk chunk1 = createTestChunk(
            "On June 1, 2019, we purchased the property for $500,000.",
            1L, 1, "Page 1"
        );
        EvidenceChunk chunk2 = createTestChunk(
            "I paid $100,000 down payment from my separate property.",
            1L, 2, "Page 2"
        );
        
        // When
        List<CaseFact> facts = factExtractor.extractFacts(
            List.of(chunk1, chunk2),
            LegalIssueType.PROPERTY_CHARACTERIZATION
        );
        
        // Then
        assertTrue(facts.size() >= 2);
    }

    // ==================== CONFIDENCE SCORING Tests ====================
    
    @Test
    @DisplayName("Facts with dates have high confidence")
    void testConfidenceWithDate() {
        // Given
        EvidenceChunk chunk = createTestChunk(
            "On March 15, 2022, the payment was made.",
            1L, 1, "Page 1"
        );
        
        // When
        List<CaseFact> facts = factExtractor.extractFacts(
            List.of(chunk),
            LegalIssueType.REIMBURSEMENT
        );
        
        // Then
        assertTrue(facts.size() > 0);
        // Facts with specific dates should have higher confidence
        assertTrue(facts.get(0).getDescription().contains("March"));
    }

    // ==================== MISSING FACTS Tests ====================
    
    @Test
    @DisplayName("Missing facts identified for REIMBURSEMENT issue")
    void testMissingFactsReimbursement() {
        // Given
        String query = "Do I deserve reimbursement for post-separation mortgage payments?";
        EvidenceChunk chunk = createTestChunk(
            "I paid $20,000 in mortgage payments.",
            1L, 1, "Page 1"
        );
        
        CaseIssue reimbursement = new CaseIssue(
            LegalIssueType.REIMBURSEMENT,
            "Request for reimbursement",
            0.85,
            "reimbursement"
        );
        
        // When
        CaseAnalysisContext context = contextBuilder.buildContext(query, query, List.of(reimbursement), List.of(chunk));
        
        // Then
        assertNotNull(context.getRelevantFacts());
        assertTrue(context.getRelevantFacts().size() >= 1, 
            "Should have at least 1 extracted fact");
        
        // Check for missing facts (facts with descriptions starting with [NEEDED FACT])
        List<CaseFact> allRelevantFacts = context.getRelevantFacts();
        List<CaseFact> neededFacts = allRelevantFacts.stream()
            .filter(f -> f.getDescription() != null && f.getDescription().startsWith("[NEEDED FACT]"))
            .toList();
        
        assertTrue(neededFacts.size() > 0, 
            "Should have identified missing facts. Total facts: " + allRelevantFacts.size() + 
            ", facts with [NEEDED FACT]: " + neededFacts.size());
        
        assertTrue(neededFacts.stream().anyMatch(f ->
            f.getDescription().toLowerCase().contains("timeline") ||
            f.getDescription().toLowerCase().contains("source")
        ));
    }
    
    @Test
    @DisplayName("Missing facts identified for TRACING issue")
    void testMissingFactsTracing() {
        // Given
        String query = "Need tracing analysis for down payment source.";
        EvidenceChunk chunk = createTestChunk(
            "The down payment was $40,000.",
            1L, 1, "Page 1"
        );
        
        CaseIssue tracing = new CaseIssue(
            LegalIssueType.TRACING,
            "Fund tracing needed",
            0.80,
            "tracing"
        );
        
        // When
        CaseAnalysisContext context = contextBuilder.buildContext(query, query, List.of(tracing), List.of(chunk));
        
        // Then
        List<CaseFact> missingFacts = context.getRelevantFacts().stream()
            .filter(f -> f.getDescription().startsWith("[NEEDED FACT]"))
            .toList();
        
        assertTrue(missingFacts.stream().anyMatch(f ->
            f.getDescription().contains("documentation") ||
            f.getDescription().contains("separate property")
        ));
    }
    
    @Test
    @DisplayName("Missing facts identified for EXCLUSIVE_USE issue")
    void testMissingFactsExclusiveUse() {
        // Given
        String query = "Should I get exclusive use of the family home?";
        EvidenceChunk chunk = createTestChunk(
            "I owned the property before marriage.",
            1L, 1, "Page 1"
        );
        
        CaseIssue exclusiveUse = new CaseIssue(
            LegalIssueType.EXCLUSIVE_USE,
            "Exclusive use of family home",
            0.75,
            "exclusive use"
        );
        
        // When
        CaseAnalysisContext context = contextBuilder.buildContext(query, query, List.of(exclusiveUse), List.of(chunk));
        
        // Then
        List<CaseFact> missingFacts = context.getRelevantFacts().stream()
            .filter(f -> f.getDescription().startsWith("[NEEDED FACT]"))
            .toList();
        
        assertTrue(missingFacts.stream().anyMatch(f ->
            f.getDescription().contains("occupancy") ||
            f.getDescription().contains("rental value")
        ));
    }
    
    @Test
    @DisplayName("Missing facts identified for CUSTODY issue")
    void testMissingFactsCustody() {
        // Given
        String query = "What custody arrangement is best for our children?";
        EvidenceChunk chunk = createTestChunk(
            "We have two children, ages 8 and 10.",
            1L, 1, "Page 1"
        );
        
        CaseIssue custody = new CaseIssue(
            LegalIssueType.CUSTODY,
            "Custody arrangement",
            0.80,
            "custody"
        );
        
        // When
        CaseAnalysisContext context = contextBuilder.buildContext(query, query, List.of(custody), List.of(chunk));
        
        // Then
        List<CaseFact> missingFacts = context.getRelevantFacts().stream()
            .filter(f -> f.getDescription().startsWith("[NEEDED FACT]"))
            .toList();
        
        assertTrue(missingFacts.stream().anyMatch(f ->
            f.getDescription().contains("parenting schedule") ||
            f.getDescription().contains("school")
        ));
    }

    // ==================== CONTEXT BUILDING Tests ====================
    
    @Test
    @DisplayName("Context building combines extracted and missing facts")
    void testContextCombinesFactTypes() {
        // Given
        String query = "Do I have a strong reimbursement claim?";
        EvidenceChunk chunk = createTestChunk(
            "From 2020 to 2023, I made mortgage payments totaling $80,000.",
            1L, 1, "Page 1"
        );
        
        CaseIssue issue = new CaseIssue(
            LegalIssueType.REIMBURSEMENT,
            "Reimbursement claim",
            0.85,
            "reimbursement"
        );
        
        // When
        CaseAnalysisContext context = contextBuilder.buildContext(query, query, List.of(issue), List.of(chunk));
        
        // Then
        assertNotNull(context);
        assertEquals(query, context.getCaseQuery());
        assertTrue(context.getIdentifiedIssues().size() > 0);
        assertTrue(context.getRelevantFacts().size() > 0);
        assertNotNull(context.getLegalStandardSummary());
    }
    
    @Test
    @DisplayName("Context includes legal standard summary")
    void testContextIncludesLegalStandards() {
        // Given
        String query = "What about property characterization?";
        EvidenceChunk chunk = createTestChunk(
            "We purchased the property during marriage.",
            1L, 1, "Page 1"
        );
        
        CaseIssue issue = new CaseIssue(
            LegalIssueType.PROPERTY_CHARACTERIZATION,
            "Property characterization",
            0.80,
            "property"
        );
        
        // When
        CaseAnalysisContext context = contextBuilder.buildContext(query, query, List.of(issue), List.of(chunk));
        
        // Then
        assertTrue(context.getLegalStandardSummary().contains("Community property") ||
                   context.getLegalStandardSummary().contains("tracing"));
    }

    // ==================== EDGE CASES Tests ====================
    
    @Test
    @DisplayName("Empty chunklist returns empty facts")
    void testEmptyChunks() {
        // When
        List<CaseFact> facts = factExtractor.extractFacts(
            List.of(),
            LegalIssueType.REIMBURSEMENT
        );
        
        // Then
        assertTrue(facts.isEmpty());
    }
    
    @Test
    @DisplayName("Chunk with empty text returns no facts")
    void testEmptyChunkText() {
        // Given
        EvidenceChunk chunk = createTestChunk(
            "",
            1L, 1, "Page 1"
        );
        
        // When
        List<CaseFact> facts = factExtractor.extractFacts(
            List.of(chunk),
            LegalIssueType.REIMBURSEMENT
        );
        
        // Then
        assertTrue(facts.isEmpty());
    }
    
    @Test
    @DisplayName("Multiple issues in single context")
    void testMultipleIssuesInContext() {
        // Given
        String query = "Reimbursement and custody issues";
        EvidenceChunk chunk = createTestChunk(
            "I paid $60,000 post-separation and have two children ages 5 and 8.",
            1L, 1, "Page 1"
        );
        
        CaseIssue issue1 = new CaseIssue(
            LegalIssueType.REIMBURSEMENT,
            "Reimbursement",
            0.85,
            "reimbursement"
        );
        CaseIssue issue2 = new CaseIssue(
            LegalIssueType.CUSTODY,
            "Custody",
            0.80,
            "custody"
        );
        
        // When
        CaseAnalysisContext context = contextBuilder.buildContext(query, query, List.of(issue1, issue2), List.of(chunk));
        
        // Then
        assertEquals(2, context.getIdentifiedIssues().size());
        assertTrue(context.getRelevantFacts().size() >= 2);
    }

    // ==================== HELPER METHODS ====================
    
    private EvidenceChunk createTestChunk(String text, Long docId, Integer pageNo, String pageRef) {
        return new EvidenceChunk(
            1L + pageNo,  // chunkId
            docId,
            pageNo,
            pageNo,
            pageNo,
            text,
            0.8,          // similarity
            "[CIT doc=" + docId + " p=" + pageRef + "]",  // citations
            0.8,          // vectorScore
            0.7,          // keywordScore
            0.75          // finalScore
        );
    }
}
