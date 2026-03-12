package com.agent.model.analysis.authority;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a legal authority retrieved from the authority-ingest service.
 * 
 * This DTO is used to receive authority data from the REST API.
 */
public class RetrievedAuthority {
    @JsonProperty
    private String authorityId;
    
    @JsonProperty
    private String title;
    
    @JsonProperty
    private String citation;
    
    @JsonProperty
    private AuthorityType authorityType;
    
    @JsonProperty
    private String ruleSummary;
    
    @JsonProperty
    private double relevanceScore;

    // Default constructor for JSON deserialization
    public RetrievedAuthority() {
    }

    public RetrievedAuthority(
        String authorityId,
        String title,
        String citation,
        AuthorityType authorityType,
        String ruleSummary,
        double relevanceScore
    ) {
        this.authorityId = authorityId;
        this.title = title;
        this.citation = citation;
        this.authorityType = authorityType;
        this.ruleSummary = ruleSummary;
        this.relevanceScore = relevanceScore;
    }

    public String getAuthorityId() {
        return authorityId;
    }

    public void setAuthorityId(String authorityId) {
        this.authorityId = authorityId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCitation() {
        return citation;
    }

    public void setCitation(String citation) {
        this.citation = citation;
    }

    public AuthorityType getAuthorityType() {
        return authorityType;
    }

    public void setAuthorityType(AuthorityType authorityType) {
        this.authorityType = authorityType;
    }

    public String getRuleSummary() {
        return ruleSummary;
    }

    public void setRuleSummary(String ruleSummary) {
        this.ruleSummary = ruleSummary;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    @Override
    public String toString() {
        return "RetrievedAuthority{" +
                "authorityId='" + authorityId + '\'' +
                ", title='" + title + '\'' +
                ", citation='" + citation + '\'' +
                ", authorityType=" + authorityType +
                ", ruleSummary='" + ruleSummary + '\'' +
                ", relevanceScore=" + relevanceScore +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RetrievedAuthority)) return false;
        RetrievedAuthority that = (RetrievedAuthority) o;
        return authorityId != null && authorityId.equals(that.authorityId);
    }

    @Override
    public int hashCode() {
        return authorityId != null ? authorityId.hashCode() : 0;
    }
}
