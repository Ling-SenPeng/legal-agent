package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.CaseFact;
import com.agent.model.analysis.LegalIssueType;

import java.util.List;

/**
 * Interface for extracting case facts from evidence.
 * 
 * Converts evidence chunks into semantic case facts relevant to
 * legal issues and analysis.
 * 
 * PAYMENT EVIDENCE INTEGRATION (Phase 2-3 In Progress):
 * =====================================================
 * 
 * CURRENT ARCHITECTURE (Phase 2):
 * CaseAnalysisModeHandler.retrieveAndMergeEvidence() converts PaymentRecords to
 * synthetic EvidenceChunks via convertPaymentRecordsToChunks(). These chunks flow
 * through the same extraction pipeline as PDF chunks.
 * 
 * ISSUE IDENTIFIED:
 * Synthetic payment chunks may not trigger fact extraction patterns (dates, amounts
 * are numeric/structured, not natural language). Facts extracted might be minimal.
 * Additionally, PaymentRecord structured data is stored in ThreadLocal and accessed
 * later in findSupportingFactsForElement() via extractPaymentRecordsAsFacts().
 * 
 * SOLUTION (Phase 3 - To Be Implemented):
 * - [ ] Add method: extractFromPaymentRecords(List<PaymentRecord>, LegalIssueType)
 *       Direct extraction from structured data avoiding natural language dependency
 * - [ ] Call extractFromPaymentRecords() in RuleBasedCaseAnalysisContextBuilder
 *       during buildContextWithAuthorities() when PaymentRecords are available
 * - [ ] Add payment-specific fact polarity: structured amounts → SUPPORTING for reimbursement
 * - [ ] Blend PaymentRecord facts with chunk facts before context is returned
 * - [ ] Remove extractPaymentRecordsAsFacts() from element-specific logic
 *       (move to context building phase for consistency)
 * 
 * INTEGRATION POINTS:
 * 1. RuleBasedCaseFactExtractor - Add extractFromPaymentRecords() method
 * 2. RuleBasedCaseAnalysisContextBuilder.buildContextWithAuthorities()
 *    - Access currentPaymentRecords ThreadLocal
 *    - Call factExtractor.extractFromPaymentRecords()
 *    - Add to extractedFacts before context return
 * 3. CaseAnalysisModeHandler - Pass ThreadLocal reference or use dependency injection
 * 
 * See: currentPaymentRecords, convertPaymentRecordsToChunks, extractPaymentRecordsAsFacts */
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
