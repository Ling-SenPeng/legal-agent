package com.agent.repository;

import com.agent.model.PaymentRecord;
import java.time.LocalDate;
import java.util.List;

/**
 * Data access layer for payment_records table.
 * 
 * Single source of truth for querying structured payment data.
 * Replaces text-based chunk extraction for payment-related questions.
 * 
 * All methods use JDBC with direct SQL execution for maximum
 * control and performance optimization.
 */
public interface PaymentRecordRepository {

    /**
     * Get all payment records for a specific PDF document.
     * 
     * @param pdfDocumentId Document ID from pdf_documents table
     * @return List of payment records from that document
     */
    List<PaymentRecord> findByPdfDocumentId(Long pdfDocumentId);

    /**
     * Get all payment records for a property city.
     * 
     * Useful for city-level aggregations and summaries.
     * 
     * @param propertyCity Property city (e.g., "Newark", "Los Angeles")
     * @return List of payment records for that city
     */
    List<PaymentRecord> findByPropertyCity(String propertyCity);

    /**
     * Get all payment records matching a property address.
     * 
     * Filters to a single specific property.
     * 
     * @param propertyAddress Full property address
     * @return List of payment records for that address
     */
    List<PaymentRecord> findByPropertyAddress(String propertyAddress);

    /**
     * Get payment records by category within a date range.
     * 
     * Categories: "mortgage", "escrow", "tax", "insurance", etc.
     * Useful for filtering by payment type and time period.
     * 
     * @param category Payment category
     * @param dateFrom Start date (inclusive)
     * @param dateTo End date (inclusive)
     * @return List of payment records matching criteria
     */
    List<PaymentRecord> findByCategoryAndDateRange(
        String category,
        LocalDate dateFrom,
        LocalDate dateTo
    );

    /**
     * Get mortgage payments (principal + interest) for a property within date range.
     * 
     * Filters to mortgage category and date range.
     * 
     * @param propertyAddress Property address
     * @param propertyCity Property city
     * @param dateFrom Start date (inclusive)
     * @param dateTo End date (inclusive)
     * @return List of mortgage payment records
     */
    List<PaymentRecord> findMortgagePaymentsByPropertyAndDateRange(
        String propertyAddress,
        String propertyCity,
        LocalDate dateFrom,
        LocalDate dateTo
    );

    /**
     * Get escrow and tax payments for a property.
     * 
     * Filters to escrow and tax categories.
     * 
     * @param propertyAddress Property address
     * @param propertyCity Property city
     * @return List of escrow/tax payment records
     */
    List<PaymentRecord> findEscrowAndTaxPaymentsByProperty(
        String propertyAddress,
        String propertyCity
    );
}
