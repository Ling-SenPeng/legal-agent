package com.agent.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a structured payment record extracted from mortgage/loan documents.
 * 
 * Maps to the payment_records table. Contains all fields extracted from statements
 * including payment amounts, dates, property info, and source citations.
 * 
 * This is the primary source for financial evidence in the legal agent.
 */
public class PaymentRecord {
    // Database reference
    private final Long pdfDocumentId;
    private final Integer statementIndex;
    
    // Period information
    private final LocalDate statementPeriodStart;
    private final LocalDate statementPeriodEnd;
    private final LocalDate paymentDate;
    
    // Classification
    private final String category;
    
    // Amount breakdown
    private final BigDecimal totalAmount;
    private final BigDecimal principalAmount;
    private final BigDecimal interestAmount;
    private final BigDecimal escrowAmount;
    private final BigDecimal taxAmount;
    private final BigDecimal insuranceAmount;
    
    // Party information
    private final String payerName;
    private final String payeeName;
    private final String loanNumber;
    
    // Property information
    private final String propertyAddress;
    private final String propertyCity;
    private final String propertyState;
    private final String propertyZip;
    
    // Record details
    private final String description;
    private final Integer sourcePage;
    private final String sourceSnippet;
    private final Double confidence;

    public PaymentRecord(
        Long pdfDocumentId,
        Integer statementIndex,
        LocalDate statementPeriodStart,
        LocalDate statementPeriodEnd,
        LocalDate paymentDate,
        String category,
        BigDecimal totalAmount,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal escrowAmount,
        BigDecimal taxAmount,
        BigDecimal insuranceAmount,
        String payerName,
        String payeeName,
        String loanNumber,
        String propertyAddress,
        String propertyCity,
        String propertyState,
        String propertyZip,
        String description,
        Integer sourcePage,
        String sourceSnippet,
        Double confidence
    ) {
        this.pdfDocumentId = pdfDocumentId;
        this.statementIndex = statementIndex;
        this.statementPeriodStart = statementPeriodStart;
        this.statementPeriodEnd = statementPeriodEnd;
        this.paymentDate = paymentDate;
        this.category = category;
        this.totalAmount = totalAmount;
        this.principalAmount = principalAmount;
        this.interestAmount = interestAmount;
        this.escrowAmount = escrowAmount;
        this.taxAmount = taxAmount;
        this.insuranceAmount = insuranceAmount;
        this.payerName = payerName;
        this.payeeName = payeeName;
        this.loanNumber = loanNumber;
        this.propertyAddress = propertyAddress;
        this.propertyCity = propertyCity;
        this.propertyState = propertyState;
        this.propertyZip = propertyZip;
        this.description = description;
        this.sourcePage = sourcePage;
        this.sourceSnippet = sourceSnippet;
        this.confidence = confidence;
    }

    // Getters
    public Long getPdfDocumentId() { return pdfDocumentId; }
    public Integer getStatementIndex() { return statementIndex; }
    public LocalDate getStatementPeriodStart() { return statementPeriodStart; }
    public LocalDate getStatementPeriodEnd() { return statementPeriodEnd; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public String getCategory() { return category; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public BigDecimal getInterestAmount() { return interestAmount; }
    public BigDecimal getEscrowAmount() { return escrowAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getInsuranceAmount() { return insuranceAmount; }
    public String getPayerName() { return payerName; }
    public String getPayeeName() { return payeeName; }
    public String getLoanNumber() { return loanNumber; }
    public String getPropertyAddress() { return propertyAddress; }
    public String getPropertyCity() { return propertyCity; }
    public String getPropertyState() { return propertyState; }
    public String getPropertyZip() { return propertyZip; }
    public String getDescription() { return description; }
    public Integer getSourcePage() { return sourcePage; }
    public String getSourceSnippet() { return sourceSnippet; }
    public Double getConfidence() { return confidence; }

    @Override
    public String toString() {
        return "PaymentRecord{" +
                "paymentDate=" + paymentDate +
                ", category='" + category + '\'' +
                ", totalAmount=" + totalAmount +
                ", propertyAddress='" + propertyAddress + '\'' +
                ", propertyCity='" + propertyCity + '\'' +
                ", sourcePage=" + sourcePage +
                ", confidence=" + confidence +
                '}';
    }
}
