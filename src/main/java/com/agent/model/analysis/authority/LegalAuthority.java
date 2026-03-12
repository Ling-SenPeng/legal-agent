package com.agent.model.analysis.authority;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a legal authority (statute, case, practice guide, etc.).
 * 
 * Used to attach relevant legal authorities to identified legal issues.
 */
public class LegalAuthority {
    @JsonProperty
    private final String authorityId;
    
    @JsonProperty
    private final String title;
    
    @JsonProperty
    private final String citation;
    
    @JsonProperty
    private final AuthorityType authorityType;
    
    @JsonProperty
    private final String source;
    
    @JsonProperty
    private final String summary;
    
    @JsonProperty
    private final double relevanceScore;

    public LegalAuthority(
        String authorityId,
        String title,
        String citation,
        AuthorityType authorityType,
        String source,
        String summary,
        double relevanceScore
    ) {
        this.authorityId = authorityId;
        this.title = title;
        this.citation = citation;
        this.authorityType = authorityType;
        this.source = source;
        this.summary = summary;
        this.relevanceScore = relevanceScore;
    }

    public String getAuthorityId() {
        return authorityId;
    }

    public String getTitle() {
        return title;
    }

    public String getCitation() {
        return citation;
    }

    public AuthorityType getAuthorityType() {
        return authorityType;
    }

    public String getSource() {
        return source;
    }

    public String getSummary() {
        return summary;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    @Override
    public String toString() {
        return "LegalAuthority{" +
                "authorityId='" + authorityId + '\'' +
                ", title='" + title + '\'' +
                ", citation='" + citation + '\'' +
                ", authorityType=" + authorityType +
                ", relevanceScore=" + String.format("%.2f", relevanceScore) +
                '}';
    }
}
