package com.agent.service.extraction;

import com.agent.model.PaymentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured payment records from text snippets.
 * 
 * Uses heuristics and pattern matching to detect and extract:
 * - Payment dates (MM/DD/YYYY, MM/DD/YY)
 * - Amounts ($X,XXX.XX, $XXXX)
 * - Property names/addresses
 * - Mortgage/loan information
 */
@Component
public class PaymentRecordExtractor {
    private static final Logger logger = LoggerFactory.getLogger(PaymentRecordExtractor.class);
    
    // Patterns for amount extraction: $X,XXX.XX or $XXXX
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "\\$([0-9,]+\\.?[0-9]*)"
    );
    
    // Patterns for date extraction: MM/DD/YYYY or MM/DD/YY
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );
    
    // Keywords for distinguishing payment-related text
    private static final String[] PAYMENT_KEYWORDS = {
        "payment",
        "paid",
        "amount due",
        "monthly payment"
    };
    
    private static final String[] MORTGAGE_KEYWORDS = {
        "mortgage",
        "loan number",
        "principal",
        "interest"
    };
    
    /**
     * Extract payment records from a text snippet.
     * 
     * @param text The text to extract from
     * @return List of extracted PaymentRecords (empty if no payment signals found)
     */
    public List<PaymentRecord> extract(String text) {
        List<PaymentRecord> records = new ArrayList<>();
        
        if (text == null || text.isBlank()) {
            return records;
        }
        
        String lowerText = text.toLowerCase();
        
        // Check if text contains payment or mortgage signals
        boolean hasPaymentSignal = containsKeywords(lowerText, PAYMENT_KEYWORDS);
        boolean hasMortgageSignal = containsKeywords(lowerText, MORTGAGE_KEYWORDS);
        
        if (!hasPaymentSignal && !hasMortgageSignal) {
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] detected_payment=false text_length={}", text.length());
            return records;
        }
        
        // Extract date
        String paymentDate = extractDate(text);
        
        // Extract amount
        Double amount = extractAmount(text);
        
        // Extract property name (last location reference or "last word after address keywords")
        String propertyName = extractPropertyName(text);
        
        // Calculate confidence
        double confidence = calculateConfidence(hasPaymentSignal, hasMortgageSignal, paymentDate != null, amount != null);
        
        // Log extraction
        logger.debug("[PAYMENT_RECORD_EXTRACTOR] detected_payment=true date={} amount={} confidence={}",
            paymentDate, amount, confidence);
        
        // Create record if we have meaningful signal
        if (amount != null || paymentDate != null) {
            PaymentRecord record = new PaymentRecord(
                propertyName,
                paymentDate,
                amount,
                null,  // accountHolder not extracted
                text,  // sourceText
                null,  // sourceDocument not provided
                confidence
            );
            records.add(record);
        }
        
        return records;
    }
    
    /**
     * Check if text contains any of the provided keywords (case-insensitive).
     */
    private boolean containsKeywords(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extract the first date found in the text (MM/DD/YYYY or MM/DD/YY format).
     */
    private String extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Extract the first amount found in the text ($X,XXX.XX or $XXXX format).
     */
    private Double extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            String amountStr = matcher.group(1).replace(",", "");
            try {
                return Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Extract property name/address from the text.
     * Looks for city name before state abbreviation (e.g., "Newark CA").
     */
    private String extractPropertyName(String text) {
        String lowerText = text.toLowerCase();
        
        // Look for state abbreviations at the end of the line or near "CA"
        // Pattern: word CA or word CA,
        java.util.regex.Pattern statePattern = java.util.regex.Pattern.compile(
            "\\b([A-Z][a-z]+)\\s+(CA|California)\\b"
        );
        java.util.regex.Matcher matcher = statePattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);  // Return the word before CA
        }
        
        // Fallback: look for common address keywords
        String[] addressKeywords = {
            "property address:",
            "property:",
            "address:",
            "located"
        };
        
        for (String keyword : addressKeywords) {
            int pos = lowerText.indexOf(keyword);
            if (pos != -1) {
                String afterKeyword = text.substring(pos + keyword.length()).trim();
                // Extract words and find the first city-like word
                String[] words = afterKeyword.split("[,\\s]+");
                for (String word : words) {
                    // Look for capitalized word that's not a number
                    if (!word.isEmpty() && Character.isUpperCase(word.charAt(0)) && !word.matches("^[0-9]+.*")) {
                        return word;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Calculate confidence score based on available signals.
     * 
     * @param hasPaymentSignal Found payment-related keywords
     * @param hasMortgageSignal Found mortgage-related keywords
     * @param hasDate Found a date
     * @param hasAmount Found an amount
     * @return Confidence score (0.0 - 1.0)
     */
    private double calculateConfidence(boolean hasPaymentSignal, boolean hasMortgageSignal, boolean hasDate, boolean hasAmount) {
        double confidence = 0.0;
        
        if (hasPaymentSignal) confidence += 0.3;
        if (hasMortgageSignal) confidence += 0.2;
        if (hasDate) confidence += 0.2;
        if (hasAmount) confidence += 0.3;
        
        return Math.min(confidence, 1.0);
    }
}
