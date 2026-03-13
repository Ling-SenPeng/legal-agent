package com.agent.service.analysis;

import com.agent.model.analysis.CaseFact;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Wraps a CaseFact with property attribution information.
 * 
 * Preserves original CaseFact while adding metadata about which community property
 * the fact relates to (based on evidence source and extracted payment/mortgage details).
 */
public class PropertyAttributedFact {
    private final CaseFact baseFact;
    private final String propertyCity;          // e.g., "Newark", "San Jose"
    private final String propertyAddress;       // e.g., "39586 S DARNER DR"
    private final String loanNumber;            // Loan identifier if available
    private final String sourceFilename;        // Source document filename
    
    public PropertyAttributedFact(
        CaseFact baseFact,
        String propertyCity,
        String propertyAddress,
        String loanNumber,
        String sourceFilename
    ) {
        this.baseFact = baseFact;
        this.propertyCity = propertyCity;
        this.propertyAddress = propertyAddress;
        this.loanNumber = loanNumber;
        this.sourceFilename = sourceFilename;
    }
    
    /**
     * Create PropertyAttributedFact from just a CaseFact (no property info).
     */
    public PropertyAttributedFact(CaseFact baseFact) {
        this(baseFact, null, null, null, null);
    }
    
    // === Getters ===
    
    public CaseFact getBaseFact() {
        return baseFact;
    }
    
    public String getPropertyCity() {
        return propertyCity;
    }
    
    public String getPropertyAddress() {
        return propertyAddress;
    }
    
    public String getLoanNumber() {
        return loanNumber;
    }
    
    public String getSourceFilename() {
        return sourceFilename;
    }
    
    /**
     * Get a normalized property key for grouping.
     * Uses city name as primary key, with loan number as fallback.
     */
    public String getPropertyKey() {
        if (propertyCity != null && !propertyCity.isEmpty()) {
            return propertyCity.toLowerCase();
        }
        if (loanNumber != null && !loanNumber.isEmpty()) {
            return "loan_" + loanNumber.toLowerCase();
        }
        return "unknown";
    }
    
    /**
     * Check if this fact has property attribution.
     */
    public boolean hasPropertyAttribution() {
        return propertyCity != null || loanNumber != null;
    }
    
    /**
     * Get human-readable property identifier for logging and output.
     */
    public String getPropertyDisplay() {
        if (propertyCity != null) {
            if (propertyAddress != null) {
                return propertyCity + " (" + propertyAddress + ")";
            }
            return propertyCity;
        }
        if (loanNumber != null) {
            return "Loan #" + loanNumber;
        }
        return "Unknown Property";
    }
    
    @Override
    public String toString() {
        return "PropertyAttributedFact{" +
                "fact=" + baseFact.getDescription() +
                ", property=" + getPropertyDisplay() +
                ", source=" + sourceFilename +
                '}';
    }
}

