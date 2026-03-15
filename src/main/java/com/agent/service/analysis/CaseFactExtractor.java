package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.CaseFact;
import com.agent.model.analysis.LegalIssueType;

import java.util.List;

/**
 * Interface for extracting case facts from evidence.
 * 
 * Converts evidence chunks into semantic case facts relevant to
 * legal issues and analysis. * 
 * PAYMENT EVIDENCE INTEGRATION NOTES:
 * ==================================
 * This interface currently works only with EvidenceChunk (PDF-based evidence).
 * Future versions should support PaymentRecord extraction with minimal changes:
 * 
 * TODO: FUTURE - Add payment record support
 * - [ ] Add method: extractFromPaymentRecords(List<PaymentRecord>, LegalIssueType)
 * - [ ] Implement in RuleBasedCaseFactExtractor
 * - [ ] Convert PaymentRecords to CaseFact with proper source attribution
 * - [ ] Ensure payment facts marked with appropriate evidence source
 * - [ ] Blend facts from both chunks and payment records in context builder
 * 
 * INTEGRATION STRATEGY:
 * 1. CaseAnalysisModeHandler detects payment query (PaymentEvidenceRoute)
 * 2. Calls PaymentEvidenceService to get PaymentRecords
 * 3. Converts PaymentRecords to EvidenceChunk-like format OR
 * 4. Calls extractFromPaymentRecords() directly in context builder
 * 5. Blends payment facts with chunk facts before returning context
 * 
 * See: paymentEvidenceService, RuleBasedCaseAnalysisContextBuilder */
public interface CaseFactExtractor {
    
    /**
     * Extract case facts from evidence chunks.
     * 
     * @param chunks List of evidence chunks to extract facts from
     * @param relevantIssue The issue type these facts relate to
     * @return List of extracted CaseFact objects
     */
    List<CaseFact> extractFacts(List<EvidenceChunk> chunks, LegalIssueType relevantIssue);
    
    /**
     * Extract all facts from evidence with issue type inference.
     * 
     * @param chunks List of evidence chunks
     * @return List of all extracted facts with inferred issue types
     */
    List<CaseFact> extractAllFacts(List<EvidenceChunk> chunks);
}
