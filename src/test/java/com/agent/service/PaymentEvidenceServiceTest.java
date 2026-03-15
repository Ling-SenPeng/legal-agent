package com.agent.service;

import com.agent.model.PaymentRecord;
import com.agent.model.PaymentSummary;
import com.agent.repository.PaymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RuleBasedPaymentEvidenceService.
 * 
 * Verifies that the service correctly coordinates between repository,
 * analyzer, and formatter layers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEvidenceService Integration Tests")
class PaymentEvidenceServiceTest {

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    private RuleBasedPaymentEvidenceService paymentEvidenceService;

    private List<PaymentRecord> testPaymentRecords;

    @BeforeEach
    void setUp() {
        // Create real service dependencies where possible
        PropertyPaymentAnalyzer analyzer = new PropertyPaymentAnalyzer();
        LegalEvidenceFormatter formatter = new LegalEvidenceFormatter();
        
        paymentEvidenceService = new RuleBasedPaymentEvidenceService(
            paymentRecordRepository,
            analyzer,
            formatter
        );
        
        testPaymentRecords = createTestPaymentRecords();
    }

    private List<PaymentRecord> createTestPaymentRecords() {
        List<PaymentRecord> records = new ArrayList<>();
        
        PaymentRecord record1 = new PaymentRecord(
            1L,                                          // pdfDocumentId
            0,                                           // statementIndex
            LocalDate.of(2024, 1, 1),                    // statementPeriodStart
            LocalDate.of(2024, 1, 31),                   // statementPeriodEnd
            LocalDate.of(2024, 2, 1),                    // paymentDate
            "mortgage",                                  // category
            new BigDecimal("2500.00"),                   // totalAmount
            new BigDecimal("1000.00"),                   // principalAmount
            new BigDecimal("1200.00"),                   // interestAmount
            new BigDecimal("150.00"),                    // escrowAmount
            new BigDecimal("100.00"),                    // taxAmount
            new BigDecimal("50.00"),                     // insuranceAmount
            "Borrower",                                  // payerName
            "Lender Inc",                                // payeeName
            "LOAN123",                                   // loanNumber
            "123 Main St",                               // propertyAddress
            "Newark",                                    // propertyCity
            "NJ",                                        // propertyState
            "07102",                                     // propertyZip
            "Monthly mortgage payment",                  // description
            1,                                           // sourcePage
            "Payment of $2500",                          // sourceSnippet
            0.95                                         // confidence
        );
        records.add(record1);
        
        return records;
    }

    @Test
    @DisplayName("getPaymentsByDocument returns records from repository")
    void testGetPaymentsByDocument() {
        when(paymentRecordRepository.findByPdfDocumentId(1L))
            .thenReturn(testPaymentRecords);

        List<PaymentRecord> results = paymentEvidenceService.getPaymentsByDocument(1L);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("mortgage", results.get(0).getCategory());
    }

    @Test
    @DisplayName("getPaymentsByProperty returns records from repository")
    void testGetPaymentsByProperty() {
        when(paymentRecordRepository.findByPropertyAddress("123 Main St"))
            .thenReturn(testPaymentRecords);

        List<PaymentRecord> results = paymentEvidenceService.getPaymentsByProperty("123 Main St", "Newark");

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Empty payment records list handled gracefully")
    void testEmptyPaymentRecordsList() {
        when(paymentRecordRepository.findByPdfDocumentId(999L))
            .thenReturn(new ArrayList<>());

        List<PaymentRecord> results = paymentEvidenceService.getPaymentsByDocument(999L);

        assertNotNull(results);
        assertEquals(0, results.size());
    }
}
