package com.agent.repository;

import com.agent.model.PaymentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * JDBC-based implementation of PaymentRecordRepository.
 * 
 * Provides direct SQL queries to payment_records table with full control
 * over query performance and flexibility.
 * 
 * All methods use prepared statements with JdbcTemplate for safety
 * and automatic row mapping via PaymentRecordRowMapper.
 */
@Repository
public class PaymentRecordRepositoryImpl implements PaymentRecordRepository {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRecordRepositoryImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final PaymentRecordRowMapper rowMapper;

    public PaymentRecordRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = new PaymentRecordRowMapper();
    }

    @Override
    public List<PaymentRecord> findByPdfDocumentId(Long pdfDocumentId) {
        String query = """
            SELECT 
                pdf_document_id,
                statement_index,
                statement_period_start,
                statement_period_end,
                payment_date,
                category,
                total_amount,
                principal_amount,
                interest_amount,
                escrow_amount,
                tax_amount,
                insurance_amount,
                payer_name,
                payee_name,
                loan_number,
                property_address,
                property_city,
                property_state,
                property_zip,
                description,
                source_page,
                source_snippet,
                confidence
            FROM payment_records
            WHERE pdf_document_id = ?
            ORDER BY payment_date DESC, statement_index ASC
            """;

        logger.debug("[PAYMENT_RECORDS] Query: findByPdfDocumentId({})", pdfDocumentId);
        List<PaymentRecord> results = jdbcTemplate.query(query, rowMapper, pdfDocumentId);
        logger.debug("[PAYMENT_RECORDS] Found {} records for pdf_document_id={}", results.size(), pdfDocumentId);
        return results;
    }

    @Override
    public List<PaymentRecord> findByPropertyCity(String propertyCity) {
        String query = """
            SELECT 
                pdf_document_id,
                statement_index,
                statement_period_start,
                statement_period_end,
                payment_date,
                category,
                total_amount,
                principal_amount,
                interest_amount,
                escrow_amount,
                tax_amount,
                insurance_amount,
                payer_name,
                payee_name,
                loan_number,
                property_address,
                property_city,
                property_state,
                property_zip,
                description,
                source_page,
                source_snippet,
                confidence
            FROM payment_records
            WHERE property_city = ?
            ORDER BY payment_date DESC, property_address ASC
            """;

        logger.debug("[PAYMENT_RECORDS] Query: findByPropertyCity({})", propertyCity);
        List<PaymentRecord> results = jdbcTemplate.query(query, rowMapper, propertyCity);
        logger.debug("[PAYMENT_RECORDS] Found {} records for property_city={}", results.size(), propertyCity);
        return results;
    }

    @Override
    public List<PaymentRecord> findByPropertyAddress(String propertyAddress) {
        String query = """
            SELECT 
                pdf_document_id,
                statement_index,
                statement_period_start,
                statement_period_end,
                payment_date,
                category,
                total_amount,
                principal_amount,
                interest_amount,
                escrow_amount,
                tax_amount,
                insurance_amount,
                payer_name,
                payee_name,
                loan_number,
                property_address,
                property_city,
                property_state,
                property_zip,
                description,
                source_page,
                source_snippet,
                confidence
            FROM payment_records
            WHERE property_address = ?
            ORDER BY payment_date DESC, statement_index ASC
            """;

        logger.debug("[PAYMENT_RECORDS] Query: findByPropertyAddress({})", propertyAddress);
        List<PaymentRecord> results = jdbcTemplate.query(query, rowMapper, propertyAddress);
        logger.debug("[PAYMENT_RECORDS] Found {} records for property_address={}", results.size(), propertyAddress);
        return results;
    }

    @Override
    public List<PaymentRecord> findByCategoryAndDateRange(
        String category,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        String query = """
            SELECT 
                pdf_document_id,
                statement_index,
                statement_period_start,
                statement_period_end,
                payment_date,
                category,
                total_amount,
                principal_amount,
                interest_amount,
                escrow_amount,
                tax_amount,
                insurance_amount,
                payer_name,
                payee_name,
                loan_number,
                property_address,
                property_city,
                property_state,
                property_zip,
                description,
                source_page,
                source_snippet,
                confidence
            FROM payment_records
            WHERE category = ? AND payment_date >= ? AND payment_date <= ?
            ORDER BY payment_date DESC, property_address ASC
            """;

        logger.debug("[PAYMENT_RECORDS] Query: findByCategoryAndDateRange({}, {}, {})", 
            category, dateFrom, dateTo);
        List<PaymentRecord> results = jdbcTemplate.query(
            query,
            rowMapper,
            category,
            dateFrom,
            dateTo
        );
        logger.debug("[PAYMENT_RECORDS] Found {} records for category={}", results.size(), category);
        return results;
    }

    @Override
    public List<PaymentRecord> findMortgagePaymentsByPropertyAndDateRange(
        String propertyAddress,
        String propertyCity,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        String query = """
            SELECT 
                pdf_document_id,
                statement_index,
                statement_period_start,
                statement_period_end,
                payment_date,
                category,
                total_amount,
                principal_amount,
                interest_amount,
                escrow_amount,
                tax_amount,
                insurance_amount,
                payer_name,
                payee_name,
                loan_number,
                property_address,
                property_city,
                property_state,
                property_zip,
                description,
                source_page,
                source_snippet,
                confidence
            FROM payment_records
            WHERE property_address = ? 
              AND property_city = ?
              AND category = 'mortgage'
              AND payment_date >= ? 
              AND payment_date <= ?
            ORDER BY payment_date DESC, statement_index ASC
            """;

        logger.debug("[PAYMENT_RECORDS] Query: findMortgagePaymentsByPropertyAndDateRange({}, {}, {}, {})", 
            propertyAddress, propertyCity, dateFrom, dateTo);
        List<PaymentRecord> results = jdbcTemplate.query(
            query,
            rowMapper,
            propertyAddress,
            propertyCity,
            dateFrom,
            dateTo
        );
        logger.debug("[PAYMENT_RECORDS] Found {} mortgage records for property", results.size());
        return results;
    }

    @Override
    public List<PaymentRecord> findEscrowAndTaxPaymentsByProperty(
        String propertyAddress,
        String propertyCity
    ) {
        String query = """
            SELECT 
                pdf_document_id,
                statement_index,
                statement_period_start,
                statement_period_end,
                payment_date,
                category,
                total_amount,
                principal_amount,
                interest_amount,
                escrow_amount,
                tax_amount,
                insurance_amount,
                payer_name,
                payee_name,
                loan_number,
                property_address,
                property_city,
                property_state,
                property_zip,
                description,
                source_page,
                source_snippet,
                confidence
            FROM payment_records
            WHERE property_address = ? 
              AND property_city = ?
              AND category IN ('escrow', 'tax')
            ORDER BY payment_date DESC, category ASC
            """;

        logger.debug("[PAYMENT_RECORDS] Query: findEscrowAndTaxPaymentsByProperty({}, {})", 
            propertyAddress, propertyCity);
        List<PaymentRecord> results = jdbcTemplate.query(
            query,
            rowMapper,
            propertyAddress,
            propertyCity
        );
        logger.debug("[PAYMENT_RECORDS] Found {} escrow/tax records for property", results.size());
        return results;
    }
}
