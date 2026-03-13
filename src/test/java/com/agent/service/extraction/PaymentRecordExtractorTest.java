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
     * Test priority: Anchored labels > Transaction row amount.
     * (But this test shows both would give same result)
     */
    @Test
    void testAmountPriorityFromRegularMonthlyPayment() {
        String text = "Regular Monthly Payment: $5,000.00\n" +
                      "01/02/26 PAYMENT $4,679.23";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertEquals(1, records.size());
        PaymentRecord record = records.get(0);
        
        // Should use first anchored label found (Regular Monthly Payment)
        assertEquals(5000.0, record.getAmount());
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
