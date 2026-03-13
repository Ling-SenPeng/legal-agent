package com.agent.service.analysis;

import com.agent.model.analysis.ClassifiedFact;
import com.agent.model.analysis.FactCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for FactClassifier.
 */
class FactClassifierTest {
    
    private FactClassifier classifier;
    
    @BeforeEach
    void setUp() {
        classifier = new FactClassifier();
    }
    
    @Test
    void testClassifyPaymentFact() {
        String text = "Both marital properties—our homes in San Jose and Newark—are paid for by me";
        ClassifiedFact result = classifier.classify(text);
        assertEquals(FactCategory.PAYMENT_FACT, result.getCategory(), "Should classify as PAYMENT_FACT");
    }
    
    @Test
    void testClassifyNoisyFactWithTableFragment() {
        String text = "23\n\nMonthly Fees and Payment\nDate Paid |Description Principal Interest";
        ClassifiedFact result = classifier.classify(text);
        assertEquals(FactCategory.NOISY_FACT, result.getCategory(), "Should classify as NOISY_FACT due to table fragments");
    }
    
    @Test
    void testClassifyNoisyFactWithBoilerplate() {
        String text = "real and personal $";
        ClassifiedFact result = classifier.classify(text);
        assertEquals(FactCategory.NOISY_FACT, result.getCategory(), "Should classify as NOISY_FACT due to boilerplate");
    }
    
    @Test
    void testClassifyUnrelatedFact() {
        String text = "On November 7, 2025, Petitioner was interviewed by his employer";
        ClassifiedFact result = classifier.classify(text);
        assertEquals(FactCategory.UNRELATED_FACT, result.getCategory(), "Should classify as UNRELATED_FACT");
    }
}
