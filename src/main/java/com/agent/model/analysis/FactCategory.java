package com.agent.model.analysis;

/**
 * Enumeration of fact categories for classification.
 * 
 * Used to classify text snippets into semantic categories
 * relevant to legal analysis. Categories also identify valid counterargument
 * facts and noise that should be filtered.
 */
public enum FactCategory {
    /**
     * Valid counterargument facts
     */
    EXCLUSIVE_OCCUPANCY("Exclusive occupancy by other spouse"),
    OFFSET_BENEFITS("Offset benefits received"),
    MORTGAGE_ASSUMPTION_REFUSAL("Mortgage assumption refusal"),
    SOURCE_OF_FUNDS_DISPUTE("Source of funds dispute"),
    COMMUNITY_BENEFIT_DISPUTE("Community benefit dispute"),
    
    /**
     * Supporting facts (payments, obligations, etc.)
     */
    PAYMENT_FACT("Payment fact"),
    SOURCE_OF_FUNDS_FACT("Source of funds fact"),
    COMMUNITY_OBLIGATION_FACT("Community obligation fact"),
    OFFSET_OR_BENEFIT_FACT("Offset or benefit fact"),
    
    /**
     * Noise categories to filter
     */
    INTEREST_RATE("Interest rate (noise)"),
    LOAN_MATURITY("Loan maturity (noise)"),
    MORTGAGE_ADVERTISEMENT("Mortgage advertisement (noise)"),
    INSURANCE_MARKETING("Insurance marketing (noise)"),
    
    /**
     * Unclassified facts
     */
    UNRELATED_FACT("Unrelated fact"),
    NOISY_FACT("Noisy or low-quality text");
    
    private final String description;
    
    FactCategory(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this category is a valid counterargument fact.
     * 
     * @return true if this category is relevant for counterarguments
     */
    public boolean isValidCounterargumentFact() {
        return this == EXCLUSIVE_OCCUPANCY ||
               this == OFFSET_BENEFITS ||
               this == MORTGAGE_ASSUMPTION_REFUSAL ||
               this == SOURCE_OF_FUNDS_DISPUTE ||
               this == COMMUNITY_BENEFIT_DISPUTE;
    }
    
    /**
     * Check if this category should be filtered out.
     * 
     * @return true if this category represents noise
     */
    public boolean isNoise() {
        return this == INTEREST_RATE ||
               this == LOAN_MATURITY ||
               this == MORTGAGE_ADVERTISEMENT ||
               this == INSURANCE_MARKETING ||
               this == NOISY_FACT;
    }
}
