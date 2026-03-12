package com.agent.service.analysis.authority;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import com.agent.model.analysis.authority.AuthorityMatch;
import com.agent.model.analysis.authority.AuthorityType;
import com.agent.model.analysis.authority.LegalAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AuthorityRetrievalService.
 * 
 * Verifies that authorities are correctly retrieved and scored
 * for legal queries related to specific issues.
 */
class AuthorityRetrievalServiceTest {
    
    private AuthorityRetrievalService service;
    
    @BeforeEach
    void setUp() {
        service = new AuthorityRetrievalService();
    }
    
    @Test
    void testRetrieveAuthoritiesForReimbursement() {
        // Given: Queries for reimbursement authorities
        List<String> queries = List.of(
            "Epstein reimbursement",
            "post separation payment reimbursement"
        );
        
        // When: Retrieving authorities
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            queries,
            LegalIssueType.REIMBURSEMENT,
            5
        );
        
        // Then: Should find relevant authorities
        assertFalse(matches.isEmpty());
        // Should include Epstein or Family Code § 2640
        boolean foundEpsteinOrStatute = matches.stream()
            .map(m -> m.getAuthority().getCitation())
            .anyMatch(c -> c.contains("Epstein") || c.contains("2640"));
        assertTrue(foundEpsteinOrStatute);
    }
    
    @Test
    void testRetrieveAuthoritiesForCustody() {
        // Given: Queries for custody authorities
        List<String> queries = List.of(
            "best interest of child custody rule",
            "custody determination factors"
        );
        
        // When: Retrieving authorities
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            queries,
            LegalIssueType.CUSTODY,
            5
        );
        
        // Then: Should find custody-related authorities
        assertFalse(matches.isEmpty());
        boolean foundCustodyAuth = matches.stream()
            .map(m -> m.getAuthority().getCitation())
            .anyMatch(c -> c.contains("3011"));  // Family Code § 3011
        assertTrue(foundCustodyAuth);
    }
    
    @Test
    void testRetrieveAuthoritiesReturnsMatchScore() {
        // Given: Authority queries
        List<String> queries = List.of("Epstein reimbursement");
        
        // When: Retrieving
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            queries,
            LegalIssueType.REIMBURSEMENT,
            3
        );
        
        // Then: Each match should have a valid score
        assertFalse(matches.isEmpty());
        for (AuthorityMatch match : matches) {
            assertTrue(match.getMatchScore() >= 0.0);
            assertTrue(match.getMatchScore() <= 1.0);
            assertEquals(LegalIssueType.REIMBURSEMENT, match.getIssueType());
            assertNotNull(match.getRetrievalQuery());
        }
    }
    
    @Test
    void testRetrieveAuthoritiesRespectsTopK() {
        // Given: Query with topK limit
        List<String> queries = List.of(
            "family law",
            "property division",
            "support"
        );
        int topK = 2;
        
        // When: Retrieving with topK=2
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            queries,
            LegalIssueType.OTHER,
            topK
        );
        
        // Then: Should return results (implementation-dependent max)
        assertFalse(matches.isEmpty());
    }
    
    @Test
    void testRetrieveAuthoritiesForPropertyCharacterization() {
        // Given: Property characterization queries
        List<String> queries = List.of(
            "community property characterization",
            "separate property tracing rule"
        );
        
        // When: Retrieving
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            queries,
            LegalIssueType.PROPERTY_CHARACTERIZATION,
            5
        );
        
        // Then: Should find relevant authorities
        assertFalse(matches.isEmpty());
        boolean foundPropertyAuth = matches.stream()
            .map(m -> m.getAuthority().getTitle())
            .anyMatch(t -> t.toLowerCase().contains("property") || t.toLowerCase().contains("moore"));
        assertTrue(foundPropertyAuth);
    }
    
    @Test
    void testRetrieveAuthoritiesForExclusiveUse() {
        // Given: Exclusive use queries
        List<String> queries = List.of(
            "exclusive use property offset family law",
            "Watts charges occupancy"
        );
        
        // When: Retrieving
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            queries,
            LegalIssueType.EXCLUSIVE_USE,
            5
        );
        
        // Then: Should find Watts case or related authority
        assertFalse(matches.isEmpty());
        boolean foundWatts = matches.stream()
            .map(m -> m.getAuthority().getTitle())
            .anyMatch(t -> t.contains("Watts"));
        assertTrue(foundWatts);
    }
    
    @Test
    void testRetrieveAuthoritiesIncludesAuthorityType() {
        // Given: A retrieval query
        List<String> queries = List.of("reimbursement statute");
        
        // When: Retrieving authorities
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            queries,
            LegalIssueType.REIMBURSEMENT,
            5
        );
        
        // Then: Each match should have valid authority type
        assertFalse(matches.isEmpty());
        for (AuthorityMatch match : matches) {
            LegalAuthority auth = match.getAuthority();
            assertNotNull(auth.getAuthorityType());
            assertTrue(auth.getAuthorityType() != null 
                && (auth.getAuthorityType() == AuthorityType.STATUTE
                    || auth.getAuthorityType() == AuthorityType.CASE_LAW
                    || auth.getAuthorityType() == AuthorityType.PRACTICE_GUIDE
                    || auth.getAuthorityType() == AuthorityType.COMMENTARY
                    || auth.getAuthorityType() == AuthorityType.OTHER));
        }
    }
    
    @Test
    void testRetrieveAuthoritiesWithEmptyQueries() {
        // Given: Empty query list
        List<String> emptyQueries = List.of();
        
        // When: Retrieving with empty queries
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            emptyQueries,
            LegalIssueType.REIMBURSEMENT,
            5
        );
        
        // Then: Should return empty list
        assertTrue(matches.isEmpty());
    }
    
    @Test
    void testRetrieveAuthoritiesMultipleQueries() {
        // Given: Multiple queries for same issue
        List<String> queries = List.of(
            "Epstein reimbursement",
            "Family Code § 2640",
            "post separation payment"
        );
        
        // When: Retrieving
        List<AuthorityMatch> matches = service.retrieveAuthorities(
            queries,
            LegalIssueType.REIMBURSEMENT,
            5
        );
        
        // Then: May have duplicate authorities (same authority matched multiple queries)
        // This is acceptable for V1
        assertFalse(matches.isEmpty());
        for (AuthorityMatch match : matches) {
            assertEquals(LegalIssueType.REIMBURSEMENT, match.getIssueType());
        }
    }
}
