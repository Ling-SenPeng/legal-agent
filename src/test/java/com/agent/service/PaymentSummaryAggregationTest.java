package com.agent.service;

import com.agent.model.PaymentRecord;
import com.agent.model.PaymentSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentSummary aggregation and null-safe BigDecimal arithmetic.
 * 
 * Verifies that payment summaries correctly aggregate multiple payment records
 * with null-safe handling of optional amount fields.
 */
@DisplayName("PaymentSummary Aggregation Tests")
class PaymentSummaryAggregationTest {

    @Test
    @DisplayName("PaymentSummary correctly aggregates total amounts")
    void testTotalAmountAggregation() {
        PaymentSummary summary = new PaymentSummary(
            "123 Main St",
            "Newark",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31),
            3,
            new BigDecimal("7500.00"),           // totalAmount
            new BigDecimal("3000.00"),           // principalTotal
            new BigDecimal("3600.00"),           // interestTotal
            new BigDecimal("450.00"),            // escrowTotal
            new BigDecimal("300.00"),            // taxTotal
            new BigDecimal("150.00"),            // insuranceTotal
            0.93,                                // averageConfidence
            new ArrayList<>()
        );

        assertEquals(new BigDecimal("7500.00"), summary.getTotalPayments());
        assertEquals(3, summary.getRecordCount());
        assertEquals(0.93, summary.getAverageConfidence());
    }

    @Test
    @DisplayName("PaymentSummary handles null amounts safely")
    void testNullAmountHandling() {
        // Create summary with some null amounts (e.g., escrow not collected in all months)
        PaymentSummary summary = new PaymentSummary(
            "456 Oak Ave",
            "Los Angeles",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 3, 31),
            3,
            new BigDecimal("6000.00"),           // totalAmount
            new BigDecimal("2000.00"),           // principalTotal
            new BigDecimal("2400.00"),           // interestTotal
            null,                                // escrowTotal (null)
            null,                                // taxTotal (null)
            new BigDecimal("1600.00"),           // insuranceTotal
            0.88,
            new ArrayList<>()
        );

        assertEquals(new BigDecimal("6000.00"), summary.getTotalPayments());
        assertNull(summary.getTotalEscrow(), "Escrow should be null when not collected");
        assertNull(summary.getTotalTax(), "Tax should be null when not collected");
        assertNotNull(summary.getTotalInsurance(), "Insurance should be populated");
    }

    @Test
    @DisplayName("PaymentSummary supports empty source records for summarized data")
    void testEmptySourceRecordsList() {
        PaymentSummary summary = new PaymentSummary(
            "789 Pine St",
            "San Francisco",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 2, 28),
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new ArrayList<>()
        );

        assertEquals(0, summary.getRecordCount());
        assertNull(summary.getTotalPayments());
        assertTrue(summary.getSourceRecords().isEmpty());
    }

    @Test
    @DisplayName("PropertyPaymentAnalyzer summarizes multiple records with aggregation")
    void testPropertyPaymentAnalyzerAggregation() {
        PropertyPaymentAnalyzer analyzer = new PropertyPaymentAnalyzer();
        
        List<PaymentRecord> records = createTestPaymentRecords();

        PaymentSummary summary = analyzer.summarizeByProperty(records);

        assertNotNull(summary);
        assertEquals("123 Main St", summary.getPropertyAddress());
        assertEquals(2, summary.getRecordCount());
        
        // Verify aggregation: 2500 + 2500 = 5000
        assertEquals(new BigDecimal("5000.00"), summary.getTotalPayments());
        assertEquals(new BigDecimal("2000.00"), summary.getTotalPrincipal());
        assertEquals(new BigDecimal("2400.00"), summary.getTotalInterest());
    }

    private List<PaymentRecord> createTestPaymentRecords() {
        List<PaymentRecord> records = new ArrayList<>();
        
        PaymentRecord record1 = new PaymentRecord(
            1L, 0,
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
            LocalDate.of(2024, 2, 1),
            "mortgage",
            new BigDecimal("2500.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("1200.00"),
            new BigDecimal("150.00"),
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            "Borrower", "Lender", "LOAN123",
            "123 Main St", "Newark", "NJ", "07102",
            "Payment 1", 1, "Payment of $2500", 0.95
        );
        records.add(record1);

        PaymentRecord record2 = new PaymentRecord(
            1L, 1,
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29),
            LocalDate.of(2024, 3, 1),
            "mortgage",
            new BigDecimal("2500.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("1200.00"),
            new BigDecimal("150.00"),
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            "Borrower", "Lender", "LOAN123",
            "123 Main St", "Newark", "NJ", "07102",
            "Payment 2", 2, "Payment of $2500", 0.95
        );
        records.add(record2);

        return records;
    }

    @Test
    @DisplayName("PaymentSummary toString includes key aggregated values")
    void testSummaryToString() {
        PaymentSummary summary = new PaymentSummary(
            "123 Main St",
            "Newark",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31),
            12,
            new BigDecimal("30000.00"),
            new BigDecimal("12000.00"),
            new BigDecimal("14400.00"),
            new BigDecimal("1800.00"),
            new BigDecimal("1200.00"),
            new BigDecimal("600.00"),
            0.92,
            new ArrayList<>()
        );

        String summaryStr = summary.toString();
        
        assertTrue(summaryStr.contains("123 Main St"));
        assertTrue(summaryStr.contains("Newark"));
        assertTrue(summaryStr.contains("30000.00"));
        assertTrue(summaryStr.contains("12"));
    }
}
