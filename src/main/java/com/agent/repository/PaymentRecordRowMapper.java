package com.agent.repository;

import com.agent.model.PaymentRecord;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps ResultSet rows from payment_records table to PaymentRecord objects.
 * 
 * Used by JDBC-based PaymentRecordRepository to convert SQL rows
 * into domain objects.
 */
public class PaymentRecordRowMapper implements RowMapper<PaymentRecord> {

    @Override
    public PaymentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentRecord(
            rs.getObject("pdf_document_id", Long.class),
            rs.getObject("statement_index", Integer.class),
            rs.getObject("statement_period_start", java.time.LocalDate.class),
            rs.getObject("statement_period_end", java.time.LocalDate.class),
            rs.getObject("payment_date", java.time.LocalDate.class),
            rs.getString("category"),
            rs.getObject("total_amount", java.math.BigDecimal.class),
            rs.getObject("principal_amount", java.math.BigDecimal.class),
            rs.getObject("interest_amount", java.math.BigDecimal.class),
            rs.getObject("escrow_amount", java.math.BigDecimal.class),
            rs.getObject("tax_amount", java.math.BigDecimal.class),
            rs.getObject("insurance_amount", java.math.BigDecimal.class),
            rs.getString("payer_name"),
            rs.getString("payee_name"),
            rs.getString("loan_number"),
            rs.getString("property_address"),
            rs.getString("property_city"),
            rs.getString("property_state"),
            rs.getString("property_zip"),
            rs.getString("description"),
            rs.getObject("source_page", Integer.class),
            rs.getString("source_snippet"),
            rs.getObject("confidence", Double.class)
        );
    }
}
