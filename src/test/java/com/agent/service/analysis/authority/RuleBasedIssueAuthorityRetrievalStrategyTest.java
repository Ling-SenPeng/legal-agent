package com.agent.service.analysis.authority;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RuleBasedIssueAuthorityRetrievalStrategy.
 * 
 * Verifies that authority retrieval queries are correctly generated
 * for each legal issue type.
 */
class RuleBasedIssueAuthorityRetrievalStrategyTest {
    
    private RuleBasedIssueAuthorityRetrievalStrategy strategy;
    
    @BeforeEach
    void setUp() {
        strategy = new RuleBasedIssueAuthorityRetrievalStrategy();
    }
    
    @Test
    void testBuildAuthorityQueriesForReimbursement() {
        // Given: A reimbursement issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.REIMBURSEMENT,
            "Seeking reimbursement for post-separation mortgage payments",
            0.85,
            "mortgage, payment, reimbursement"
        );
        
        // When: Building authority queries
        List<String> queries = strategy.buildAuthorityQueries(issue);
        
        // Then: Queries should include key authorities for reimbursement
        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("epstein")));
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("reimbursement")));
    }
    
    @Test
    void testBuildAuthorityQueriesForSupport() {
        // Given: A support issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.SUPPORT,
            "Determining spousal support obligation",
            0.75,
            "support, income"
        );
        
        // When: Building authority queries
        List<String> queries = strategy.buildAuthorityQueries(issue);
        
        // Then: Queries should address support obligations
        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("support")));
    }
    
    @Test
    void testBuildAuthorityQueriesForExclusiveUse() {
        // Given: An exclusive use issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.EXCLUSIVE_USE,
            "Requesting exclusive use of family home",
            0.80,
            "exclusive, use, home"
        );
        
        // When: Building authority queries
        List<String> queries = strategy.buildAuthorityQueries(issue);
        
        // Then: Queries should include exclusive use authorities
        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("exclusive")));
    }
    
    @Test
    void testBuildAuthorityQueriesForPropertyCharacterization() {
        // Given: A property characterization issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.PROPERTY_CHARACTERIZATION,
            "Determining characterization of property as community or separate",
            0.82,
            "property, community, characterization"
        );
        
        // When: Building authority queries
        List<String> queries = strategy.buildAuthorityQueries(issue);
        
        // Then: Queries should address property characterization
        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("property")));
    }
    
    @Test
    void testBuildAuthorityQueriesForTracing() {
        // Given: A tracing issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.TRACING,
            "Tracing separate property source",
            0.78,
            "tracing, source, separate"
        );
        
        // When: Building authority queries
        List<String> queries = strategy.buildAuthorityQueries(issue);
        
        // Then: Queries should include tracing methodologies
        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("trac")));
    }
    
    @Test
    void testBuildAuthorityQueriesForCustody() {
        // Given: A custody issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.CUSTODY,
            "Determining custody arrangement",
            0.88,
            "custody, child, best interest"
        );
        
        // When: Building authority queries
        List<String> queries = strategy.buildAuthorityQueries(issue);
        
        // Then: Queries should address custody standards
        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("custody")));
    }
    
    @Test
    void testBuildAuthorityQueriesForRestrainingOrder() {
        // Given: A restraining order issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.RESTRAINING_ORDER,
            "Obtaining domestic violence protective order",
            0.90,
            "restraining, protective, order"
        );
        
        // When: Building authority queries
        List<String> queries = strategy.buildAuthorityQueries(issue);
        
        // Then: Queries should include restraining order authorities
        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("order")));
    }
    
    @Test
    void testBuildAuthorityQueriesForOther() {
        // Given: An unclassified issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.OTHER,
            "Unclassified legal issue",
            0.50,
            "issue"
        );
        
        // When: Building authority queries
        List<String> queries = strategy.buildAuthorityQueries(issue);
        
        // Then: Should return fallback generic queries
        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(q -> q.toLowerCase().contains("law")));
    }
    
    @Test
    void testAuthorityQueriesAreNotEmpty() {
        // Given: Any legal issue
        for (LegalIssueType issueType : LegalIssueType.values()) {
            CaseIssue issue = new CaseIssue(issueType, "Test issue", 0.75, "test");
            
            // When: Building queries
            List<String> queries = strategy.buildAuthorityQueries(issue);
            
            // Then: Should always return non-empty list
            assertFalse(queries.isEmpty(), "No queries for issue type: " + issueType);
        }
    }
}
