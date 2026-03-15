package com.agent.service;

import com.agent.model.PaymentRecord;
import com.agent.model.PaymentSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates and summarizes payment records into summaries.
 * 
 * Handles business logic for:
 * - Grouping payments by property
 * - Calculating totals and averages
 * - Summarizing by date range
 * - Confidence score aggregation
 */
@Service
public class PropertyPaymentAnalyzer {

    /**
     * Summarize payment records by property, aggregating all amounts.
     * 
     * @param records Payment records to summarize
     * @return Summary grouped by property address and city
     */
    public PaymentSummary summarizeByProperty(List<PaymentRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }

        // Assume all records are for the same property
        PaymentRecord first = records.get(0);
        
        BigDecimal totalPayments = records.stream()
            .map(PaymentRecord::getTotalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPrincipal = records.stream()
            .map(PaymentRecord::getPrincipalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInterest = records.stream()
            .map(PaymentRecord::getInterestAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEscrow = records.stream()
            .map(PaymentRecord::getEscrowAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTax = records.stream()
            .map(PaymentRecord::getTaxAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInsurance = records.stream()
            .map(PaymentRecord::getInsuranceAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Double avgConfidence = records.stream()
            .map(PaymentRecord::getConfidence)
            .filter(c -> c != null)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

        LocalDate periodStart = records.stream()
            .map(PaymentRecord::getPaymentDate)
            .filter(d -> d != null)
            .min(LocalDate::compareTo)
            .orElse(null);

        LocalDate periodEnd = records.stream()
            .map(PaymentRecord::getPaymentDate)
            .filter(d -> d != null)
            .max(LocalDate::compareTo)
            .orElse(null);

        return new PaymentSummary(
            first.getPropertyAddress(),
            first.getPropertyCity(),
            periodStart,
            periodEnd,
            records.size(),
            totalPayments,
            totalPrincipal,
            totalInterest,
            totalEscrow,
            totalTax,
            totalInsurance,
            avgConfidence,
            records
        );
    }

    /**
     * Summarize payments for a property within a specific date range.
     * 
     * @param records Payment records
     * @param dateFrom Start date (inclusive)
     * @param dateTo End date (inclusive)
     * @return Summary for records within date range
     */
    public PaymentSummary summarizePropertyByDateRange(
        List<PaymentRecord> records,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        if (records == null || records.isEmpty()) {
            return null;
        }

        List<PaymentRecord> filtered = records.stream()
            .filter(r -> r.getPaymentDate() != null &&
                        !r.getPaymentDate().isBefore(dateFrom) &&
                        !r.getPaymentDate().isAfter(dateTo))
            .collect(Collectors.toList());

        return summarizeByProperty(filtered);
    }

    /**
     * Summarize payments grouped by category.
     * 
     * @param records Payment records
     * @return Summaries grouped by category
     */
    public java.util.Map<String, PaymentSummary> summarizeByCategory(List<PaymentRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyMap();
        }

        return records.stream()
            .filter(r -> r.getCategory() != null)
            .collect(Collectors.groupingBy(
                PaymentRecord::getCategory,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    this::summarizeByProperty
                )
            ));
    }
}
