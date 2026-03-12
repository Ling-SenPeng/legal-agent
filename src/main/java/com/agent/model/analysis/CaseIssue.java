package com.agent.model.analysis;

/**
 * Represents a legal issue identified in a case.
 * 
 * Each issue has a type, human-readable description, and confidence
 * score indicating how strongly it was detected in the case context.
 */
public class CaseIssue {
    private final LegalIssueType type;
    private final String description;  // Human-readable description
    private final double confidence;   // [0.0, 1.0] - strength of detection
    private final String matchedKeywords;  // Comma-separated keywords that triggered detection

    public CaseIssue(LegalIssueType type, String description, double confidence, String matchedKeywords) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        this.type = type;
        this.description = description;
        this.confidence = confidence;
        this.matchedKeywords = matchedKeywords;
    }

    public LegalIssueType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getMatchedKeywords() {
        return matchedKeywords;
    }

    @Override
    public String toString() {
        return "CaseIssue{" +
                "type=" + type +
                ", description='" + description + '\'' +
                ", confidence=" + String.format("%.2f", confidence) +
                ", keywords='" + matchedKeywords + '\'' +
                '}';
    }
}
