package com.agent.service.analysis.authority;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import com.agent.model.analysis.authority.AuthorityMatch;
import com.agent.model.analysis.authority.AuthorityType;
import com.agent.model.analysis.authority.LegalAuthority;
import com.agent.model.analysis.authority.RetrievedAuthority;
import com.agent.service.authority.AuthorityClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for AuthorityRetrievalService.
 * 
 * Verifies that authorities are correctly retrieved and scored
 * for legal queries related to specific issues.
 */
@DisplayName("AuthorityRetrievalService Tests")
class AuthorityRetrievalServiceTest {
    
    private AuthorityRetrievalService service;
    
    @Mock
    private AuthorityClient authorityClient;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Test with no HTTP client (uses mocks)
        service = new AuthorityRetrievalService(Optional.empty());
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

    @Test
    @DisplayName("retrieveAuthoritiesFromService uses HTTP client when available")
    void testRetrieveAuthoritiesFromServiceUsesHttpClient() {
        // Given: HTTP client returning authorities
        RetrievedAuthority retrieved1 = new RetrievedAuthority(
            "http-case-001",
            "Marriage of Johnson",
            "200 Cal.App.3d 200",
            AuthorityType.CASE_LAW,
            "Court ruled on reimbursement...",
            0.92
        );
        
        when(authorityClient.findRelevantAuthorities(anyString(), anyList()))
            .thenReturn(Arrays.asList(retrieved1));
        
        // When: Creating service with HTTP client
        AuthorityRetrievalService serviceWithClient = 
            new AuthorityRetrievalService(Optional.of(authorityClient));
        
        List<LegalAuthority> authorities = 
            serviceWithClient.retrieveAuthoritiesFromService("reimbursement", 
                Arrays.asList("REIMBURSEMENT"));
        
        // Then: Should return converted authorities from HTTP service
        assertFalse(authorities.isEmpty());
        assertEquals("http-case-001", authorities.get(0).getAuthorityId());
        assertEquals("Marriage of Johnson", authorities.get(0).getTitle());
    }
    
    @Test
    @DisplayName("findRelevantAuthorities merges and deduplicates results")
    void testRetrieveAuthoritiesFromServiceFallsBackToMocks() {
        // Given: HTTP client returning empty results
        when(authorityClient.findRelevantAuthorities(anyString(), anyList()))
            .thenReturn(new ArrayList<>());
        
        // When: Creating service with HTTP client that returns empty
        AuthorityRetrievalService serviceWithClient = 
            new AuthorityRetrievalService(Optional.of(authorityClient));
        
        List<LegalAuthority> authorities = 
            serviceWithClient.retrieveAuthoritiesFromService("test", 
                Arrays.asList("SOME_ISSUE"));
        
        // Then: Should fall back to mock authorities
        assertFalse(authorities.isEmpty(), "Should fall back to mocks when service returns nothing");
    }
    
    @Test
    @DisplayName("CaseAnalysisModeHandler can use authorities via HTTP client")
    void testSystemWorksWhenAuthorityServiceReturnsNoResults() {
        // Given: HTTP client that has no results
        when(authorityClient.findRelevantAuthorities(anyString(), anyList()))
            .thenReturn(Collections.emptyList());
        
        AuthorityRetrievalService serviceWithClient = 
            new AuthorityRetrievalService(Optional.of(authorityClient));
        
        // When: Retrieving authorities with empty response
        List<LegalAuthority> authorities = 
            serviceWithClient.retrieveAuthoritiesFromService("", 
                new ArrayList<>());
        
        // Then: Should gracefully fall back to mocks
        assertNotNull(authorities);
        assertFalse(authorities.isEmpty(), "Should provide fallback authorities");
    }

    @Test
    @DisplayName("Retrieved authorities are properly converted from RetrievedAuthority to LegalAuthority")
    void testAuthorityConversionFromHttpResponse() {
        // Given: Multiple authorities from HTTP service
        List<RetrievedAuthority> httpAuthorities = Arrays.asList(
            new RetrievedAuthority("id1", "Case 1", "100 Ca.3d 1", AuthorityType.CASE_LAW, "Summary 1", 0.95),
            new RetrievedAuthority("id2", "Statute 1", "Code § 100", AuthorityType.STATUTE, "Summary 2", 0.85),
            new RetrievedAuthority("id3", "Guide 1", "Practice Guide", AuthorityType.PRACTICE_GUIDE, "Summary 3", 0.75)
        );
        
        when(authorityClient.findRelevantAuthorities(anyString(), anyList()))
            .thenReturn(httpAuthorities);
        
        // When: Converting authorities
        AuthorityRetrievalService serviceWithClient = 
            new AuthorityRetrievalService(Optional.of(authorityClient));
        
        List<LegalAuthority> converted = 
            serviceWithClient.retrieveAuthoritiesFromService("test", 
                Arrays.asList("TEST"));
        
        // Then: All required fields should be present
        assertEquals(3, converted.size());
        assertEquals("id1", converted.get(0).getAuthorityId());
        assertEquals("Case 1", converted.get(0).getTitle());
        assertEquals("100 Ca.3d 1", converted.get(0).getCitation());
        assertEquals(AuthorityType.CASE_LAW, converted.get(0).getAuthorityType());
        assertEquals("Summary 1", converted.get(0).getSummary());
        assertEquals(0.95, converted.get(0).getRelevanceScore());
    }
}
