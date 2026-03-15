package com.agent.service;

import com.agent.model.LegalEvidenceLine;
import com.agent.model.PaymentRecord;
import com.agent.model.PaymentSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * Service boundary for payment evidence queries and analysis.
 * 
 * Primary responsibility: Query payment_records table and provide
 * financial evidence for legal analysis.
 * 
 * Coordinates:
 * - PaymentRecordRepository: database access
 * - PropertyPaymentAnalyzer: aggregation and summarization
 * - LegalEvidenceFormatter: formatting for legal answers
 * 
 * Usage pattern:
 * 1. Query payment records by document/property/date
 * 2. Analyze/aggregate via PropertyPaymentAnalyzer
 * 3. Format via LegalEvidenceFormatter
 * 
 * This service should be called FIRST for payment-related questions,
 * with pdf_chunks as fallback only.
 */
public interface PaymentEvidenceService {

    /**
     * Get all payment records for a specific PDF document.
     * 
     * @param pdfDocumentId Document ID
     * @return List of payment records
     */
    List<PaymentRecord> getPaymentsByDocument(Long pdfDocumentId);

    /**
     * Get all payment records for a property by address and city.
     * 
     * @param propertyAddress Property address
     * @param propertyCity Property city
     * @return List of payment records
     */
    List<PaymentRecord> getPaymentsByProperty(String propertyAddress, String propertyCity);

    /**
     * Get payment records by category (e.g., "mortgage", "escrow", "tax").
     * 
     * @param category Payment category
     * @return List of payment records
     */
    List<PaymentRecord> getPaymentsByCategory(String category);

    /**
     * Get payment records by property and date range.
     * 
     * @param propertyAddress Property address
     * @param propertyCity Property city
     * @param dateFrom Start date (inclusive)
     * @param dateTo End date (inclusive)
     * @return List of payment records
     */
    List<PaymentRecord> getPaymentsByPropertyAndDateRange(
        String propertyAddress,
        String propertyCity,
        LocalDate dateFrom,
        LocalDate dateTo
    );

    /**
     * Get mortgage payments (principal + interest) for property within date range.
     * 
     * @param propertyAddress Property address
     * @param propertyCity Property city
     * @param dateFrom Start date (inclusive)
     * @param dateTo End date (inclusive)
     * @return List of mortgage payment records
     */
    List<PaymentRecord> getMortgagePaymentsByPropertyAndDateRange(
        String propertyAddress,
        String propertyCity,
        LocalDate dateFrom,
        LocalDate dateTo
    );

    /**
     * Get summarized payment data for a property by date range.
     * 
     * Aggregates all payments for property within date range
     * and returns a single summary with totals.
     * 
     * @param propertyAddress Property address
     * @param propertyCity Property city
     * @param dateFrom Start date (inclusive)
     * @param dateTo End date (inclusive)
     * @return PaymentSummary with aggregated totals, or null if no records
     */
    PaymentSummary summarizePaymentsByPropertyAndDateRange(
        String propertyAddress,
        String propertyCity,
        LocalDate dateFrom,
        LocalDate dateTo
    );

    /**
     * Get formatted evidence lines for payment records.
     * 
     * Converts raw payment records into citation-ready evidence lines
     * for embedding in legal answers.
     * 
     * @param records Payment records to format
     * @return List of LegalEvidenceLine objects
     */
    List<LegalEvidenceLine> formatPaymentRecordsAsEvidence(List<PaymentRecord> records);

    /**
     * Get formatted evidence line for payment summary.
     * 
     * Converts aggregated summary into single evidence line
     * for high-level financial fact.
     * 
     * @param summary Payment summary to format
     * @return LegalEvidenceLine representing the summary
     */
    LegalEvidenceLine formatPaymentSummaryAsEvidence(PaymentSummary summary);
}
