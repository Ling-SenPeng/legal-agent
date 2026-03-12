package com.agent.model.analysis.authority;

import com.agent.model.analysis.LegalIssueType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Summary of authorities retrieved for a specific legal issue.
 * 
 * Contains the issue type, retrieved authorities, and a synthesized
 * rule summary describing the legal principle.
 */
public class AuthoritySummary {
    @JsonProperty
    private final LegalIssueType issueType;
    
    @JsonProperty
    private final int authorityCount;
    
    @JsonProperty
    private final String summarizedRule;
    
    @JsonProperty
    private final List<LegalAuthority> authorities;

    public AuthoritySummary(
        LegalIssueType issueType,
        int authorityCount,
        String summarizedRule,
        List<LegalAuthority> authorities
    ) {
        this.issueType = issueType;
        this.authorityCount = authorityCount;
        this.summarizedRule = summarizedRule;
        this.authorities = authorities;
    }

    public LegalIssueType getIssueType() {
        return issueType;
    }

    public int getAuthorityCount() {
        return authorityCount;
    }

    public String getSummarizedRule() {
        return summarizedRule;
    }

    public List<LegalAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String toString() {
        return "AuthoritySummary{" +
                "issueType=" + issueType +
                ", authorityCount=" + authorityCount +
                ", authorities=" + authorities.size() +
                '}';
    }
}
