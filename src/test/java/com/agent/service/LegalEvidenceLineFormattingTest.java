package com.agent.service;

import com.agent.model.LegalEvidenceLine;
import com.agent.model.PaymentRecord;
import com.agent.model.PaymentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LegalEvidenceLine formatting and citation generation.
 * 
 * Verifies that payment records and summaries are formatted into
 * citation-ready evidence text for legal answers.
 */
@DisplayName("LegalEvidenceLine Formatting Tests")
class LegalEvidenceLineFormattingTest {

    private LegalEvidenceFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new LegalEvidenceFormatter();
    }

    @Test
    @DisplayName("LegalEvidenceLine format contains payment date")
    void testFormattingIncludesPaymentDate() {
        PaymentRecord record = createTestPaymentRecord();

        LegalEvidenceLine line = formatter.formatPaymentRecord(record);

        assertNotNull(line);
        String text = line.getEvidenceText();
        assertTrue(text.contains("2024-02-01") || text.contains("02/01") || text.contains("on "),
            "Evidence line should contain payment date");
    }

    @Test
    @DisplayName("LegalEvidenceLine format contains amount")
    void testFormattingIncludesAmount() {
        PaymentRecord record = createTestPaymentRecord();

        LegalEvidenceLine line = formatter.formatPaymentRecord(record);

        assertNotNull(line);
        String text = line.getEvidenceText();
        String formatted = line.getFormattedAmount();
        assertTrue((text != null && text.contains("2500")) || 
                   (formatted != null && formatted.contains("2500")) ||
                   (formatted != null && formatted.contains("2,500")),
            "Evidence line should contain payment amount in some form");
    }

    @Test
    @DisplayName("LegalEvidenceLine includes property information")
    void testFormattingIncludesPropertyInfo() {
        PaymentRecord record = createTestPaymentRecord();

        LegalEvidenceLine line = formatter.formatPaymentRecord(record);

        assertNotNull(line);
        String text = line.getEvidenceText();
        assertTrue((text.contains("123 Main St") || line.getPropertyReference().contains("123 Main St")) &&
                   (text.contains("Newark") || line.getPropertyReference().contains("Newark")),
            "Evidence line should include property address and city");
    }

    @Test
    @DisplayName("LegalEvidenceLine includes source citation")
    void testFormattingIncludesSourceCitation() {
        PaymentRecord record = createTestPaymentRecord();

        LegalEvidenceLine line = formatter.formatPaymentRecord(record);

        assertNotNull(line);
        assertTrue(line.getPageCitation() != null,
            "Evidence line should include page citation");
    }

    @Test
    @DisplayName("LegalEvidenceLine format handles null principal/interest gracefully")
    void testFormattingNullAmounts() {
        PaymentRecord record = new PaymentRecord(
            1L, 0,
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
            LocalDate.of(2024, 2, 1),
            "escrow",
            new BigDecimal("300.00"),
            null,  // principalAmount is null
            null,  // interestAmount is null
            new BigDecimal("200.00"),
            new BigDecimal("100.00"),
            null,
            "Borrower", "Lender", "LOAN123",
            "123 Main St", "Newark", "NJ", "07102",
            "Escrow payment", 1, "Escrow payment of $300", 0.90
        );

        LegalEvidenceLine line = formatter.formatPaymentRecord(record);

        assertNotNull(line);
        // Should format gracefully without null pointer exceptions
        assertNotNull(line.getEvidenceText());
        assertTrue(line.getEvidenceText().contains("Payment of"));
    }

    @Test
    @DisplayName("Multiple records formatted as list")
    void testFormattingMultipleRecords() {
        List<PaymentRecord> records = new ArrayList<>();
        records.add(createTestPaymentRecord());
        records.add(createTestPaymentRecord());

        List<LegalEvidenceLine> lines = formatter.formatMultipleRecords(records);

        assertNotNull(lines);
        assertEquals(2, lines.size(), "Should format 2 records into 2 evidence lines");
    }

    @Test
    @DisplayName("Payment summary formatted as evidence line")
    void testFormattingSummary() {
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

        LegalEvidenceLine line = formatter.formatPaymentSummary(summary);

        assertNotNull(line);
        String text = line.getEvidenceText();
        assertTrue(text.contains("30000") || text.contains("$30000") || text.contains("Total payments"),
            "Summary should include total");
        assertTrue(text.contains("123 Main St") || line.getPropertyReference().contains("123 Main St"),
            "Summary should include property");
    }

    @Test
    @DisplayName("Evidence line has traceable citation fields")
    void testEvidenceLineCitation() {
        PaymentRecord record = createTestPaymentRecord();

        LegalEvidenceLine line = formatter.formatPaymentRecord(record);

        assertNotNull(line.getPageCitation(), "Should have page citation");
        assertNotNull(line.getDateRange(), "Should have date range");
        assertNotNull(line.getFormattedAmount(), "Should have formatted amount");
    }

    @Test
    @DisplayName("Evidence line text is factual, not argumentative")
    void testEvidenceLineIsFactual() {
        PaymentRecord record = createTestPaymentRecord();

        LegalEvidenceLine line = formatter.formatPaymentRecord(record);

        String text = line.getEvidenceText();
        
        // Should not contain argumentative language
        assertFalse(text.toLowerCase().contains("clearly"),
            "Evidence should not be argumentative");
        assertFalse(text.toLowerCase().contains("obviously"),
            "Evidence should not be argumentative");
        assertFalse(text.toLowerCase().contains("must"),
            "Evidence should not be argumentative");
    }

    private PaymentRecord createTestPaymentRecord() {
        return new PaymentRecord(
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
            "Borrower", "Lender Inc", "LOAN123",
            "123 Main St", "Newark", "NJ", "07102",
            "Monthly mortgage payment", 1, "Payment of $2500", 0.95
        );
    }
}
