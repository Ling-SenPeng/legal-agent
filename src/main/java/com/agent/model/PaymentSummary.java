package com.agent.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated summary of payment records grouped by property.
 * 
 * Used when summarizing multiple payment records into totals
 * for a single property or date range. Provides high-level
 * financial facts for legal analysis.
 */
public class PaymentSummary {
    private final String propertyAddress;
    private final String propertyCity;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int recordCount;
    
    private final BigDecimal totalPayments;
    private final BigDecimal totalPrincipal;
    private final BigDecimal totalInterest;
    private final BigDecimal totalEscrow;
    private final BigDecimal totalTax;
    private final BigDecimal totalInsurance;
    
    private final Double averageConfidence;
    private final List<PaymentRecord> sourceRecords;

    public PaymentSummary(
        String propertyAddress,
        String propertyCity,
        LocalDate periodStart,
        LocalDate periodEnd,
        int recordCount,
        BigDecimal totalPayments,
        BigDecimal totalPrincipal,
        BigDecimal totalInterest,
        BigDecimal totalEscrow,
        BigDecimal totalTax,
        BigDecimal totalInsurance,
        Double averageConfidence,
        List<PaymentRecord> sourceRecords
    ) {
        this.propertyAddress = propertyAddress;
        this.propertyCity = propertyCity;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.recordCount = recordCount;
        this.totalPayments = totalPayments;
        this.totalPrincipal = totalPrincipal;
        this.totalInterest = totalInterest;
        this.totalEscrow = totalEscrow;
        this.totalTax = totalTax;
        this.totalInsurance = totalInsurance;
        this.averageConfidence = averageConfidence;
        this.sourceRecords = sourceRecords;
    }

    // Getters
    public String getPropertyAddress() { return propertyAddress; }
    public String getPropertyCity() { return propertyCity; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public int getRecordCount() { return recordCount; }
    public BigDecimal getTotalPayments() { return totalPayments; }
    public BigDecimal getTotalPrincipal() { return totalPrincipal; }
    public BigDecimal getTotalInterest() { return totalInterest; }
    public BigDecimal getTotalEscrow() { return totalEscrow; }
    public BigDecimal getTotalTax() { return totalTax; }
    public BigDecimal getTotalInsurance() { return totalInsurance; }
    public Double getAverageConfidence() { return averageConfidence; }
    public List<PaymentRecord> getSourceRecords() { return sourceRecords; }

    @Override
    public String toString() {
        return "PaymentSummary{" +
                "property='" + propertyAddress + ", " + propertyCity + '\'' +
                ", period=" + periodStart + " to " + periodEnd +
                ", records=" + recordCount +
                ", totalPayments=" + totalPayments +
                ", confidence=" + averageConfidence +
                '}';
    }
}
