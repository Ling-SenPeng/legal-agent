package com.agent.service.extraction;

import com.agent.model.PaymentRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentRecordExtractor.
 */
class PaymentRecordExtractorTest {
    
    private PaymentRecordExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new PaymentRecordExtractor();
    }
    
    @Test
    void testExtractPaymentRecordFromMortgageStatement() {
        String text = "09/02/2025 PAYMENT $4,679.23\n" +
                      "Principal $2,350.96\n" +
                      "Interest $2,328.27\n" +
                      "Property address: 39586 Darner Dr Newark CA";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertFalse(records.isEmpty(), "Should extract at least one payment record");
        PaymentRecord record = records.get(0);
        
        assertEquals("09/02/2025", record.getPaymentDate());
        assertEquals(4679.23, record.getAmount());
        assertEquals("Newark", record.getPropertyName());
        assertFalse(record.getConfidence() < 0.5, "Confidence should be at least 0.5");
    }
    
    @Test
    void testExtractPaymentWithStandardFormat() {
        String text = "Payment made on 03/15/2024 for $1,500.00";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertFalse(records.isEmpty());
        PaymentRecord record = records.get(0);
        
        assertEquals("03/15/2024", record.getPaymentDate());
        assertEquals(1500.0, record.getAmount());
        assertTrue(record.getConfidence() > 0.6);
    }
    
    @Test
    void testExtractPaymentWithoutDecimalAmount() {
        String text = "Monthly payment of $2000 due on 12/01/2025";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertFalse(records.isEmpty());
        PaymentRecord record = records.get(0);
        
        assertEquals(2000.0, record.getAmount());
        assertEquals("12/01/2025", record.getPaymentDate());
    }
    
    @Test
    void testNoPaymentSignalsReturnsEmpty() {
        String text = "The weather is nice today and the sky is blue";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertTrue(records.isEmpty(), "Should return empty list when no payment signals found");
    }
    
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
    
    @Test
    void testExtractWithMortgageKeywordOnly() {
        String text = "Mortgage loan number 123456 with principal of $150,000";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertFalse(records.isEmpty(), "Should extract with mortgage keyword even without payment keyword");
        PaymentRecord record = records.get(0);
        assertEquals(150000.0, record.getAmount());
    }
    
    @Test
    void testExtractWithMultipleAmounts() {
        String text = "Interest $500 and principal payment of $1,200 on 06/30/2025";
        
        List<PaymentRecord> records = extractor.extract(text);
        
        assertFalse(records.isEmpty());
        PaymentRecord record = records.get(0);
        // Should extract the first amount found
        assertEquals(500.0, record.getAmount());
    }
}
