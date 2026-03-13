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
 * Extracts structured payment records from text snippets using ANCHORED FIELD EXTRACTION.
 * 
 * Only extracts from specific labeled fields to avoid mixing incompatible data:
 * - Amount: Only from "Regular Monthly Payment", "Total Payment Amount", "Amount Due", or transaction rows
 * - Date: Only from transaction rows near "PAYMENT", "Next Payment Due Date", or "Date Paid" rows
 * - Property: Only from "Property Address:" line
 * - Loan Number: Only from "Loan Number:" line
 * 
 * Does NOT extract from:
 * - Outstanding Principal Balance
 * - Interest Rate Until / Maturity Date
 * - Paid Year to Date
 * - Principal/Interest totals (unless part of transaction row)
 */
@Component
public class PaymentRecordExtractor {
    private static final Logger logger = LoggerFactory.getLogger(PaymentRecordExtractor.class);
    
    // Patterns for targeted extraction
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );
    
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "\\$([0-9,]+\\.?[0-9]*)"
    );
    
    // Anchored field patterns (labels that mark valid extraction points)
    private static final Pattern REGULAR_MONTHLY_PAYMENT_PATTERN = Pattern.compile(
        "(?i)regular\\s+monthly\\s+payment[:\\s]*\\$([0-9,]+\\.?[0-9]*)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TOTAL_PAYMENT_AMOUNT_PATTERN = Pattern.compile(
        "(?i)total\\s+payment\\s+amount[:\\s]*\\$([0-9,]+\\.?[0-9]*)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern AMOUNT_DUE_PATTERN = Pattern.compile(
        "(?i)amount\\s+due[:\\s]*\\$([0-9,]+\\.?[0-9]*)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Transaction row pattern: Date PAYMENT Amount (e.g., "01/02/26 PAYMENT ... $4,679.23")
    private static final Pattern TRANSACTION_ROW_PATTERN = Pattern.compile(
        "(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+.*?PAYMENT.*?\\$([0-9,]+\\.?[0-9]*)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PROPERTY_ADDRESS_PATTERN = Pattern.compile(
        "(?i)property\\s+address\\s*:\\s*(.+?)(?:\\n|$)"
    );
    
    private static final Pattern LOAN_NUMBER_PATTERN = Pattern.compile(
        "(?i)loan\\s+number\\s*:\\s*([0-9]+)"
    );
    
    private static final Pattern NEXT_PAYMENT_DUE_PATTERN = Pattern.compile(
        "(?i)next\\s+payment\\s+due\\s+date[:\\s]*([0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4})",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern DATE_PAID_PATTERN = Pattern.compile(
        "(?i)date\\s+paid[:\\s]*([0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4})",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Extract payment records from a text snippet using ANCHORED FIELD EXTRACTION.
     * Only extracts from specific labeled fields - does NOT use loose pattern matching.
     * 
     * @param text The text to extract from
     * @return List of extracted PaymentRecords (empty if no valid payment records found)
     */
    public List<PaymentRecord> extract(String text) {
        List<PaymentRecord> records = new ArrayList<>();
        
        if (text == null || text.isBlank()) {
            return records;
        }
        
        // Extract each field from its specific anchor
        String propertyName = extractPropertyNameAnchored(text);
        String loanNumber = extractLoanNumberAnchored(text);
        Double amount = extractAmountAnchored(text);
        String paymentDate = extractPaymentDateAnchored(text);
        
        // Log extracted fields
        if (logger.isDebugEnabled()) {
            logFieldExtraction("propertyName", propertyName, "Property Address:");
            logFieldExtraction("loanNumber", loanNumber, "Loan Number:");
            logFieldExtraction("amount", amount, "Regular Monthly Payment / Total Payment Amount / Amount Due / Transaction");
            logFieldExtraction("paymentDate", paymentDate, "Transaction row / Next Payment Due Date / Date Paid");
        }
        
        // Only create record if we have meaningful fields
        boolean hasAmount = amount != null && amount > 0;
        boolean hasDate = paymentDate != null && !paymentDate.isEmpty();
        
        if (!hasAmount && !hasDate) {
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] discard_reason=no_valid_fields text_length={}", text.length());
            return records;
        }
        
        // Calculate confidence based on field completeness
        double confidence = calculateConfidence(propertyName != null, loanNumber != null, hasDate, hasAmount);
        
        // Create and return record
        PaymentRecord record = new PaymentRecord(
            propertyName,
            paymentDate,
            amount,
            loanNumber,
            text,  // sourceText
            null,  // sourceDocument not provided
            confidence
        );
        records.add(record);
        
        logger.info("[PAYMENT_RECORD_EXTRACTOR] extracted_record=true date={} amount={} property={} loan={} confidence={}",
            paymentDate, amount, propertyName, loanNumber, confidence);
        
        return records;
    }
    
    /**
     * Extract property name ONLY from "Property Address:" line.
     * Extracts city name from address line: "Property Address: 39586 S DARNER DR NEWARK, CA 94560" → "Newark"
     */
    private String extractPropertyNameAnchored(String text) {
        Matcher matcher = PROPERTY_ADDRESS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        
        String addressLine = matcher.group(1).trim();
        
        // Extract city name: look for word before CA/California
        Pattern cityPattern = Pattern.compile("\\b([A-Z][A-Za-z]*)\\s*[,]?\\s*(CA|California)\\b");
        Matcher cityMatcher = cityPattern.matcher(addressLine);
        if (cityMatcher.find()) {
            String city = cityMatcher.group(1);
            // Validate it's not a street abbreviation (e.g., "S" in "S DARNER")
            if (city.length() > 1 && !city.matches("^[A-Z]{1,2}$")) {
                String normalized = city.substring(0, 1).toUpperCase() + city.substring(1).toLowerCase();
                logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=propertyName value={} source_label=Property Address:", normalized);
                return normalized;
            }
        }
        
        return null;
    }
    
    /**
     * Extract loan number ONLY from "Loan Number:" label.
     */
    private String extractLoanNumberAnchored(String text) {
        Matcher matcher = LOAN_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            String loanNumber = matcher.group(1);
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=loanNumber value={} source_label=Loan Number:", loanNumber);
            return loanNumber;
        }
        return null;
    }
    
    /**
     * Extract amount ONLY from predefined anchored sources:
     * 1. Regular Monthly Payment label
     * 2. Total Payment Amount label
     * 3. Amount Due label
     * 4. Transaction row: date PAYMENT amount
     * 
     * Order: Check anchored labels FIRST (more reliable), then transaction rows
     */
    private Double extractAmountAnchored(String text) {
        // Check anchored labels first (highest priority)
        
        Matcher regularMonthlyMatcher = REGULAR_MONTHLY_PAYMENT_PATTERN.matcher(text);
        if (regularMonthlyMatcher.find()) {
            Double amount = parseAmount(regularMonthlyMatcher.group(1));
            if (amount != null) {
                logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=amount value={} source_label=Regular Monthly Payment", amount);
                return amount;
            }
        }
        
        Matcher totalPaymentMatcher = TOTAL_PAYMENT_AMOUNT_PATTERN.matcher(text);
        if (totalPaymentMatcher.find()) {
            Double amount = parseAmount(totalPaymentMatcher.group(1));
            if (amount != null) {
                logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=amount value={} source_label=Total Payment Amount", amount);
                return amount;
            }
        }
        
        Matcher amountDueMatcher = AMOUNT_DUE_PATTERN.matcher(text);
        if (amountDueMatcher.find()) {
            Double amount = parseAmount(amountDueMatcher.group(1));
            if (amount != null) {
                logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=amount value={} source_label=Amount Due", amount);
                return amount;
            }
        }
        
        // Check transaction row pattern (has both date and amount)
        Matcher transactionMatcher = TRANSACTION_ROW_PATTERN.matcher(text);
        if (transactionMatcher.find()) {
            Double amount = parseAmount(transactionMatcher.group(2));
            if (amount != null) {
                logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=amount value={} source_label=Transaction Row (PAYMENT)", amount);
                return amount;
            }
        }
        
        logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=amount value=null discard_reason=no_anchored_amount_field");
        return null;
    }
    
    /**
     * Extract payment date ONLY from predefined anchored sources:
     * 1. Transaction row: date PAYMENT amount
     * 2. Next Payment Due Date label
     * 3. Date Paid label
     * 
     * Order: Transaction rows first (most specific payment signal), then labeled dates
     */
    private String extractPaymentDateAnchored(String text) {
        // Check transaction row pattern first (highest priority - most specific payment signal)
        Matcher transactionMatcher = TRANSACTION_ROW_PATTERN.matcher(text);
        if (transactionMatcher.find()) {
            String date = transactionMatcher.group(1);
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=paymentDate value={} source_label=Transaction Row (PAYMENT)", date);
            return date;
        }
        
        // Check Next Payment Due Date label
        Matcher nextPaymentMatcher = NEXT_PAYMENT_DUE_PATTERN.matcher(text);
        if (nextPaymentMatcher.find()) {
            String date = nextPaymentMatcher.group(1);
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=paymentDate value={} source_label=Next Payment Due Date", date);
            return date;
        }
        
        // Check Date Paid label
        Matcher datePaidMatcher = DATE_PAID_PATTERN.matcher(text);
        if (datePaidMatcher.find()) {
            String date = datePaidMatcher.group(1);
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=paymentDate value={} source_label=Date Paid", date);
            return date;
        }
        
        logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=paymentDate value=null discard_reason=no_anchored_date_field");
        return null;
    }
    
    /**
     * Parse amount string, handling commas and decimal points.
     * "4,679.23" → 4679.23
     */
    private Double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(amountStr.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Log field extraction details for debugging.
     */
    private void logFieldExtraction(String fieldName, Object value, String sourceLabel) {
        if (value != null) {
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] field={} value={} source_label={}", 
                fieldName, value, sourceLabel);
        }
    }
    
    /**
     * Calculate confidence score based on field completeness.
     * Anchored extraction means if we have the fields, they're valid - so confidence is high.
     */
    private double calculateConfidence(boolean hasProperty, boolean hasLoanNumber, boolean hasDate, boolean hasAmount) {
        double confidence = 0.0;
        
        // All fields present = high confidence
        if (hasProperty) confidence += 0.2;
        if (hasLoanNumber) confidence += 0.2;
        if (hasDate) confidence += 0.3;
        if (hasAmount) confidence += 0.3;
        
        return Math.min(confidence, 1.0);
    }
}
