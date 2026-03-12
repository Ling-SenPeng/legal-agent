package com.agent.service.analysis.authority;

import com.agent.model.analysis.LegalIssueType;
import com.agent.model.analysis.authority.AuthorityMatch;
import com.agent.model.analysis.authority.AuthorityType;
import com.agent.model.analysis.authority.LegalAuthority;
import com.agent.model.analysis.authority.RetrievedAuthority;
import com.agent.service.authority.AuthorityClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for retrieving legal authorities (statutes, cases, practice guides) related to issues.
 * 
 * V2 Implementation: Uses HttpAuthorityClient to fetch from authority-ingest service.
 * Falls back to mock authorities if service is unavailable.
 */
@Service
public class AuthorityRetrievalService {
    private static final Logger logger = LoggerFactory.getLogger(AuthorityRetrievalService.class);
    
    private final Optional<AuthorityClient> authorityClient;
    private static final List<LegalAuthority> MOCK_AUTHORITIES = initializeMockAuthorities();

    public AuthorityRetrievalService(Optional<AuthorityClient> authorityClient) {
        this.authorityClient = authorityClient;
    }

    private static List<LegalAuthority> initializeMockAuthorities() {
        List<LegalAuthority> authorities = new ArrayList<>();
        
        // REIMBURSEMENT authorities
        authorities.add(new LegalAuthority(
            "case-epstein-001",
            "Marriage of Epstein",
            "191 Cal.App.3d 592",
            AuthorityType.CASE_LAW,
            "California Appellate Court",
            "A spouse who uses separate property to pay community obligations after separation may be entitled to reimbursement with interest.",
            0.95
        ));
        
        authorities.add(new LegalAuthority(
            "statute-fam-2640",
            "California Family Code § 2640",
            "Cal. Fam. Code § 2640",
            AuthorityType.STATUTE,
            "California Legislature",
            "Provides mechanism for reimbursement of separate property contributions to community property.",
            0.92
        ));
        
        // SUPPORT authorities
        authorities.add(new LegalAuthority(
            "statute-fam-4320",
            "California Family Code § 4320",
            "Cal. Fam. Code § 4320",
            AuthorityType.STATUTE,
            "California Legislature",
            "Enumerates factors court must consider in determining spousal support, including non-remunerative service to family.",
            0.88
        ));
        
        // EXCLUSIVE_USE authorities
        authorities.add(new LegalAuthority(
            "case-watts-001",
            "Marriage of Watts",
            "171 Cal.App.3d 366",
            AuthorityType.CASE_LAW,
            "California Appellate Court",
            "Court may award exclusive use and occupancy of family home with offset against other assets as alternative to sale.",
            0.90
        ));
        
        // PROPERTY_CHARACTERIZATION authorities
        authorities.add(new LegalAuthority(
            "statute-fam-750",
            "California Family Code § 750",
            "Cal. Fam. Code § 750",
            AuthorityType.STATUTE,
            "California Legislature",
            "Establishes that community property consists of all property acquired by either spouse during marriage except separate property.",
            0.94
        ));
        
        authorities.add(new LegalAuthority(
            "case-moore-001",
            "Marriage of Moore",
            "28 Cal.4th 366",
            AuthorityType.CASE_LAW,
            "California Supreme Court",
            "Definitive guidance on separate property characterization and transmutation requirements in family law.",
            0.96
        ));
        
        // TRACING authorities
        authorities.add(new LegalAuthority(
            "practice-guide-tracing",
            "Family Law Practice Guide: Property Tracing",
            "CEB Family Law Practice Guide",
            AuthorityType.PRACTICE_GUIDE,
            "Continuing Education of the Bar",
            "Comprehensive guide to tracing methodologies, source of funds analysis, and presumptions in family law property division.",
            0.85
        ));
        
        // CUSTODY authorities
        authorities.add(new LegalAuthority(
            "statute-fam-3011",
            "California Family Code § 3011",
            "Cal. Fam. Code § 3011",
            AuthorityType.STATUTE,
            "California Legislature",
            "Requires courts to consider best interest of child including health, safety, welfare; parent's history of abuse; and custody arrangement preferences.",
            0.93
        ));
        
        // RESTRAINING_ORDER authorities
        authorities.add(new LegalAuthority(
            "statute-fam-6320",
            "California Family Code § 6320",
            "Cal. Fam. Code § 6320",
            AuthorityType.STATUTE,
            "California Legislature",
            "Authorizes protective orders in domestic violence cases; requires showing of abuse or credible threat of abuse.",
            0.91
        ));
        
        return authorities;
    }

