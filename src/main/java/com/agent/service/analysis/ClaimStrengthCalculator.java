package com.agent.service.analysis;

import com.agent.model.analysis.CaseAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for calculating claim strength with consistent aggregation rules.
 * 
 * Enforces constraints on maximum strength based on fact distribution
 * and applies rules to ensure output strength doesn't exceed boundaries.
 */
@Service
public class ClaimStrengthCalculator {
    private static final Logger logger = LoggerFactory.getLogger(ClaimStrengthCalculator.class);
    
    /**
     * Calculate final claim strength with enforced constraints.
     * 
     * Rules:
     * 1. If weak_elements >= 2 OR missing_facts >= 3
     *    → max_strength = MODERATE
     * 2. If supporting_facts == 0
     *    → max_strength = WEAK
     * 3. If supporting_facts > adverse_facts AND missing_facts <= 2
     *    → strength = STRONG
     * 4. Otherwise → strength = MODERATE
     * 
     * @param supportingFacts Number of supporting facts
     * @param adverseFacts Number of adverse facts
     * @param missingFacts Number of missing facts
     * @param weakElementCount Number of weak elements
     * @return Calculated and bounded StrengthLevel
     */
    public CaseAnalysisResult.StrengthLevel calculateStrength(
        long supportingFacts,
        long adverseFacts,
        long missingFacts,
        int weakElementCount
    ) {
        logger.debug("[CLAIM_STRENGTH] Calculating strength: supporting={}, adverse={}, missing={}, weakElements={}",
            supportingFacts, adverseFacts, missingFacts, weakElementCount);
        
        // Calculate maximum allowed strength based on constraints
        CaseAnalysisResult.StrengthLevel maxStrength = calculateMaxStrength(
            supportingFacts, adverseFacts, missingFacts, weakElementCount
        );
        
        // Calculate ideal strength based on fact ratios
        CaseAnalysisResult.StrengthLevel idealStrength = calculateIdealStrength(
            supportingFacts, adverseFacts, missingFacts
        );
        
        // Return the lower of the two (bounded)
        CaseAnalysisResult.StrengthLevel finalStrength = boundStrength(idealStrength, maxStrength);
        
        if (logger.isDebugEnabled()) {
            logger.debug("[CLAIM_STRENGTH] maxStrength={}, idealStrength={}, finalStrength={}",
                maxStrength, idealStrength, finalStrength);
        }
        
        return finalStrength;
    }
    
    /**
     * Calculate the maximum allowed strength based on constraints.
     * 
     * @return Maximum allowed StrengthLevel
     */
    private CaseAnalysisResult.StrengthLevel calculateMaxStrength(
        long supportingFacts,
        long adverseFacts,
        long missingFacts,
        int weakElementCount
    ) {
        // Rule 1: Too many weak elements or missing facts
        if (weakElementCount >= 2 || missingFacts >= 3) {
            logger.debug("[CLAIM_STRENGTH_CONSTRAINT] Rule 1: weak_elements={} or missing_facts={} → max=MODERATE",
                weakElementCount, missingFacts);
            return CaseAnalysisResult.StrengthLevel.MODERATE;
        }
        
        // Rule 2: No supporting facts
        if (supportingFacts == 0) {
            logger.debug("[CLAIM_STRENGTH_CONSTRAINT] Rule 2: no supporting facts → max=WEAK");
            return CaseAnalysisResult.StrengthLevel.WEAK;
        }
        
        // No constraint - allow any strength
        return CaseAnalysisResult.StrengthLevel.VERY_STRONG;
    }
    
    /**
     * Calculate ideal strength based on fact ratios.
     * 
     * @return Ideal StrengthLevel
     */
    private CaseAnalysisResult.StrengthLevel calculateIdealStrength(
        long supportingFacts,
        long adverseFacts,
        long missingFacts
    ) {
        long totalFacts = supportingFacts + adverseFacts;
        
        if (totalFacts == 0) {
            return CaseAnalysisResult.StrengthLevel.MODERATE;
        }
        
        // Rule 3: Supporting facts overwhelm adverse facts with few missing
        if (supportingFacts > adverseFacts && missingFacts <= 2) {
            logger.debug("[CLAIM_STRENGTH_RULE] Rule 3: supporting > adverse and missing <= 2 → STRONG");
            return CaseAnalysisResult.StrengthLevel.STRONG;
        }
        
        // Rule 4: Otherwise moderate
        // (Could add more granularity here if needed)
        double supportingRatio = (double) supportingFacts / totalFacts;
        
        if (supportingRatio > 0.75) {
            return CaseAnalysisResult.StrengthLevel.STRONG;
        } else if (supportingRatio > 0.5) {
            return CaseAnalysisResult.StrengthLevel.MODERATE;
        } else if (supportingRatio > 0.25) {
            return CaseAnalysisResult.StrengthLevel.WEAK;
        } else {
            return CaseAnalysisResult.StrengthLevel.WEAK;
        }
    }
    
    /**
     * Bound the ideal strength to not exceed the maximum allowed.
     * 
     * @param idealStrength The calculated ideal strength
     * @param maxStrength The maximum allowed strength
     * @return Bounded strength (the lower of the two)
     */
    private CaseAnalysisResult.StrengthLevel boundStrength(
        CaseAnalysisResult.StrengthLevel idealStrength,
        CaseAnalysisResult.StrengthLevel maxStrength
    ) {
        // Define strength hierarchy for comparison
        int idealRank = getStrengthRank(idealStrength);
        int maxRank = getStrengthRank(maxStrength);
        
        // Return the lower-ranked (less optimistic) strength
        if (idealRank > maxRank) {
            logger.debug("[CLAIM_STRENGTH_BOUND] Bounding {} down to {}",
                idealStrength, maxStrength);
            return maxStrength;
        }
        return idealStrength;
    }
    
    /**
     * Get numeric rank for strength comparison (lower = weaker).
     * 
     * @return Rank value [0-4]
     */
    private int getStrengthRank(CaseAnalysisResult.StrengthLevel strength) {
        return switch (strength) {
            case VERY_WEAK -> 0;
            case WEAK -> 1;
            case MODERATE -> 2;
            case STRONG -> 3;
            case VERY_STRONG -> 4;
        };
    }
}
