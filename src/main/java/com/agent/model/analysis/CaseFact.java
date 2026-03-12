package com.agent.model.analysis;

/**
 * Represents a key fact relevant to case analysis.
 * 
 * Facts are extracted from evidence and used to support
 * or undermine legal positions in case analysis.
 */
public class CaseFact {
    private final String description;
    private final FactPolarity polarity;  // SUPPORTING, ADVERSE, NEUTRAL, or UNKNOWN
    private final String sourceReference;  // Reference to evidence chunk or document
    private final LegalIssueType relevantIssue;  // Issue this fact relates to

    public CaseFact(String description, FactPolarity polarity, String sourceReference, LegalIssueType relevantIssue) {
        this.description = description;
        this.polarity = polarity != null ? polarity : FactPolarity.UNKNOWN;
        this.sourceReference = sourceReference;
        this.relevantIssue = relevantIssue;
    }
    
    /**
     * Convenience constructor for backwards compatibility.
     * Converts boolean favorable to FactPolarity.
     */
    @Deprecated
    public CaseFact(String description, boolean favorable, String sourceReference, LegalIssueType relevantIssue) {
        this(description, 
            favorable ? FactPolarity.SUPPORTING : FactPolarity.ADVERSE,
            sourceReference, 
            relevantIssue
        );
    }

    public String getDescription() {
        return description;
    }

    public FactPolarity getPolarity() {
        return polarity;
    }
    
    /**
     * Check if this fact is favorable to the client's position.
     * Convenience method - equivalent to polarity == SUPPORTING.
     */
    public boolean isFavorable() {
        return polarity == FactPolarity.SUPPORTING;
    }
    
    /**
     * Check if this fact is adverse to the client's position.
     * Convenience method - equivalent to polarity == ADVERSE.
     */
    public boolean isAdverse() {
        return polarity == FactPolarity.ADVERSE;
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
                ", polarity=" + polarity +
                ", issue=" + relevantIssue +
                ", source='" + sourceReference + '\'' +
                '}';
    }
}
