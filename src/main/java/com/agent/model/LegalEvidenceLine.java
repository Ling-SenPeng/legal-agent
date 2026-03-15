package com.agent.model;

/**
 * Citation-ready evidence line for payment facts.
 * 
 * Formats payment records into structured evidence text
 * suitable for embedding in legal answers. Includes:
 * - Formatted amounts and dates
 * - Property references
 * - Source citations
 * - Confidence score
 */
public class LegalEvidenceLine {
    private final String evidenceText;
    private final String propertyReference;
    private final String dateRange;
    private final String formattedAmount;
    private final String sourceReference;
    private final Integer pageCitation;
    private final Double confidence;

    public LegalEvidenceLine(
        String evidenceText,
        String propertyReference,
        String dateRange,
        String formattedAmount,
        String sourceReference,
        Integer pageCitation,
        Double confidence
    ) {
        this.evidenceText = evidenceText;
        this.propertyReference = propertyReference;
        this.dateRange = dateRange;
        this.formattedAmount = formattedAmount;
        this.sourceReference = sourceReference;
        this.pageCitation = pageCitation;
        this.confidence = confidence;
    }

    // Getters
    public String getEvidenceText() { return evidenceText; }
    public String getPropertyReference() { return propertyReference; }
    public String getDateRange() { return dateRange; }
    public String getFormattedAmount() { return formattedAmount; }
    public String getSourceReference() { return sourceReference; }
    public Integer getPageCitation() { return pageCitation; }
    public Double getConfidence() { return confidence; }

    @Override
    public String toString() {
        return "LegalEvidenceLine{" +
                "evidence='" + evidenceText + '\'' +
                ", property='" + propertyReference + '\'' +
                ", amount='" + formattedAmount + '\'' +
                ", source='" + sourceReference + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
