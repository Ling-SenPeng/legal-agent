package com.agent.service.authority;

import com.agent.config.AuthorityServiceProperties;
import com.agent.model.analysis.authority.AuthorityType;
import com.agent.model.analysis.authority.RetrievedAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HttpAuthorityClient Tests")
class HttpAuthorityClientTest {
    
    private AuthorityServiceProperties properties;
    
    @BeforeEach
    void setUp() {
        properties = new AuthorityServiceProperties();
        properties.setBaseUrl("http://localhost:8081");
        properties.setTimeoutMs(10000);
    }
    
    @Test
    @DisplayName("searchAuthorities handles empty query gracefully")
    void testSearchAuthoritiesHandlesEmptyQuery() {
        // Create a test client with a WebClient builder
        var webClientBuilder = org.springframework.web.reactive.function.client.WebClient.builder();
        var client = new HttpAuthorityClient(webClientBuilder, properties);
        
        List<RetrievedAuthority> results = client.searchAuthorities("", 5);
        
        assertTrue(results.isEmpty(), "Should return empty list for empty query");
    }
    
    @Test
    @DisplayName("findAuthoritiesByTopic handles empty topic gracefully")
    void testFindAuthoritiesByTopicHandlesEmptyTopic() {
        var webClientBuilder = org.springframework.web.reactive.function.client.WebClient.builder();
        var client = new HttpAuthorityClient(webClientBuilder, properties);
        
        List<RetrievedAuthority> results = client.findAuthoritiesByTopic("");
        
        assertTrue(results.isEmpty(), "Should return empty list for empty topic");
    }
    
    @Test
    @DisplayName("findRelevantAuthorities handles empty inputs gracefully")
    void testFindRelevantAuthoritiesHandlesEmptyInputs() {
        var webClientBuilder = org.springframework.web.reactive.function.client.WebClient.builder();
        var client = new HttpAuthorityClient(webClientBuilder, properties);
        
        // With empty query and empty topics
        List<RetrievedAuthority> results = client.findRelevantAuthorities("", Arrays.asList());
        assertTrue(results.isEmpty(), "Should return empty list for empty inputs");
        
        // With null inputs
        results = client.findRelevantAuthorities(null, null);
        assertTrue(results.isEmpty(), "Should return empty list for null inputs");
    }
    
    @Test
    @DisplayName("System gracefully handles HTTP service unavailability")
    void testSystemHandlesServiceUnavailability() {
        var webClientBuilder = org.springframework.web.reactive.function.client.WebClient.builder();
        var client = new HttpAuthorityClient(webClientBuilder, properties);
        
        // Service is likely unavailable on localhost:8081, so should return empty
        List<RetrievedAuthority> results = client.searchAuthorities("test query", 5);
        
        // Should never return null
        assertTrue(results != null, "Should never return null");
        // In reality, will be empty because service is not available
        assertTrue(results.isEmpty(), "Should return empty when service is unavailable");
    }
    
    @Test
    @DisplayName("RetrievedAuthority DTO properties are correctly set")
    void testRetrievedAuthorityDto() {
        RetrievedAuthority authority = new RetrievedAuthority(
            "case-001",
            "Marriage of Smith",
            "100 Cal.App.3d 100",
            AuthorityType.CASE_LAW,
            "Court held that reimbursement is available...",
            0.95
        );
        
        assertEquals("case-001", authority.getAuthorityId());
        assertEquals("Marriage of Smith", authority.getTitle());
        assertEquals("100 Cal.App.3d 100", authority.getCitation());
        assertEquals(AuthorityType.CASE_LAW, authority.getAuthorityType());
        assertEquals("Court held that reimbursement is available...", authority.getRuleSummary());
        assertEquals(0.95, authority.getRelevanceScore());
    }
    
    @Test
    @DisplayName("RetrievedAuthority equals and hashCode work correctly")
    void testRetrievedAuthorityEquality() {
        RetrievedAuthority authority1 = new RetrievedAuthority(
            "case-001",
            "Marriage of Smith",
            "100 Cal.App.3d 100",
            AuthorityType.CASE_LAW,
            "Court held...",
            0.95
        );
        
        RetrievedAuthority authority2 = new RetrievedAuthority(
            "case-001",
            "Marriage of Smith",
            "100 Cal.App.3d 100",
            AuthorityType.CASE_LAW,
            "Court held...",
            0.95
        );
        
        RetrievedAuthority authority3 = new RetrievedAuthority(
            "case-002",
            "Marriage of Jones",
            "200 Cal.App.3d 200",
            AuthorityType.CASE_LAW,
            "Court held...",
            0.90
        );
        
        // Same ID should be equal
        assertEquals(authority1, authority2);
        assertEquals(authority1.hashCode(), authority2.hashCode());
        
        // Different ID should not be equal
        assertNotEquals(authority1, authority3);
    }
}
