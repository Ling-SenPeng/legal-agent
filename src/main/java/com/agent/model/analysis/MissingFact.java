package com.agent.model.analysis;

/**
 * Represents a fact that is needed for case analysis but missing from evidence.
 * 
 * Missing facts identify critical information gaps that should be addressed
 * to strengthen the case or provide important context for legal analysis.
 */
public class MissingFact {
    private final String description;  // What fact is needed
    private final LegalIssueType relevantIssue;  // Issue this would address
    private final String reason;  // Why this fact is needed

    public MissingFact(String description, LegalIssueType relevantIssue, String reason) {
        this.description = description;
        this.relevantIssue = relevantIssue;
        this.reason = reason;
    }

    public String getDescription() {
        return description;
    }

    public LegalIssueType getRelevantIssue() {
        return relevantIssue;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "MissingFact{" +
                "description='" + description + '\'' +
                ", issue=" + relevantIssue +
                ", reason='" + reason + '\'' +
                '}';
    }
}
