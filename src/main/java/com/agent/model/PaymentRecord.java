package com.agent.model;

/**
 * Represents a structured payment record extracted from text.
 * 
 * Contains extracted fields from mortgage/payment evidence snippets.
 */
public class PaymentRecord {
    private final String propertyName;
    private final String paymentDate;
    private final Double amount;
    private final String accountHolder;
    private final String sourceText;
    private final String sourceDocument;
    private final double confidence;

    public PaymentRecord(
        String propertyName,
        String paymentDate,
        Double amount,
        String accountHolder,
        String sourceText,
        String sourceDocument,
        double confidence
    ) {
        this.propertyName = propertyName;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.accountHolder = accountHolder;
        this.sourceText = sourceText;
        this.sourceDocument = sourceDocument;
        this.confidence = confidence;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getPaymentDate() {
        return paymentDate;
    }

    public Double getAmount() {
        return amount;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public String getSourceText() {
        return sourceText;
    }

    public String getSourceDocument() {
        return sourceDocument;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "PaymentRecord{" +
                "propertyName='" + propertyName + '\'' +
                ", paymentDate='" + paymentDate + '\'' +
                ", amount=" + amount +
                ", confidence=" + confidence +
                '}';
    }
}
