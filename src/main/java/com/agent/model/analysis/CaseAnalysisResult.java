package com.agent.model.analysis;

/**
 * Result of case analysis including strength assessment and recommendations.
 * 
 * Provides analysis conclusion on claim strength, risks, and recommendations
 * based on identified issues, facts, and applicable legal standards.
 */
public class CaseAnalysisResult {
    /**
     * Enum for strength assessment levels
     */
    public enum StrengthLevel {
        VERY_WEAK,      // Unlikely to succeed
        WEAK,           // Poor chances
        MODERATE,       // Reasonable chances
        STRONG,         // Good chances
        VERY_STRONG     // Likely to succeed
    }

    private final CaseAnalysisContext context;
    private final StrengthLevel strengthLevel;
    private final double confidenceScore;  // [0.0, 1.0]
    private final String analysis;  // Detailed legal analysis
    private final String recommendations;  // Specific recommendations

    public CaseAnalysisResult(
        CaseAnalysisContext context,
        StrengthLevel strengthLevel,
        double confidenceScore,
        String analysis,
        String recommendations
    ) {
        if (confidenceScore < 0.0 || confidenceScore > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        this.context = context;
        this.strengthLevel = strengthLevel;
        this.confidenceScore = confidenceScore;
        this.analysis = analysis;
        this.recommendations = recommendations;
    }

    public CaseAnalysisContext getContext() {
        return context;
    }

    public StrengthLevel getStrengthLevel() {
        return strengthLevel;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public String getAnalysis() {
        return analysis;
    }

    public String getRecommendations() {
        return recommendations;
    }

    @Override
    public String toString() {
        return "CaseAnalysisResult{" +
                "strength=" + strengthLevel +
                ", confidence=" + String.format("%.2f", confidenceScore) +
                ", issues=" + context.getIdentifiedIssues().size() +
                '}';
    }
}
