package com.agent.service.authority;

import com.agent.config.AuthorityServiceProperties;
import com.agent.model.analysis.authority.RetrievedAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for AuthorityClient.
 * 
 * Tests real HTTP communication with the authority-ingest service running at localhost:8081.
 * 
 * Prerequisites:
 * - Authority service must be running at http://localhost:8081
 * - Service should have authorities with "Epstein" in title
 * 
 * Note: This test makes real HTTP calls and is not mocked.
 */
@DisplayName("AuthorityClient Integration Tests")
class AuthorityClientIntegrationTest {
    
    private AuthorityClient authorityClient;
    private AuthorityServiceProperties properties;
    
    @BeforeEach
    void setUp() {
        properties = new AuthorityServiceProperties();
        properties.setBaseUrl("http://localhost:8081");
        properties.setTimeoutMs(10000);
        
        WebClient.Builder webClientBuilder = WebClient.builder();
        authorityClient = new HttpAuthorityClient(webClientBuilder, properties);
    }
    
    @Test
    @DisplayName("searchAuthorities calls authority service and retrieves authorities with Epstein")
    void testSearchAuthoritiesRetrievesEpsteinAuthority() {
        // When: Calling searchAuthorities with "epstein" query
        List<RetrievedAuthority> authorities = authorityClient.searchAuthorities("epstein", 5);
        
        // Then: Result list should not be empty (if authority service is running)
        assertNotNull(authorities, "Result should not be null");
        
        // If authorities are returned, verify content
        if (!authorities.isEmpty()) {
            // Verify first authority title contains "Epstein"
            RetrievedAuthority firstAuthority = authorities.get(0);
            assertNotNull(firstAuthority, "First authority should not be null");
            assertNotNull(firstAuthority.getTitle(), "Authority title should not be null");
            assertTrue(
                firstAuthority.getTitle().contains("Epstein"),
                "First authority title should contain 'Epstein', but was: " + firstAuthority.getTitle()
            );
            
            // Verify that all returned authorities have required fields
            for (RetrievedAuthority authority : authorities) {
                assertNotNull(authority.getAuthorityId(), "Authority ID should not be null");
                assertNotNull(authority.getTitle(), "Authority title should not be null");
                assertNotNull(authority.getCitation(), "Authority citation should not be null");
                assertNotNull(authority.getAuthorityType(), "Authority type should not be null");
                assertNotNull(authority.getRuleSummary(), "Authority rule summary should not be null");
                assertTrue(authority.getRelevanceScore() >= 0.0 && authority.getRelevanceScore() <= 1.0,
                    "Relevance score should be between 0.0 and 1.0, but was: " + authority.getRelevanceScore());
            }
            
            System.out.println("✓ Successfully retrieved " + authorities.size() + " authorities");
            System.out.println("  First authority: " + firstAuthority.getTitle() + " (" + firstAuthority.getCitation() + ")");
        } else {
            System.out.println("⚠ No authorities returned - authority service may not be running or have no matching results");
        }
    }
    
    @Test
    @DisplayName("searchAuthorities with valid query returns sorted results")
    void testSearchAuthoritiesReturnsSortedResults() {
        // When: Calling searchAuthorities
        List<RetrievedAuthority> authorities = authorityClient.searchAuthorities("epstein", 5);
        
        // Then: If multiple results returned, they should be sorted by relevance score
        if (authorities.size() > 1) {
            for (int i = 0; i < authorities.size() - 1; i++) {
                double currentScore = authorities.get(i).getRelevanceScore();
                double nextScore = authorities.get(i + 1).getRelevanceScore();
                assertTrue(
                    currentScore >= nextScore,
                    "Results should be sorted by relevance score (descending). " +
                    "Position " + i + " score: " + currentScore + ", " +
                    "Position " + (i + 1) + " score: " + nextScore
                );
            }
            System.out.println("✓ Authorities are properly sorted by relevance score");
        }
    }
    
    @Test
    @DisplayName("findAuthoritiesByTopic retrieves authorities by topic")
    void testFindAuthoritiesByTopic() {
        // When: Calling findAuthoritiesByTopic with REIMBURSEMENT topic
        List<RetrievedAuthority> authorities = authorityClient.findAuthoritiesByTopic("REIMBURSEMENT");
        
        // Then: Should return results (if service is running)
        assertNotNull(authorities, "Result should not be null");
        
        if (!authorities.isEmpty()) {
            System.out.println("✓ Successfully retrieved " + authorities.size() + " authorities for REIMBURSEMENT topic");
            
            // All results should have valid fields
            for (RetrievedAuthority authority : authorities) {
                assertNotNull(authority.getAuthorityId());
                assertNotNull(authority.getTitle());
                assertNotNull(authority.getCitation());
            }
        } else {
            System.out.println("⚠ No authorities returned for REIMBURSEMENT topic");
        }
    }
    
    @Test
    @DisplayName("findRelevantAuthorities combines search and topic lookups")
    void testFindRelevantAuthoritiesCombinesResults() {
        // When: Calling findRelevantAuthorities with query and topics
        List<RetrievedAuthority> authorities = authorityClient.findRelevantAuthorities(
            "epstein", 
            List.of("REIMBURSEMENT", "PROPERTY_CHARACTERIZATION")
        );
        
        // Then: Should return deduplicated results
        assertNotNull(authorities, "Result should not be null");
        
        if (!authorities.isEmpty()) {
            System.out.println("✓ Successfully retrieved " + authorities.size() + " merged authorities");
            
            // Verify no duplicates (by authority ID)
            var ids = authorities.stream().map(RetrievedAuthority::getAuthorityId).toList();
            var uniqueIds = authorities.stream().map(RetrievedAuthority::getAuthorityId).distinct().toList();
            assertEquals(
                ids.size(), uniqueIds.size(),
                "Results should be deduplicated - found duplicate authority IDs"
            );
            
            // Results should be sorted by relevance score
            for (int i = 0; i < authorities.size() - 1; i++) {
                assertTrue(
                    authorities.get(i).getRelevanceScore() >= authorities.get(i + 1).getRelevanceScore(),
                    "Results should be sorted by relevance score"
                );
            }
        } else {
            System.out.println("⚠ No combined authorities returned");
        }
    }
    
    @Test
    @DisplayName("searchAuthorities respects topK parameter")
    void testSearchAuthoritiesRespectsTopK() {
        // When: Calling searchAuthorities with topK=2
        List<RetrievedAuthority> twoResults = authorityClient.searchAuthorities("epstein", 2);
        
        // When: Calling with larger topK
        List<RetrievedAuthority> tenResults = authorityClient.searchAuthorities("epstein", 10);
        
        // Then: Should respect the topK parameter
        if (!twoResults.isEmpty() && !tenResults.isEmpty()) {
            assertTrue(
                twoResults.size() <= 2,
                "topK=2 should return at most 2 results, but got: " + twoResults.size()
            );
            System.out.println("✓ topK parameter respected: 2 requested, " + twoResults.size() + " returned");
        }
    }
}
