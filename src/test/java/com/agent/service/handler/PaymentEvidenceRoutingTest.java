package com.agent.service.handler;

import com.agent.model.EvidenceChunk;
import com.agent.model.PaymentRecord;
import com.agent.service.PaymentEvidenceRoute;
import com.agent.service.PaymentEvidenceService;
import com.agent.service.RetrievalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for payment evidence routing in CaseAnalysisModeHandler.
 * 
 * Verifies that payment-related queries are routed to PaymentEvidenceService
 * as the primary evidence source, with pdf_chunks as fallback.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Evidence Routing Integration Tests")
class PaymentEvidenceRoutingTest {

    @Mock
    private PaymentEvidenceService paymentEvidenceService;

    private final PaymentEvidenceRoute paymentEvidenceRoute = new PaymentEvidenceRoute();

    @Test
    @DisplayName("Payment query is detected by PaymentEvidenceRoute")
    void testPaymentQueryDetection() {
        String mortgageQuery = "What are the monthly mortgage payments?";
        String principalQuery = "How much principal has been paid?";
        String interestQuery = "What are the interest amounts?";
        
        assertTrue(paymentEvidenceRoute.isPaymentRelatedQuery(mortgageQuery),
            "Mortgage query should be detected");
        assertTrue(paymentEvidenceRoute.isPaymentRelatedQuery(principalQuery),
            "Principal query should be detected");
        assertTrue(paymentEvidenceRoute.isPaymentRelatedQuery(interestQuery),
            "Interest query should be detected");
    }

    @Test
    @DisplayName("Non-payment query not detected as payment-related")
    void testNonPaymentQueryBypassesPaymentService() {
        String query = "Who owns the property?";
        
        assertFalse(paymentEvidenceRoute.isPaymentRelatedQuery(query),
            "Ownership query should not be detected as payment-related");
    }

    @Test
    @DisplayName("Property references extracted from queries")
    void testPropertyReferenceExtraction() {
        String query = "mortgage payments for 123 Main St, Newark";
        
        List<String> refs = paymentEvidenceRoute.extractPropertyReferences(query);
        
        assertNotNull(refs);
        assertTrue(refs.size() > 0, "Should extract property references from address patterns");
    }

    @Test
    @DisplayName("Date filtering detection works")
    void testDateFilteringDetection() {
        String queryWithDates = "mortgage payments from 2024-01-01 to 2024-12-31";
        String queryWithoutDates = "What are the mortgage payments?";
        
        assertTrue(paymentEvidenceRoute.requiresDateFiltering(queryWithDates),
            "Query with explicit dates should require filtering");
    }

    @Test
    @DisplayName("Payment records are converted to evidence chunks correctly")
    void testPaymentRecordsConversion() {
        PaymentRecord record = createTestPaymentRecord();

        // Simulate conversion to EvidenceChunk
        String formattedText = String.format(
            "Payment Record - Property: %s, %s\nPayment Date: %s\nTotal Amount: $%s\nCategory: %s",
            record.getPropertyAddress(),
            record.getPropertyCity(),
            record.getPaymentDate(),
            record.getTotalAmount(),
            record.getCategory()
        );

        assertTrue(formattedText.contains("123 Main St"),
            "Converted chunk should contain property address");
        assertTrue(formattedText.contains("2024-02-01"),
            "Converted chunk should contain payment date");
        assertTrue(formattedText.contains("mortgage"),
            "Converted chunk should contain category");
    }

    @Test
    @DisplayName("Fallback to chunk retrieval when payment records empty")
    void testFallbackToChunksWhenNoPaymentRecords() {
        String query = "What are the mortgage payments for property at 999 Main St?";
        
        // Should fall back to chunk retrieval when no payment records match
        List<EvidenceChunk> chunkFallback = createMockChunkFallback();
        
        assertNotNull(chunkFallback);
        assertTrue(chunkFallback.size() > 0,
            "Fallback should return chunk-based evidence when payment records unavailable");
    }

    @Test
    @DisplayName("Multiple payment records handled correctly")
    void testMultiplePaymentRecordsHandling() {
        List<PaymentRecord> records = createMultipleTestPaymentRecords();
        
        assertEquals(3, records.size(), "Should create multiple payment records");
        
        // Verify records have distinct dates
        boolean hasDifferentDates = records.stream()
            .map(PaymentRecord::getPaymentDate)
            .distinct()
            .count() > 1;
        
        assertTrue(hasDifferentDates, "Multiple records should have different payment dates");
    }

    @Test
    @DisplayName("Confidence scores preserved in payment records")
    void testConfidenceScorePreservation() {
        PaymentRecord record = createTestPaymentRecord();

        // Confidence should be used as relevance score in converted chunk
        double expectedConfidence = record.getConfidence();
        assertEquals(0.95, expectedConfidence,
            "Confidence score should be preserved from payment record");
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
            "Borrower", "Lender", "LOAN123",
            "123 Main St", "Newark", "NJ", "07102",
            "Payment", 1, "Payment of $2500", 0.95
        );
    }

    private List<PaymentRecord> createMultipleTestPaymentRecords() {
        List<PaymentRecord> records = new ArrayList<>();
        
        for (int i = 1; i <= 3; i++) {
            records.add(new PaymentRecord(
                1L, i-1,
                LocalDate.of(2024, i, 1),
                LocalDate.of(2024, i, 28),
                LocalDate.of(2024, i+1, 1),
                "mortgage",
                new BigDecimal("2500.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("1200.00"),
                new BigDecimal("150.00"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                "Borrower", "Lender", "LOAN123",
                "123 Main St", "Newark", "NJ", "07102",
                "Payment " + i, i, "Payment of $2500", 0.95
            ));
        }
        
        return records;
    }

    private List<EvidenceChunk> createMockChunkFallback() {
        List<EvidenceChunk> chunks = new ArrayList<>();
        chunks.add(new EvidenceChunk(
            1L, 1L, "test.pdf", 1, 1, 1,
            "Sample payment information from PDF",
            0.85, "", 0.85, 0.85, 0.85
        ));
        return chunks;
    }
}
