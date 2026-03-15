package com.agent.service.analysis;

import com.agent.model.MortgagePaymentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEPRECATED: Text-based parsing of mortgage statements.
 * 
 * This service is now deprecated in favor of structured PaymentRecord queries from the payment_records table.
 * As of Phase 3, CaseAnalysisModeHandler uses DB-backed PaymentRecord data exclusively.
 * 
 * Keep this service for:
 * - Documents with severe OCR artifacts requiring manual parsing
 * - Historical statements not yet ingested to payment_records table
 * - Testing and validation of statement parsing accuracy
 * - Legacy fallback when structured records unavailable
 * 
 * Do NOT use this service in new code. Always use PaymentEvidenceService methods instead.
 */
@Deprecated(since = "Phase 3", forRemoval = false)
@Service
public class MortgageStatementParser {
    private static final Logger logger = LoggerFactory.getLogger(MortgageStatementParser.class);
    
    private static final Pattern TRANSACTION_ROW_PATTERN = Pattern.compile(
        "(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+" +  // Date
        "([^\\d\\$]*?)\\s+" +                    // Description
        "([\\d,]+\\.\\d{2})?\\s*" +              // Principal (optional)
        "([\\d,]+\\.\\d{2})?\\s*" +              // Interest (optional)
        "([\\d,]+\\.\\d{2})?\\s*" +              // Escrow (optional)
        "([\\d,]+\\.\\d{2})"                     // Total (required)
    );
    
    private static final Set<String> TRANSACTION_BLOCK_MARKERS = Set.of(
        "transaction activity",
        "payment history",
        "transaction details",
        "payment record"
    );
    
    /**
     * Parse payment records from mortgage statement text.
     * 
     * Only binds fields that occur within the same payment block.
     * 
     * @param statementText The mortgage statement text
     * @return List of parsed MortgagePaymentRecord objects
     */
    public List<MortgagePaymentRecord> parsePayments(String statementText) {
        List<MortgagePaymentRecord> records = new ArrayList<>();
        
        if (statementText == null || statementText.isEmpty()) {
            logger.debug("[MORTGAGE_PARSER] No text to parse");
            return records;
        }
        
        // Split by newlines for line-by-line parsing
        String[] lines = statementText.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Check if this line is a transaction row
            Matcher matcher = TRANSACTION_ROW_PATTERN.matcher(line);
            if (matcher.find()) {
                MortgagePaymentRecord record = parseTransactionRow(matcher);
                if (record != null) {
                    records.add(record);
                    logger.debug("[MORTGAGE_PARSER] Parsed transaction: {}", record);
                }
            }
        }
        
        logger.info("[MORTGAGE_PARSER] Parsed {} payment records from statement", records.size());
        return records;
    }
    
    /**
     * Parse a transaction row into a MortgagePaymentRecord.
     * 
     * @param matcher Regex matcher with captured groups
     * @return MortgagePaymentRecord or null if parsing fails
     */
    private MortgagePaymentRecord parseTransactionRow(Matcher matcher) {
        try {
            String dateStr = matcher.group(1);
            String description = matcher.group(2).trim();
            String principalStr = matcher.group(3);
            String interestStr = matcher.group(4);
            String escrowStr = matcher.group(5);
            String totalStr = matcher.group(6);
            
            // Parse date
            LocalDate paymentDate = parseDate(dateStr);
            
            // Parse amounts
            BigDecimal principal = parseAmount(principalStr);
            BigDecimal interest = parseAmount(interestStr);
            BigDecimal escrow = parseAmount(escrowStr);
            BigDecimal total = parseAmount(totalStr);
            
            // Build and return record
            return new MortgagePaymentRecord.Builder()
                .paymentDate(paymentDate)
                .principal(principal)
                .interest(interest)
                .totalPayment(total)
                .transactionDescription(description)
                .build();
                
        } catch (Exception e) {
            logger.debug("[MORTGAGE_PARSER] Error parsing transaction row: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse date string in various formats.
     * 
     * @param dateStr Date string (MM/DD/YYYY or MM/DD/YY)
     * @return LocalDate or null
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        try {
            // Try MM/DD/YYYY first
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("M/d/yyyy"));
        } catch (Exception e1) {
            try {
                // Try MM/DD/YY
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("M/d/yy"));
            } catch (Exception e2) {
                logger.debug("[MORTGAGE_PARSER] Could not parse date: {}", dateStr);
                return null;
            }
        }
    }
    
    /**
     * Parse amount string, removing currency symbols and commas.
     * 
     * @param amountStr Amount string (e.g., "1,234.56")
     * @return BigDecimal or null
     */
    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            return null;
        }
        
        try {
            // Remove currency symbols and commas
            String cleaned = amountStr.replaceAll("[^\\d.]", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            logger.debug("[MORTGAGE_PARSER] Could not parse amount: {}", amountStr);
            return null;
        }
    }
}