    /**
     * Retrieve authorities for a list of queries.
     * 
     * V1 Implementation: Returns mock authorities that match the query context.
     * Scores are based on keyword overlap and predefined relevance.
     * 
     * @param queries List of authority retrieval queries
     * @param topK Maximum number of authorities to return per query
     * @return List of AuthorityMatch objects
     */
    public List<AuthorityMatch> retrieveAuthorities(List<String> queries, LegalIssueType issueType, int topK) {
        logger.debug("AuthorityRetrievalService: Retrieving authorities for {} queries, issue type: {}", 
            queries.size(), issueType);
        
        List<AuthorityMatch> matches = new ArrayList<>();
        
        for (String query : queries) {
            logger.debug("AuthorityRetrievalService: Processing query: '{}'", query);
            
            // Score all mock authorities against this query
            List<LegalAuthority> scoredAuthorities = scoreAuthoritiesAgainstQuery(query, topK);
            
            for (LegalAuthority authority : scoredAuthorities) {
                double matchScore = calculateMatchScore(query, authority);
                
                AuthorityMatch match = new AuthorityMatch(
                    issueType,
                    authority,
                    matchScore,
                    query
                );
                
                matches.add(match);
                logger.debug("AuthorityRetrievalService: Found match - {} (score: {:.2f})", 
                    authority.getCitation(), String.format("%.2f", matchScore));
            }
        }
        
        logger.info("AuthorityRetrievalService: Retrieved {} authority matches for issue type: {}", 
            matches.size(), issueType);
        
        return matches;
    }

    /**
     * Score authorities against a query and return top K.
     * 
     * @param query The authority retrieval query
     * @param topK Number of top authorities to return
     * @return Top K authorities ranked by relevance
     */
    private List<LegalAuthority> scoreAuthoritiesAgainstQuery(String query, int topK) {
        return MOCK_AUTHORITIES.stream()
            .sorted((a1, a2) -> Double.compare(
                calculateMatchScore(query, a2),
                calculateMatchScore(query, a1)
            ))
            .limit(topK)
            .toList();
    }

    /**
     * Calculate match score between query and authority.
     * 
     * Simple keyword matching for V1:
     * - Exact citation match: 1.0
     * - Title keyword match: 0.8-0.9 based on match count
     * - Authority's own relevance score: 0.5-1.0
     * 
     * @param query The retrieval query
     * @param authority The authority to score
     * @return Match score [0.0, 1.0]
     */
    private double calculateMatchScore(String query, LegalAuthority authority) {
        String queryLower = query.toLowerCase();
        String titleLower = authority.getTitle().toLowerCase();
        String citationLower = authority.getCitation().toLowerCase();
        
        // Exact citation match
        if (citationLower.contains(queryLower) || queryLower.contains(citationLower)) {
            return 0.95;
        }
        
        // Title word matching
        String[] queryWords = queryLower.split("\\s+");
        int matchedWords = 0;
        for (String word : queryWords) {
            if (word.length() > 2 && titleLower.contains(word)) {
                matchedWords++;
            }
        }
        
        if (matchedWords == 0) {
            return authority.getRelevanceScore() * 0.5;
        }
        
        double wordMatchRatio = (double) matchedWords / queryWords.length;
        return Math.min(0.9, authority.getRelevanceScore() * (0.6 + wordMatchRatio * 0.4));
    }

    /**
     * Retrieve authorities from the HTTP service using query and topics.
     * 
     * Attempts to use HttpAuthorityClient if available. Falls back to mock authorities
     * if the service is not available or returns no results.
     * 
     * @param query The search query
     * @param topics List of topics to search
     * @return List of LegalAuthority objects from either HTTP service or mocks
     */
    public List<LegalAuthority> retrieveAuthoritiesFromService(String query, List<String> topics) {
        logger.debug("Attempting to retrieve authorities via HTTP service for query: '{}', topics: {}", 
            query, topics);
        
        if (authorityClient.isPresent()) {
            try {
                List<RetrievedAuthority> retrieved = authorityClient.get()
                    .findRelevantAuthorities(query, topics);
                
                if (!retrieved.isEmpty()) {
                    logger.info("Retrieved {} authorities from HTTP service", retrieved.size());
                    
                    List<LegalAuthority> converted = new ArrayList<>();
                    for (RetrievedAuthority ra : retrieved) {
                        LegalAuthority authority = new LegalAuthority(
                            ra.getAuthorityId(),
                            ra.getTitle(),
                            ra.getCitation(),
                            ra.getAuthorityType(),
                            "Authority Service",
                            ra.getRuleSummary(),
                            ra.getRelevanceScore()
                        );
                        converted.add(authority);
                    }
                    return converted;
                } else {
                    logger.debug("HTTP service returned no authorities, falling back to mocks");
                }
            } catch (Exception e) {
                logger.warn("Error retrieving authorities from HTTP service, falling back to mocks: {}", 
                    e.getMessage());
            }
        }
        
        // Fallback to mocks
        logger.debug("Using fallback mock authorities");
        return MOCK_AUTHORITIES;
    }
}

