package com.agent.service.analysis;

import com.agent.model.analysis.ClassifiedFact;
import com.agent.model.analysis.FactCategory;
import org.springframework.stereotype.Component;

/**
 * Minimal deterministic classifier for fact snippets.
 * 
 * Uses simple heuristics and keyword matching to classify text.
 */
@Component
public class FactClassifier {
    
    private static final int MIN_LENGTH = 20;
    
    // Boilerplate fragments and common phrases to filter out
    private static final String[] BOILERPLATE_PHRASES = {
        "petitioner:",
        "respondent:",
        "real and personal $",
        "check the box"
    };
    
    // Table-like fragments to exclude
    private static final String[] TABLE_FRAGMENTS = {
        "Date Paid",
        "Principal",
        "Interest",
        "Escrow",
        "Charges"
    };
    
    /**
     * Classify a text snippet.
     * 
     * @param text The text to classify
     * @return ClassifiedFact with category, confidence, and reason
     */
    public ClassifiedFact classify(String text) {
        // Check for NOISY_FACT first
        if (isNoisyFact(text)) {
            return new ClassifiedFact(text, FactCategory.NOISY_FACT, 0.9, "Text is noisy or boilerplate");
        }
        
        String lowerText = text.toLowerCase();
        
        // Check for PAYMENT_FACT
        if (containsKeywords(lowerText, "paid", "payment", "mortgage", "amount due", "due date", "principal", "interest")) {
            return new ClassifiedFact(text, FactCategory.PAYMENT_FACT, 0.85, "Contains payment-related keywords");
        }
        
        // Check for SOURCE_OF_FUNDS_FACT
        if (containsKeywords(lowerText, "paid by me", "i paid", "my funds", "my own funds", "separate funds")) {
            return new ClassifiedFact(text, FactCategory.SOURCE_OF_FUNDS_FACT, 0.85, "Contains source of funds keywords");
        }
        
        // Check for COMMUNITY_OBLIGATION_FACT
        if (containsKeywords(lowerText, "mortgage", "loan", "property", "community obligation", "marital property")) {
            return new ClassifiedFact(text, FactCategory.COMMUNITY_OBLIGATION_FACT, 0.8, "Contains community obligation keywords");
        }
        
        // Check for OFFSET_OR_BENEFIT_FACT
        if (containsKeywords(lowerText, "exclusive use", "occupancy", "benefit", "offset")) {
            return new ClassifiedFact(text, FactCategory.OFFSET_OR_BENEFIT_FACT, 0.8, "Contains offset or benefit keywords");
        }
        
        // Default to UNRELATED_FACT
        return new ClassifiedFact(text, FactCategory.UNRELATED_FACT, 0.5, "Does not match any category");
    }
    
    /**
     * Check if text is noisy or low quality.
     */
    private boolean isNoisyFact(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        
        String trimmed = text.trim();
        
        // Check length
        if (trimmed.length() < MIN_LENGTH) {
            return true;
        }
        
        // Check if mostly numeric
        int numericCount = (int) trimmed.chars().filter(Character::isDigit).count();
        if (numericCount > trimmed.length() * 0.5) {
            return true;
        }
        
        // Check for boilerplate phrases
        String lowerText = trimmed.toLowerCase();
        for (String phrase : BOILERPLATE_PHRASES) {
            if (lowerText.contains(phrase.toLowerCase())) {
                return true;
            }
        }
        
        // Check for table-like fragments
        for (String fragment : TABLE_FRAGMENTS) {
            if (trimmed.contains(fragment)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if text contains any of the provided keywords.
     */
    private boolean containsKeywords(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
