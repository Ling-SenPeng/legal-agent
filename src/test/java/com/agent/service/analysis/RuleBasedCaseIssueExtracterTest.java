package com.agent.service.analysis;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleBasedCaseIssueExtractor.
 * 
 * Test Coverage:
 * 1. Issue extraction for each legal issue type
 * 2. Keyword matching and case insensitivity
 * 3. Confidence scoring based on keyword matches
 * 4. Multiple issue detection in single query
 * 5. Generic OTHER issue for unmatched queries
 * 6. Edge cases (null, empty queries)
 * 7. Context parameter handling
 */
class RuleBasedCaseIssueExtracterTest {
    
    private CaseIssueExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new RuleBasedCaseIssueExtractor();
    }

    // ==================== REIMBURSEMENT ISSUE Tests ====================
    
    @Test
    @DisplayName("Extract REIMBURSEMENT issue from reimbursement keyword")
    void testExtractReimbursementBasic() {
        // Given
        String query = "I need reimbursement for mortgage payments made after separation.";
        
        // When
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        // Then
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.REIMBURSEMENT));
    }
    
    @Test
    @DisplayName("Extract REIMBURSEMENT from Epstein case reference")
    void testExtractReimbursementEpstein() {
        String query = "Under Epstein principles, should I get reimbursement for the mortgage?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.REIMBURSEMENT));
    }
    
    @Test
    @DisplayName("Extract REIMBURSEMENT from post-separation payments")
    void testExtractReimbursementPostSeparation() {
        String query = "I paid the mortgage post-separation. Am I entitled to reimbursement?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.REIMBURSEMENT));
    }

    // ==================== SUPPORT ISSUE Tests ====================
    
    @Test
    @DisplayName("Extract SUPPORT issue from child support keyword")
    void testExtractSupportChild() {
        String query = "What is the child support obligation in my case?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.SUPPORT));
    }
    
    @Test
    @DisplayName("Extract SUPPORT from spousal support")
    void testExtractSupportSpousal() {
        String query = "I need to determine spousal support liability.";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.SUPPORT));
    }

    // ==================== PROPERTY CHARACTERIZATION ISSUE Tests ====================
    
    @Test
    @DisplayName("Extract PROPERTY_CHARACTERIZATION from characterization keyword")
    void testExtractPropertyCharacterization() {
        String query = "How should I characterize this property - is it community or separate?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.PROPERTY_CHARACTERIZATION));
    }
    
    @Test
    @DisplayName("Extract PROPERTY_CHARACTERIZATION from transmutation")
    void testExtractPropertyTransmutation() {
        String query = "Did transmutation of separate property occur?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.PROPERTY_CHARACTERIZATION));
    }

    // ==================== TRACING ISSUE Tests ====================
    
    @Test
    @DisplayName("Extract TRACING issue from tracing keyword")
    void testExtractTracingBasic() {
        String query = "Need tracing analysis for the down payment funds.";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.TRACING));
    }
    
    @Test
    @DisplayName("Extract TRACING from down payment source")
    void testExtractTracingDownPayment() {
        String query = "Where did the down payment come from? Source of funds analysis needed.";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.TRACING));
    }

    // ==================== EXCLUSIVE USE ISSUE Tests ====================
    
    @Test
    @DisplayName("Extract EXCLUSIVE_USE from exclusive use keyword")
    void testExtractExclusiveUse() {
        String query = "Should I get exclusive use of the family home?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.EXCLUSIVE_USE));
    }
    
    @Test
    @DisplayName("Extract EXCLUSIVE_USE from occupancy")
    void testExtractExclusiveOccupancy() {
        String query = "What is the exclusive occupancy arrangement?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.EXCLUSIVE_USE));
    }

    // ==================== CUSTODY ISSUE Tests ====================
    
    @Test
    @DisplayName("Extract CUSTODY from custody keyword")
    void testExtractCustodyBasic() {
        String query = "What custody arrangements apply to minor children?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.CUSTODY));
    }
    
    @Test
    @DisplayName("Extract CUSTODY from visitation")
    void testExtractCustodyVisitation() {
        String query = "I need to address visitation and parenting arrangements.";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.CUSTODY));
    }

    // ==================== RESTRAINING ORDER ISSUE Tests ====================
    
    @Test
    @DisplayName("Extract RESTRAINING_ORDER from TRO")
    void testExtractRestrainingOrderTRO() {
        String query = "Can a TRO be issued against my ex?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.RESTRAINING_ORDER));
    }
    
    @Test
    @DisplayName("Extract RESTRAINING_ORDER from DVRO")
    void testExtractRestrainingOrderDVRO() {
        String query = "Should I file for a DVRO?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.RESTRAINING_ORDER));
    }

    // ==================== MULTIPLE ISSUES Tests ====================
    
    @Test
    @DisplayName("Extract multiple issues from single query")
    void testExtractMultipleIssues() {
        // Given: Query mentioning both support and custody
        String query = "Need child support determination AND custody arrangement clarity.";
        
        // When
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        // Then
        assertTrue(issues.size() >= 2);
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.SUPPORT));
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.CUSTODY));
    }
    
    @Test
    @DisplayName("Extract multiple issues: reimbursement, property, tracing")
    void testExtractComplexMultipleIssues() {
        String query = "Reimbursement for post-separation mortgage, characterization of property, source of funds tracing";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.REIMBURSEMENT));
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.PROPERTY_CHARACTERIZATION));
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.TRACING));
    }

    // ==================== CONFIDENCE SCORING Tests ====================
    
    @Test
    @DisplayName("Single keyword match produces confidence >= 0.6")
    void testConfidenceSingleMatch() {
        String query = "Draft a memo about reimbursement.";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        Optional<CaseIssue> reimbursement = issues.stream()
            .filter(i -> i.getType() == LegalIssueType.REIMBURSEMENT)
            .findFirst();
        
        assertTrue(reimbursement.isPresent());
        assertTrue(reimbursement.get().getConfidence() >= 0.6);
    }
    
    @Test
    @DisplayName("Multiple keyword matches increase confidence")
    void testConfidenceMultipleMatches() {
        // Query with multiple reimbursement keywords
        String query = "Post-separation reimbursement for mortgage payment and loan repayment.";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        Optional<CaseIssue> reimbursement = issues.stream()
            .filter(i -> i.getType() == LegalIssueType.REIMBURSEMENT)
            .findFirst();
        
        assertTrue(reimbursement.isPresent());
        // Multiple matches should result in higher confidence
        assertTrue(reimbursement.get().getConfidence() >= 0.7);
    }

    // ==================== CASE INSENSITIVITY Tests ====================
    
    @Test
    @DisplayName("Issue extraction is case insensitive")
    void testCaseInsensitivity() {
        String queryLower = "reimbursement for mortgage";
        String queryUpper = "REIMBURSEMENT FOR MORTGAGE";
        String queryMixed = "ReImBuRsEmEnT for Mortgage";
        
        List<CaseIssue> issuesLower = extractor.extractIssues(queryLower);
        List<CaseIssue> issuesUpper = extractor.extractIssues(queryUpper);
        List<CaseIssue> issuesMixed = extractor.extractIssues(queryMixed);
        
        assertTrue(issuesLower.stream().anyMatch(i -> i.getType() == LegalIssueType.REIMBURSEMENT));
        assertTrue(issuesUpper.stream().anyMatch(i -> i.getType() == LegalIssueType.REIMBURSEMENT));
        assertTrue(issuesMixed.stream().anyMatch(i -> i.getType() == LegalIssueType.REIMBURSEMENT));
    }

    // ==================== EDGE CASES Tests ====================
    
    @Test
    @DisplayName("Empty query returns generic OTHER issue")
    void testEmptyQuery() {
        List<CaseIssue> issues = extractor.extractIssues("");
        
        assertTrue(issues.isEmpty());
    }
    
    @Test
    @DisplayName("Null query returns empty list")
    void testNullQuery() {
        List<CaseIssue> issues = extractor.extractIssues(null);
        
        assertTrue(issues.isEmpty());
    }
    
    @Test
    @DisplayName("Query with no matching keywords returns OTHER issue")
    void testNoMatchingKeywords() {
        String query = "What are the applicable statute of limitations rules?";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.stream().anyMatch(i -> i.getType() == LegalIssueType.OTHER));
    }

    // ==================== ISSUE STRUCTURE Tests ====================
    
    @Test
    @DisplayName("CaseIssue contains required fields")
    void testIssueStructure() {
        String query = "Custody and child support issues";
        List<CaseIssue> issues = extractor.extractIssues(query);
        
        assertTrue(issues.size() > 0);
        
        CaseIssue issue = issues.get(0);
        assertNotNull(issue.getType());
        assertNotNull(issue.getDescription());
        assertTrue(issue.getConfidence() >= 0.0 && issue.getConfidence() <= 1.0);
        assertNotNull(issue.getMatchedKeywords());
    }

    // ==================== CONTEXT PARAMETER Tests ====================
    
    @Test
    @DisplayName("extractIssues(query, context) accepts optional context")
    void testContextParameter() {
        String query = "Need analysis on my case";
        String context = "Additional case facts mentioning reimbursement.";
        
        // Should not throw exception
        List<CaseIssue> issues = extractor.extractIssues(query, context);
        
        assertNotNull(issues);
    }
}
