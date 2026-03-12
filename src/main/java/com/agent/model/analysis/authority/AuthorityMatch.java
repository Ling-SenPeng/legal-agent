package com.agent.model.analysis.authority;

import com.agent.model.analysis.LegalIssueType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a match between an issue and a legal authority.
 */
public class AuthorityMatch {
    @JsonProperty
    private final LegalIssueType issueType;
    
    @JsonProperty
    private final LegalAuthority authority;
    
    @JsonProperty
    private final double matchScore;
    
    @JsonProperty
    private final String retrievalQuery;

    public AuthorityMatch(
        LegalIssueType issueType,
        LegalAuthority authority,
        double matchScore,
        String retrievalQuery
    ) {
        this.issueType = issueType;
        this.authority = authority;
        this.matchScore = matchScore;
        this.retrievalQuery = retrievalQuery;
    }

    public LegalIssueType getIssueType() {
        return issueType;
    }

    public LegalAuthority getAuthority() {
        return authority;
    }

    public double getMatchScore() {
        return matchScore;
    }

    public String getRetrievalQuery() {
        return retrievalQuery;
    }

    @Override
    public String toString() {
        return "AuthorityMatch{" +
                "issueType=" + issueType +
                ", authority=" + authority.getCitation() +
                ", matchScore=" + String.format("%.2f", matchScore) +
                '}';
    }
}
