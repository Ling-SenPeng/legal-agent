package com.agent.service.analysis;

import com.agent.model.analysis.CaseFact;
import com.agent.model.analysis.FactCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Service for filtering counterargument facts.
 * 
 * Removes noise and irrelevant facts before counterargument generation,
 * keeping only legally-relevant opposing arguments.
 * 
 * Allowed counterargument categories:
 * - Exclusive occupancy by other spouse
 * - Offset benefits received
 * - Mortgage assumption refusal
 * - Source of funds dispute
 * - Community benefit dispute
 * 
 * Filtered noise patterns:
 * - Interest rate information
 * - Loan maturity details
 * - Mortgage advertisement
 * - Insurance marketing text
 */
@Service
public class CounterArgumentFilter {
    private static final Logger logger = LoggerFactory.getLogger(CounterArgumentFilter.class);
    
    // Keywords that indicate noise/non-argument content
    private static final Set<String> NOISE_KEYWORDS = Set.of(
        "interest rate",
        "apr",
        "annual percentage",
        "loan maturity",
        "maturity date",
        "term ends",
        "prepayment penalty",
        "mortgage insurance",
        "pmi",
        "homeowners insurance",
        "property tax",
        "hazard insurance",
        "advertisement",
        "advertise",
        "promotional",
        "special offer",
        "rate buy-down",
        "financing program"
    );
    
    /**
     * Filter adverse facts to keep only valid counterargument facts.
     * 
     * @param unfavorableFacts List of adverse/unfavorable facts
     * @return Filtered list containing only valid counterargument facts
     */
    public List<CaseFact> filterCounterargumentFacts(List<CaseFact> unfavorableFacts) {
        if (unfavorableFacts == null || unfavorableFacts.isEmpty()) {
            logger.debug("[COUNTERARGUMENT_FILTER] No facts to filter");
            return List.of();
        }
        
        List<CaseFact> filtered = unfavorableFacts.stream()
            .filter(fact -> {
                boolean isValid = isValidCounterargumentFact(fact);
                if (!isValid) {
                    logger.debug("[COUNTERARGUMENT_FILTER] Filtering out noise: {}",
                        truncate(fact.getDescription(), 80));
                }
                return isValid;
            })
            .toList();
        
        logger.info("[COUNTERARGUMENT_FILTER] Filtered {} → {} facts",
            unfavorableFacts.size(), filtered.size());
        
        return filtered;
    }
    
    /**
     * Check if a fact is valid for counterargument use.
     * 
     * @param fact The fact to validate
     * @return true if the fact should be included
     */
    private boolean isValidCounterargumentFact(CaseFact fact) {
        String description = fact.getDescription();
        if (description == null || description.isEmpty()) {
            return false;
        }
        
        String descLower = description.toLowerCase();
        
        // Reject if contains noise keywords
        for (String keyword : NOISE_KEYWORDS) {
            if (descLower.contains(keyword)) {
                logger.debug("[COUNTERARGUMENT_FILTER] Rejected due to noise keyword: {}", keyword);
                return false;
            }
        }
        
        // Check if it's a valid counterargument category
        FactCategory category = classifyFact(description);
        boolean isValidCategory = category.isValidCounterargumentFact();
        
        if (!isValidCategory && category.isNoise()) {
            logger.debug("[COUNTERARGUMENT_FILTER] Rejected as noise category: {}", category);
        }
        
        return isValidCategory;
    }
    
    /**
     * Classify a fact into a FactCategory.
     * Simplified version - can be enhanced with ML-based classification.
     * 
     * @param factDescription The fact description text
     * @return Classified FactCategory
     */
    private FactCategory classifyFact(String factDescription) {
        if (factDescription == null) {
            return FactCategory.NOISY_FACT;
        }
        
        String lower = factDescription.toLowerCase();
        
        // Check for noise patterns
        if (lower.contains("interest") || lower.contains("rate") || lower.contains("apr")) {
            return FactCategory.INTEREST_RATE;
        }
        if (lower.contains("maturity") || lower.contains("term")) {
            return FactCategory.LOAN_MATURITY;
        }
        if (lower.contains("insurance") && (lower.contains("marketing") || lower.contains("offer"))) {
            return FactCategory.INSURANCE_MARKETING;
        }
        if (lower.contains("advertis") || lower.contains("promot") || lower.contains("offer")) {
            return FactCategory.MORTGAGE_ADVERTISEMENT;
        }
        
        // Check for valid counterargument patterns
        if (lower.contains("occupancy") || lower.contains("exclusive") || lower.contains("resides")) {
            return FactCategory.EXCLUSIVE_OCCUPANCY;
        }
        if (lower.contains("offset") || lower.contains("benefit") || lower.contains("received")) {
            return FactCategory.OFFSET_BENEFITS;
        }
        if (lower.contains("assumption") || lower.contains("refused") || lower.contains("assume")) {
            return FactCategory.MORTGAGE_ASSUMPTION_REFUSAL;
        }
        if (lower.contains("source") || lower.contains("fund") || lower.contains("paid")) {
            return FactCategory.SOURCE_OF_FUNDS_DISPUTE;
        }
        if (lower.contains("community") && (lower.contains("benefit") || lower.contains("property"))) {
            return FactCategory.COMMUNITY_BENEFIT_DISPUTE;
        }
        
        // Default - could be valid offset/benefit but unclassified
        return FactCategory.OFFSET_OR_BENEFIT_FACT;
    }
    
    /**
     * Truncate text for logging.
     */
    private String truncate(String text, int length) {
        if (text == null) return "null";
        return text.length() > length ? text.substring(0, length) + "..." : text;
    }
}
