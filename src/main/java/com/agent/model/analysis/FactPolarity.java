package com.agent.model.analysis;

/**
 * Represents the polarity or tendency of a case fact relative to the client's position.
 * 
 * Uses a cautious, multi-valued approach rather than optimistic binary classification.
 * Default is UNKNOWN when polarity cannot be determined from available heuristics.
 */
public enum FactPolarity {
    /**
     * Fact directly supports the client's legal position.
     * Example: "Client worked 60+ hours per week" (supports primary custody claim)
     */
    SUPPORTING,
    
    /**
     * Fact is adverse to the client's legal position.
     * Example: "Client missed 20 school meetings" (undermines primary custody claim)
     */
    ADVERSE,
    
    /**
     * Fact is relevant but neither clearly supports nor undermines the position.
     * Example: "Property was acquired in 2015" (relevant to characterization but not inherently favorable/adverse)
     */
    NEUTRAL,
    
    /**
     * Polarity cannot be determined from available information or context.
     * This is the default/safe value when heuristics are inconclusive.
     * Example: "Respondent's employment details mentioned" (lacks specifics)
     */
    UNKNOWN;
    
    /**
     * Check if this polarity is favorable to the client's case.
     * Useful for quick boolean-style checks.
     */
    public boolean isFavorable() {
        return this == SUPPORTING;
    }
    
    /**
     * Check if this polarity is adverse to the client's case.
     */
    public boolean isAdverse() {
        return this == ADVERSE;
    }
    
    /**
     * Check if polarity is determined (not UNKNOWN).
     */
    public boolean isDetermined() {
        return this != UNKNOWN;
    }
}
