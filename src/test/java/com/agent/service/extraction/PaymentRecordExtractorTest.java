package com.agent.service.extraction;

import com.agent.model.PaymentRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentRecordExtractor using ANCHORED FIELD EXTRACTION.
 * 
 * Tests verify that extraction uses labeled fields, not loose pattern matching.
 */
class PaymentRecordExtractorTest {
    
    private PaymentRecordExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new PaymentRecordExtractor();
    }
    
    /**
     * Test anchored extraction from complete mortgage statement.
     * Verifies that all fields are extracted from their correct labels.
     */
    @Test
    void testExtractFromCompleteAnchoredMortgageStatement() {
        String text = "Property Address: 39586 S DARNER DR NEWARK, CA 94560\n" +
                      "Loan Number: 2109013512\n" +
                      "Regular Monthly Payment: $4,679.23\n" +
                      "01/02/26 PAYMENT $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size(), "Should extract exactly one record from anchored fields");
        PaymentRecord record = records.get(0);
        
        assertEquals("Newark", record.getPropertyName(), "Property from 'Property Address:' label");
        assertEquals("2109013512", record.getLoanNumber(), "Loan from 'Loan Number:' label");
        assertEquals(4679.23, record.getAmount(), "Amount from transaction row with PAYMENT keyword");
        assertEquals("01/02/26", record.getPaymentDate(), "Date from transaction row");
        assertTrue(record.getConfidence() >= 0.8, "High confidence for complete anchored statement");
    }
    
    /**
     * Test exception: Do NOT extract from unanchored amounts.
     * Outstanding Principal Balance should NOT be extracted as payment amount.
     */
    @Test
    void testRejectUnanchoredAmount() {
        String text = "Outstanding Principal Balance: $45,832.15\n" +
                      "Interest Rate Until: 03/01/32\n" +
                      "paid to date: 01/02/26";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        // Should be empty because:
        // - No "Regular Monthly Payment", "Total Payment Amount", "Amount Due", or Transaction row
        // - No anchored date field (just "paid to date" which isn't an anchor)
        assertTrue(records.isEmpty(), "Should reject amounts not from anchored fieldsa");
    }
    
    /**
     * Test transaction row extraction (date + PAYMENT + amount).
     * Highest priority for date/amount combination.
     */
    @Test
    void testExtractFromTransactionRow() {
        String text = "Transaction History:\n" +
                      "01/02/26 PAYMENT $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals("01/02/26", record.getPaymentDate(), "Date from transaction row");
        assertEquals(4679.23, record.getAmount(), "Amount from transaction row");
    }
    
    /**
     * Test "Regular Monthly Payment" label extraction.
     */
    @Test
    void testExtractFromRegularMonthlyPaymentLabel() {
        String text = "Loan Number: ABC123\n" +
                      "Regular Monthly Payment: $3,500.00\n" +
                      "Due Date: 01/15/26";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals(3500.0, record.getAmount(), "From 'Regular Monthly Payment' label");
    }
    
    /**
     * Test "Amount Due" label extraction.
     */
    @Test
    void testExtractFromAmountDueLabel() {
        String text = "Billing Period: Jan 1 - Feb 1\n" +
                      "Amount Due: $2,000.00\n" +
                      "Next Payment Due Date: 02/15/26";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals(2000.0, record.getAmount(), "From 'Amount Due' label");
        assertEquals("02/15/26", record.getPaymentDate(), "From 'Next Payment Due Date' label");
    }
    
    /**
     * Test "Total Payment Amount" label extraction.
     */
    @Test
    void testExtractFromTotalPaymentAmountLabel() {
        String text = "Principal Payment: $1,200.00\n" +
                      "Interest Payment: $800.00\n" +
                      "Total Payment Amount: $2,000.00";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals(2000.0, record.getAmount(), "From 'Total Payment Amount' label");
    }
    
    /**
     * Test null and blank input handling.
     */
    @Test
    void testNullInputReturnsEmpty() {
        List<PaymentRecord> records = extractor.extract(null);
        assertTrue(records.isEmpty());
    }
    
    @Test
    void testBlankInputReturnsEmpty() {
        List<PaymentRecord> records = extractor.extract("   ");
        assertTrue(records.isEmpty());
    }
    
    /**
     * Test rejection of text with no anchored fields.
     */
    @Test
    void testNoAnchoredFieldsReturnsEmpty() {
        String text = "The weather is nice. Outstanding Principal is $150,000. " +
                      "Interest Rate Until 2032 is 4.5%.";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertTrue(records.isEmpty(), "No anchored fields present - should return empty");
    }
    
    /**
     * Test extraction with minimal fields (just amount).
     */
    @Test
    void testExtractWithOnlyAmount() {
        String text = "Regular Monthly Payment: $1,500.00";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals(1500.0, record.getAmount());
        assertNull(record.getPaymentDate(), "No date field - should be null");
        assertNull(record.getPropertyName(), "No property field - should be null");
    }
    
    /**
     * Test extraction with minimal fields (just date).
     */
    @Test
    void testExtractWithOnlyDate() {
        String text = "Next Payment Due Date: 03/15/26";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals("03/15/26", record.getPaymentDate());
        assertNull(record.getAmount(), "No amount field - should be null");
    }
    
    /**
     * Test "Property Address:" extraction.
     */
    @Test
    void testExtractPropertyFromAddressLabel() {
        String text = "Property Address: 39586 S DARNER DR NEWARK, CA 94560\n" +
                      "Regular Monthly Payment: $4,000.00";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals("Newark", record.getPropertyName(), "Should extract city from property address");
    }
    
    /**
     * Test "Loan Number:" extraction.
     */
    @Test
    void testExtractLoanNumberFromLabel() {
        String text = "Loan Number: 2109013512\n" +
                      "Regular Monthly Payment: $4,500.00";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals("2109013512", record.getLoanNumber());
    }
    
    /**
     * Test "Date Paid" label extraction.
     */
    @Test
    void testExtractDateFromDatePaidLabel() {
        String text = "Date Paid: 01/10/26\n" +
                      "Amount: $3,500.00";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals("01/10/26", record.getPaymentDate(), "From 'Date Paid' label");
    }
    
    /**
     * Test priority: Transaction row date > Next Payment Due Date > Date Paid.
     */
    @Test
    void testDatePriorityTransactionRowFirst() {
        String text = "01/02/26 PAYMENT $4,679.23\n" +
                      "Next Payment Due Date: 02/15/26\n" +
                      "Date Paid: 01/01/26";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // Should use transaction row date (highest priority)
        assertEquals("01/02/26", record.getPaymentDate());
    }
    
    /**
     * Test TRANSACTION-ROW-FIRST: Transaction row amount takes priority.
     * Verify that transaction row amount overrides summary field amounts.
     */
    @Test
    void testAmountPriorityFromRegularMonthlyPayment() {
        String text = "Regular Monthly Payment: $5,000.00\n" +
                      "01/02/26 PAYMENT $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // TRANSACTION-ROW-FIRST: Must use transaction row amount (4679.23), not summary (5000.0)
        assertEquals(4679.23, record.getAmount());
    }
    
    /**
     * Test amounts with commas are parsed correctly.
     */
    @Test
    void testAmountParsingWithCommas() {
        String text = "Regular Monthly Payment: $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        assertEquals(4679.23, record.getAmount(), 0.01);
    }
    
    /**
     * Test TRANSACTION-ROW-FIRST: Transaction row date/amount override summary fields.
     * Verify that Interest Rate Until date is NOT used when transaction row present.
     */
    @Test
    void testTransactionRowDateOverridesSummaryDate() {
        String text = "Interest Rate Until 01/01/2029\n" +
                      "Loan Number: 2109013512\n" +
                      "Regular Monthly Payment $4,679.23\n" +
                      "01/02/26 PAYMENT $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // MUST use transaction row date, NOT Interest Rate Until date
        assertEquals("01/02/26", record.getPaymentDate(), 
            "Should use transaction row date, NOT Interest Rate Until (01/01/2029)");
        assertNotEquals("01/01/2029", record.getPaymentDate());
    }
    
    /**
     * Test TRANSACTION-ROW-FIRST: Transaction row amount overrides summary amounts.
     * Verify that summary labels don't override the transaction row amount.
     */
    @Test
    void testTransactionRowAmountOverridesSummaryAmount() {
        String text = "Principal Payment: $2,345.83\n" +
                      "Interest Payment: $2,334.40\n" +
                      "Regular Monthly Payment: $5,000.00\n" +
                      "01/02/26 PAYMENT ... $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // MUST use transaction row amount (4679.23), NOT summary field (5000.00)
        // NOT principal-only (2345.83) or interest-only (2334.40)
        assertEquals(4679.23, record.getAmount(), 0.01,
            "Should use transaction row amount $4,679.23, NOT summary labels or components");
        assertNotEquals(5000.0, record.getAmount());
        assertNotEquals(2345.83, record.getAmount());
    }
    
    /**
     * Test TRANSACTION-ROW-FIRST: Only transaction row is extracted.
     * Complete validation with all incompatible summary fields present.
     */
    @Test
    void testTransactionRowFirstCompleteExample() {
        String text = "Interest Rate Until: 01/01/2029\n" +
                      "Outstanding Principal Balance: $45,832.15\n" +
                      "Paid Year to Date: $12,000.00\n" +
                      "Principal: $2,345.83\n" +
                      "Interest: $2,334.40\n" +
                      "Property Address: 39586 S DARNER DR NEWARK, CA 94560\n" +
                      "Loan Number: 2109013512\n" +
                      "Regular Monthly Payment: $4,679.23\n" +
                      "01/02/26 PAYMENT ... $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // From transaction row (ONLY source for date+amount)
        assertEquals("01/02/26", record.getPaymentDate());
        assertEquals(4679.23, record.getAmount(), 0.01);
        
        // From anchored fields (always safe)
        assertEquals("Newark", record.getPropertyName());
        assertEquals("2109013512", record.getLoanNumber());
        
        // MUST NOT use these values:
        assertNotEquals("01/01/2029", record.getPaymentDate()); // Interest Rate Until
        assertNotEquals(45832.15, record.getAmount()); // Outstanding Principal Balance
        assertNotEquals(12000.0, record.getAmount()); // Paid Year to Date
        assertNotEquals(2345.83, record.getAmount()); // Principal component only
        assertNotEquals(2334.40, record.getAmount()); // Interest component only
    }
    
    /**
     * Test fallback to summary fields when NO transaction row exists.
     */
    @Test
    void testFallbackToSummaryFieldsWhenNoTransactionRow() {
        String text = "Loan Number: ABC123\n" +
                      "Regular Monthly Payment: $3,500.00\n" +
                      "Next Payment Due Date: 02/15/26";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // Uses summary fields since no transaction row present
        assertEquals(3500.0, record.getAmount());
        assertEquals("02/15/26", record.getPaymentDate());
        assertEquals("ABC123", record.getLoanNumber());
    }
    
    /**
     * Test rejection of component-only amounts (principal or interest alone).
     * When transaction row exists with total, component-only values are ignored.
     */
    @Test
    void testRejectComponentOnlyAmountWithTransactionRow() {
        String text = "Principal: $2,345.83\n" +
                      "Interest: $2,334.40\n" +
                      "01/02/26 PAYMENT ... $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // MUST use transaction row (4679.23), NOT components
        assertEquals(4679.23, record.getAmount(), 0.01);
        assertNotEquals(2345.83, record.getAmount()); // Principal only
        assertNotEquals(2334.40, record.getAmount()); // Interest only
    }
    
    /**
     * Test complete mortgage statement example from requirements.
     * This is the primary validation test.
     */
    @Test
    void testCompleteMortgageStatementExample() {
        String text = "Property Address: 39586 S DARNER DR NEWARK, CA 94560\n" +
                      "Loan Number: 2109013512\n" +
                      "Regular Monthly Payment $4,679.23\n" +
                      "01/02/26 PAYMENT ... $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // Verify all fields match requirement
        assertEquals("Newark", record.getPropertyName(), "propertyName = Newark");
        assertEquals("01/02/26", record.getPaymentDate(), "paymentDate = 01/02/26");
        assertEquals(4679.23, record.getAmount(), "amount = 4679.23");
        assertEquals("2109013512", record.getLoanNumber(), "loanNumber = 2109013512");
        assertTrue(record.getConfidence() >= 0.8, "confidence >= 0.8");
    }
}
