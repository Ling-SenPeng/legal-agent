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
 */
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
