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
 * Extracts structured payment records from text snippets using TRANSACTION-ROW-FIRST EXTRACTION.
 * 
 * Priority: If a transaction row with PAYMENT exists, extract ONLY from that row.
 * Do not allow summary fields to override transaction row data.
 * 
 * Valid extraction sources:
 * - Transaction row (HIGHEST PRIORITY): Date + Amount from "DD/MM/YY PAYMENT ... $X" row
 * - Fallback (only if no transaction row): Summary labels
 *   - Amount: "Regular Monthly Payment", "Total Payment Amount", "Amount Due"
 *   - Date: "Next Payment Due Date", "Date Paid"
 * - Always anchored:
 *   - Property: "Property Address:" line
 *   - Loan: "Loan Number:" line
 * 
 * Does NOT extract from (even if present):
 * - Outstanding Principal Balance
 * - Interest Rate Until / Maturity Date (summary dates)
 * - Paid Year to Date
 * - Principal/Interest component only (without total)
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
        "(?i)loan\\s+number\\s*:\\s*([A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE
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
     * Extract payment records from a text snippet using TRANSACTION-ROW-FIRST extraction.
     * 
     * If a PAYMENT transaction row exists, extract date and amount ONLY from that row.
     * Do not allow summary field values to override transaction row values.
     * 
     * @param text The text to extract from
     * @return List of extracted PaymentRecords (empty if no valid payment records found)
     */
    public List<PaymentRecord> extract(String text) {
        List<PaymentRecord> records = new ArrayList<>();
        
        if (text == null || text.isBlank()) {
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] Skipping null or blank text");
            return records;
        }
        
        // TRANSACTION-ROW-FIRST: Check if a PAYMENT transaction row exists
        TransactionRowMatch transactionRow = extractTransactionRow(text);
        
        // Extract anchored fields (always safe - specific labels)
        String propertyName = extractPropertyNameAnchored(text);
        String loanNumber = extractLoanNumberAnchored(text);
        
        Double amount;
        String paymentDate;
        String source;
        
        if (transactionRow != null) {
            // Transaction row exists: use ONLY that row's date and amount
            paymentDate = transactionRow.date;
            amount = transactionRow.amount;
            source = "TRANSACTION_ROW";
            
            // Log what summary values were ignored
            String summaryDate = extractSummaryDateOnly(text);
            Double summaryAmount = extractSummaryAmountOnly(text);
            
            if (summaryDate != null && !summaryDate.equals(paymentDate)) {
                logger.info("[PAYMENT_RECORD_IGNORE] field=date value={} reason=transaction_row_takes_priority", summaryDate);
            }
            if (summaryAmount != null && Math.abs(summaryAmount - amount) > 0.01) {
                logger.info("[PAYMENT_RECORD_IGNORE] field=amount value={} reason=transaction_row_takes_priority", summaryAmount);
            }
            
            logger.info("[PAYMENT_RECORD_ROW_MATCH] row=true date={} amount={}", paymentDate, amount);
        } else {
            // No transaction row: fall back to summary fields
            amount = extractAmountAnchored(text);
            paymentDate = extractPaymentDateAnchored(text);
            source = "SUMMARY_FIELDS";
            
            logger.info("[PAYMENT_RECORD_SUMMARY_FALLBACK] date={} amount={} reason=no_transaction_row", paymentDate, amount);
        }
        
        // Only create record if we have meaningful fields
        boolean hasAmount = amount != null && amount > 0;
        boolean hasDate = paymentDate != null && !paymentDate.isEmpty();
        
        if (!hasAmount && !hasDate) {
            logger.debug("[PAYMENT_RECORD_EXTRACTOR] Discarding record: no_valid_fields text_length={}", text.length());
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
        
        logger.info("[PAYMENT_RECORD_FINAL] property={} date={} amount={} loan={} confidence={}", 
            propertyName, paymentDate, amount, loanNumber, confidence);
        logger.debug("[PAYMENT_RECORD_EXTRACTOR] Extracted record from source: {}", source);
        
        return records;
    }
    
    /**
     * Represents a matched transaction row with date and amount.
     */
    private static class TransactionRowMatch {
        final String date;
        final Double amount;
        
        TransactionRowMatch(String date, Double amount) {
            this.date = date;
            this.amount = amount;
        }
    }
    
    /**
     * Extract payment information from a PAYMENT transaction row.
     * Returns both date and amount together to ensure they come from the same row.
     * Pattern: "01/02/26 PAYMENT ... $4,679.23"
     * 
     * @return TransactionRowMatch if row found, null otherwise
     */
    private TransactionRowMatch extractTransactionRow(String text) {
        Matcher matcher = TRANSACTION_ROW_PATTERN.matcher(text);
        if (matcher.find()) {
            String date = matcher.group(1);
            Double amount = parseAmount(matcher.group(2));
            if (amount != null && amount > 0) {
                logger.info("[PAYMENT_RECORD_ROW_MATCH] row=true date={} amount={}", date, amount);
                return new TransactionRowMatch(date, amount);
            }
        }
        logger.debug("[PAYMENT_RECORD_ROW_MATCH] row=false reason=no_transaction_row_found");
        return null;
    }
    
    /**
     * Extract property name ONLY from "Property Address:" line.
     * Extracts city name from address line: "Property Address: 39586 S DARNER DR NEWARK, CA 94560" → "Newark"
     */
    private String extractPropertyNameAnchored(String text) {
        // Normalize text first to handle PDF line breaks and whitespace
        String normalizedText = normalizeAddressText(text);
        
        Matcher matcher = PROPERTY_ADDRESS_PATTERN.matcher(normalizedText);
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
     * Extract amount ONLY from summary field labels (fallback if no transaction row).
     * Check in priority order:
     * 1. Regular Monthly Payment label
     * 2. Total Payment Amount label
     * 3. Amount Due label
     */
    private Double extractAmountAnchored(String text) {
        Matcher regularMonthlyMatcher = REGULAR_MONTHLY_PAYMENT_PATTERN.matcher(text);
        if (regularMonthlyMatcher.find()) {
            Double amount = parseAmount(regularMonthlyMatcher.group(1));
            if (amount != null) {
                logger.info("[PAYMENT_RECORD_IGNORE_AVOID] field=amount source=Regular Monthly Payment value={}", amount);
                return amount;
            }
        }
        
        Matcher totalPaymentMatcher = TOTAL_PAYMENT_AMOUNT_PATTERN.matcher(text);
        if (totalPaymentMatcher.find()) {
            Double amount = parseAmount(totalPaymentMatcher.group(1));
            if (amount != null) {
                logger.info("[PAYMENT_RECORD_IGNORE_AVOID] field=amount source=Total Payment Amount value={}", amount);
                return amount;
            }
        }
        
        Matcher amountDueMatcher = AMOUNT_DUE_PATTERN.matcher(text);
        if (amountDueMatcher.find()) {
            Double amount = parseAmount(amountDueMatcher.group(1));
            if (amount != null) {
                logger.info("[PAYMENT_RECORD_IGNORE_AVOID] field=amount source=Amount Due value={}", amount);
                return amount;
            }
        }
        
        logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=amount value=null discard_reason=no_summary_amount_field");
        return null;
    }
    
    /**
     * Extract amount ONLY for comparison/debugging - used to log what was ignored.
     * Same as extractAmountAnchored but separated for clarity.
     */
    private Double extractSummaryAmountOnly(String text) {
        return extractAmountAnchored(text);
    }
    
    /**
     * Extract payment date ONLY from summary field labels (fallback if no transaction row).
     * Check in priority order:
     * 1. Next Payment Due Date label
     * 2. Date Paid label
     * 
     * NOTE: Do NOT fall back to unanchored dates like "Interest Rate Until" or "Maturity Date"
     */
    private String extractPaymentDateAnchored(String text) {
        // Check Next Payment Due Date label
        Matcher nextPaymentMatcher = NEXT_PAYMENT_DUE_PATTERN.matcher(text);
        if (nextPaymentMatcher.find()) {
            String date = nextPaymentMatcher.group(1);
            logger.info("[PAYMENT_RECORD_IGNORE_AVOID] field=date source=Next Payment Due Date value={}", date);
            return date;
        }
        
        // Check Date Paid label
        Matcher datePaidMatcher = DATE_PAID_PATTERN.matcher(text);
        if (datePaidMatcher.find()) {
            String date = datePaidMatcher.group(1);
            logger.info("[PAYMENT_RECORD_IGNORE_AVOID] field=date source=Date Paid value={}", date);
            return date;
        }
        
        logger.debug("[PAYMENT_RECORD_EXTRACTOR] field=paymentDate value=null discard_reason=no_summary_date_field");
        return null;
    }
    
    /**
     * Extract date ONLY for comparison/debugging - used to log what was ignored.
     * Same as extractPaymentDateAnchored but separated for clarity.
     */
    private String extractSummaryDateOnly(String text) {
        return extractPaymentDateAnchored(text);
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
     * Normalize PDF-extracted address text by handling line breaks and collapsing whitespace.
     * 
     * Converts: "39586 S DARNER DR\rNEWARK, CA 94560"
     * Into:     "39586 S DARNER DR NEWARK, CA 94560"
     * 
     * Steps:
     * 1. Replace \r and \n with spaces
     * 2. Collapse consecutive whitespace to single space
     * 3. Trim leading/trailing whitespace
     * 4. Convert to uppercase for consistent matching
     * 
     * @param text The raw address text from PDF extraction
     * @return Normalized address text ready for pattern matching
     */
    private String normalizeAddressText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        
        // Replace carriage returns and newlines with spaces
        String normalized = text.replace("\r", " ").replace("\n", " ");
        
        // Collapse consecutive whitespace into single space
        normalized = normalized.replaceAll("\\s+", " ");
        
        // Trim and uppercase for matching
        normalized = normalized.trim().toUpperCase();
        
        return normalized;
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
