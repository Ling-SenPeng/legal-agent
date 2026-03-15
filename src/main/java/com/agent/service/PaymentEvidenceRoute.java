package com.agent.service;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility component for routing queries to PaymentEvidenceService.
 * 
 * DESIGN INTENT:
 * This component identifies payment-related questions and determines whether
 * to use PaymentEvidenceService (payment_records table) as primary evidence source.
 * 
 * Responsibilities:
 * 1. Detect payment-related intent in queries
 * 2. Identify required property/date context for payment lookups
 * 3. Suggest fallback to chunks when payment records insufficient
 * 
 * Integration points:
 * - CaseAnalysisModeHandler.retrieveAndMergeEvidence() (planned)
 * - CaseAnalysisContextBuilder.buildContext() (planned)
 * 
 * Future: May expand to handle specialized payment routing modes
 * (e.g., PAYMENT_ANALYSIS mode for pure financial analysis).
 * 
 * CURRENT STATUS:
 * - Payment detection logic implemented
 * - CaseAnalysisModeHandler integration pending (see TODO markers)
 * - Full property-resolved payment table lookups pending database connectivity
 */
@Component
public class PaymentEvidenceRoute {
    
    // Payment-specific keywords that indicate queries should use PaymentEvidenceService
    private static final Set<String> PAYMENT_INTENT_KEYWORDS = new HashSet<>();
    static {
        // Core payment keywords
        PAYMENT_INTENT_KEYWORDS.add("payment");
        PAYMENT_INTENT_KEYWORDS.add("mortgage");
        PAYMENT_INTENT_KEYWORDS.add("principal");
        PAYMENT_INTENT_KEYWORDS.add("interest");
        PAYMENT_INTENT_KEYWORDS.add("escrow");
        PAYMENT_INTENT_KEYWORDS.add("property tax");
        PAYMENT_INTENT_KEYWORDS.add("insurance");
        PAYMENT_INTENT_KEYWORDS.add("loan");
        PAYMENT_INTENT_KEYWORDS.add("reimbursement");
        
        // Post-separation specific
        PAYMENT_INTENT_KEYWORDS.add("post-separation");
        PAYMENT_INTENT_KEYWORDS.add("post separation");
        PAYMENT_INTENT_KEYWORDS.add("after separation");
        PAYMENT_INTENT_KEYWORDS.add("after divorce");
    }

    /**
     * Detect if a query is payment-related and should use PaymentEvidenceService.
     * 
     * Returns true if query contains payment-specific intent indicators.
     * This supersedes chunk-based retrieval for these queries.
     * 
     * @param query The user's query
     * @return true if query is payment-related, false otherwise
     */
    public boolean isPaymentRelatedQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        
        String lowerQuery = query.toLowerCase();
        
        // Check for payment-specific keywords
        for (String keyword : PAYMENT_INTENT_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Identify property references in a query.
     * 
     * FUTURE: Integrate with NER/entity extraction to find property addresses.
     * CURRENT: Simple heuristic-based detection.
     * 
     * @param query The user's query
     * @return List of potential property references (may be city names, addresses, or "the property")
     */
    public List<String> extractPropertyReferences(String query) {
        List<String> properties = new ArrayList<>();
        
        if (query == null || query.isBlank()) {
            return properties;
        }
        
        String lower = query.toLowerCase();
        
        // Generic property references
        if (lower.contains("the property") || lower.contains("our property") ||
            lower.contains("marital property") || lower.contains("family home")) {
            properties.add("GENERIC_PROPERTY");
            return properties;
        }
        
        // Extract address patterns (simple heuristic for street numbers and names)
        // e.g., "123 Main St" or "456 Oak Avenue"
        if (query.matches(".*\\b\\d+\\s+[A-Za-z\\s]+(?:St|Street|Ave|Avenue|Rd|Road|Dr|Drive).*")) {
            properties.add("ADDRESS_DETECTED");
        }
        
        return properties;
    }

    /**
     * Determine if query requires date-based payment filtering.
     * 
     * Payment records often need chronological filtering (pre/post separation).
     * This method identifies queries that would benefit from date-filtered lookups.
     * 
     * @param query The user's query
     * @return true if query references specific time periods or separation, false otherwise
     */
    public boolean requiresDateFiltering(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        
        String lowerQuery = query.toLowerCase();
        
        // Separation-related date filtering
        if (lowerQuery.contains("post-separation") || 
            lowerQuery.contains("post separation") ||
            lowerQuery.contains("after separation") ||
            lowerQuery.contains("before separation")) {
            return true;
        }
        
        // Specific date references
        if (lowerQuery.matches(".*\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b.*") ||  // MM/DD/YYYY
            lowerQuery.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*")) {       // YYYY-MM-DD
            return true;
        }
        
        return false;
    }

    /**
     * Suggest whether to fall back to chunk retrieval.
     * 
     * CURRENT: Always suggest chunks as secondary source.
     * FUTURE: Implement heuristics to determine when chunk fallback is necessary
     *         (e.g., when payment records insufficient or date context missing).
     * 
     * @param query The user's query
     * @param paymentRecordCount Number of payment records found
     * @return true if chunk fallback should be used, false otherwise
     */
    public boolean shouldFallbackToChunks(String query, int paymentRecordCount) {
        // FUTURE: Implement smart fallback logic
        // Currently: If we have some payment records, use them; otherwise fallback to chunks
        return paymentRecordCount == 0;
    }

    /**
     * Log payment routing decision details.
     * 
     * Used by CaseAnalysisModeHandler and downstream services to track
     * how payment evidence is being sourced.
     * 
     * @param query The original query
     * @param isPaymentQuery Whether detected as payment-related
     * @param propertyRefs List of detected property references
     * @param requiresDateFilter Whether date filtering is needed
     * @return Formatted log string
     */
    public String generateRoutingLog(String query, boolean isPaymentQuery, 
                                      List<String> propertyRefs, boolean requiresDateFilter) {
        return String.format(
            "[PAYMENT_ROUTE] query='%s' payment_related=%s properties=%s date_filter=%s",
            query, isPaymentQuery, propertyRefs, requiresDateFilter
        );
    }
}
