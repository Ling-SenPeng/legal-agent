package com.agent.model.analysis;

/**
 * Enumeration of fact categories for classification.
 * 
 * Used to classify text snippets into semantic categories
 * relevant to legal analysis.
 */
public enum FactCategory {
    /**
     * Facts related to payments (paid, payment, mortgage, amount due, etc.)
     */
    PAYMENT_FACT,
    
    /**
     * Facts about the source of funds (paid by me, my funds, separate funds, etc.)
     */
    SOURCE_OF_FUNDS_FACT,
    
    /**
     * Facts about community obligations (mortgage, loan, property, etc.)
     */
    COMMUNITY_OBLIGATION_FACT,
    
    /**
     * Facts about exclusive use, occupancy, benefits, or offsets
     */
    OFFSET_OR_BENEFIT_FACT,
    
    /**
     * Facts that do not fit other categories
     */
    UNRELATED_FACT,
    
    /**
     * Noisy or low-quality text (null, too short, boilerplate, table fragments, etc.)
     */
    NOISY_FACT
}
