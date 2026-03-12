package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.CaseAnalysisContext;
import com.agent.model.analysis.LegalIssueType;

import java.util.List;

/**
 * Interface for building case analysis context.
 * 
 * Combines issue extraction, fact extraction, and missing fact
 * identification to build a complete context for case analysis.
 */
public interface CaseAnalysisContextBuilder {
    
    /**
     * Build analysis context from case query and evidence.
     * 
     * @param caseQuery The user's case question
     * @param evidenceChunks Retrieved evidence chunks
     * @return CaseAnalysisContext with issues, facts, and missing facts
     */
    CaseAnalysisContext buildContext(String caseQuery, List<EvidenceChunk> evidenceChunks);
}
