package com.agent.model.analysis;

/**
 * DTO representing a fact snippet that has been classified.
 */
public class ClassifiedFact {
    private final String originalText;
    private final FactCategory category;
    private final double confidence;
    private final String reason;

    public ClassifiedFact(String originalText, FactCategory category, double confidence, String reason) {
        this.originalText = originalText;
        this.category = category;
        this.confidence = confidence;
        this.reason = reason;
    }

    public String getOriginalText() {
        return originalText;
    }

    public FactCategory getCategory() {
        return category;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ClassifiedFact{" +
                "category=" + category +
                ", confidence=" + confidence +
                ", reason='" + reason + '\'' +
                '}';
    }
}
