package com.agent.model;

/**
 * Structured case-level facts for reimbursement analysis.
 * 
 * Contains high-level case information used across analysis phases,
 * particularly for filtering post-separation payment evidence.
 * 
 * This is minimal and focused on reimbursement needs.
 */
public class CaseProfile {
    private final String dateOfSeparation;  // Format: MM/DD/YYYY or MM/DD/YY
    
    public CaseProfile(String dateOfSeparation) {
        this.dateOfSeparation = dateOfSeparation;
    }
    
    /**
     * Get the Date of Separation.
     * Format: MM/DD/YYYY or MM/DD/YY
     * 
     * @return DOS string, or null if not set
     */
    public String getDateOfSeparation() {
        return dateOfSeparation;
    }
    
    /**
     * Check if DOS is set.
     * 
     * @return true if DOS is available
     */
    public boolean hasSeparationDate() {
        return dateOfSeparation != null && !dateOfSeparation.isBlank();
    }
    
    @Override
    public String toString() {
        return "CaseProfile{" +
                "dateOfSeparation='" + dateOfSeparation + '\'' +
                '}';
    }
}
