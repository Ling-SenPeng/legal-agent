package com.agent.model.analysis;

/**
 * Represents a key fact relevant to case analysis.
 * 
 * Facts are extracted from evidence and used to support
 * or undermine legal positions in case analysis.
 */
public class CaseFact {
    private final String description;
    private final boolean favorable;  // true if favorable to client, false if adverse
    private final String sourceReference;  // Reference to evidence chunk or document
    private final LegalIssueType relevantIssue;  // Issue this fact relates to

    public CaseFact(String description, boolean favorable, String sourceReference, LegalIssueType relevantIssue) {
        this.description = description;
        this.favorable = favorable;
        this.sourceReference = sourceReference;
        this.relevantIssue = relevantIssue;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFavorable() {
        return favorable;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public LegalIssueType getRelevantIssue() {
        return relevantIssue;
    }

    @Override
    public String toString() {
        return "CaseFact{" +
                "description='" + description + '\'' +
                ", favorable=" + favorable +
                ", issue=" + relevantIssue +
                ", source='" + sourceReference + '\'' +
                '}';
    }
}
