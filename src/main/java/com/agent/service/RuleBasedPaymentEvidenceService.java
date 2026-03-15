package com.agent.service;

import com.agent.model.LegalEvidenceLine;
import com.agent.model.PaymentRecord;
import com.agent.model.PaymentSummary;
import com.agent.repository.PaymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Orchestration service for payment evidence queries.
 * 
 * Coordinates between repository, analyzer, and formatter layers
 * to provide complete payment evidence pipeline.
 * 
 * All public methods:
 * 1. Query via PaymentRecordRepository
 * 2. Aggregate via PropertyPaymentAnalyzer
 * 3. Format via LegalEvidenceFormatter
 * 4. Return payment facts for legal decisions
 */
@Service
public class RuleBasedPaymentEvidenceService implements PaymentEvidenceService {

    private static final Logger logger = LoggerFactory.getLogger(RuleBasedPaymentEvidenceService.class);

    private final PaymentRecordRepository repository;
    private final PropertyPaymentAnalyzer analyzer;
    private final LegalEvidenceFormatter formatter;

    public RuleBasedPaymentEvidenceService(
        PaymentRecordRepository repository,
        PropertyPaymentAnalyzer analyzer,
        LegalEvidenceFormatter formatter
    ) {
        this.repository = repository;
        this.analyzer = analyzer;
        this.formatter = formatter;
    }

    @Override
    public List<PaymentRecord> getPaymentsByDocument(Long pdfDocumentId) {
        logger.debug("[PAYMENT_EVIDENCE] Querying payments for document: {}", pdfDocumentId);
        List<PaymentRecord> records = repository.findByPdfDocumentId(pdfDocumentId);
        logger.debug("[PAYMENT_EVIDENCE] Found {} payment records for document {}", records.size(), pdfDocumentId);
        return records;
    }

    @Override
    public List<PaymentRecord> getPaymentsByProperty(String propertyAddress, String propertyCity) {
        logger.debug("[PAYMENT_EVIDENCE] Querying payments for property: {} {}", propertyAddress, propertyCity);
        List<PaymentRecord> records = repository.findByPropertyAddress(propertyAddress);
        logger.debug("[PAYMENT_EVIDENCE] Found {} payment records for property", records.size());
        return records;
    }

    @Override
    public List<PaymentRecord> getPaymentsByCategory(String category) {
        logger.debug("[PAYMENT_EVIDENCE] Querying payments by category: {}", category);
        // Note: Repository doesn't have direct category-only method; would need date range
        // For now, return empty - should be implemented with date params
        logger.warn("[PAYMENT_EVIDENCE] Category-only queries not yet supported; use date range queries");
        return List.of();
    }

    @Override
    public List<PaymentRecord> getPaymentsByPropertyAndDateRange(
        String propertyAddress,
        String propertyCity,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        logger.debug("[PAYMENT_EVIDENCE] Querying payments for property {} from {} to {}",
            propertyAddress, dateFrom, dateTo);
        List<PaymentRecord> records = repository.findMortgagePaymentsByPropertyAndDateRange(
            propertyAddress,
            propertyCity,
            dateFrom,
            dateTo
        );
        logger.debug("[PAYMENT_EVIDENCE] Found {} payment records for date range", records.size());
        return records;
    }

    @Override
    public List<PaymentRecord> getMortgagePaymentsByPropertyAndDateRange(
        String propertyAddress,
        String propertyCity,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        logger.debug("[PAYMENT_EVIDENCE] Querying mortgage payments for property {} from {} to {}",
            propertyAddress, dateFrom, dateTo);
        List<PaymentRecord> records = repository.findMortgagePaymentsByPropertyAndDateRange(
            propertyAddress,
            propertyCity,
            dateFrom,
            dateTo
        );
        logger.debug("[PAYMENT_EVIDENCE] Found {} mortgage payment records", records.size());
        return records;
    }

    @Override
    public PaymentSummary summarizePaymentsByPropertyAndDateRange(
        String propertyAddress,
        String propertyCity,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        logger.debug("[PAYMENT_EVIDENCE] Summarizing payments for property {} from {} to {}",
            propertyAddress, dateFrom, dateTo);

        List<PaymentRecord> records = getPaymentsByPropertyAndDateRange(
            propertyAddress,
            propertyCity,
            dateFrom,
            dateTo
        );

        PaymentSummary summary = analyzer.summarizeByProperty(records);
        logger.debug("[PAYMENT_EVIDENCE] Generated summary: total={}, records={}",
            summary != null ? summary.getTotalPayments() : null,
            records.size());

        return summary;
    }

    @Override
    public List<LegalEvidenceLine> formatPaymentRecordsAsEvidence(List<PaymentRecord> records) {
        logger.debug("[PAYMENT_EVIDENCE] Formatting {} payment records as evidence", records.size());
        return formatter.formatMultipleRecords(records);
    }

    @Override
    public LegalEvidenceLine formatPaymentSummaryAsEvidence(PaymentSummary summary) {
        logger.debug("[PAYMENT_EVIDENCE] Formatting payment summary as evidence");
        return formatter.formatPaymentSummary(summary);
    }
}
