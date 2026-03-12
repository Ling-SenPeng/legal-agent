package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.CaseAnalysisContext;
import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.authority.AuthoritySummary;

import java.util.List;

/**
 * Interface for building case analysis context.
 * 
 * Combines fact extraction and missing fact identification to build 
 * a complete context for case analysis.
 * 
 * Issues are pre-extracted and passed in to avoid recomputation.
 */
public interface CaseAnalysisContextBuilder {
    
    /**
     * Build analysis context from pre-extracted issues and evidence.
     * 
     * Avoids recomputing issues; instead receives already-identified issues
     * from the CASE_ANALYSIS handler to ensure consistency across the pipeline.
     * 
     * @param originalQuery The user's original case question (for context storage)
     * @param cleanedQuery The user's query after noise removal (for reference)
     * @param identifiedIssues Pre-extracted legal issues (from CaseIssueExtractor)
     * @param evidenceChunks Retrieved evidence chunks
     * @return CaseAnalysisContext with issues, facts, and missing facts
     */
    CaseAnalysisContext buildContext(
        String originalQuery,
        String cleanedQuery,
        List<CaseIssue> identifiedIssues,
        List<EvidenceChunk> evidenceChunks
    );
    
    /**
     * Build analysis context from pre-extracted issues, evidence, and authorities.
     * 
     * Extended version that includes legal authorities for each issue.
     * 
     * @param originalQuery The user's original case question (for context storage)
     * @param cleanedQuery The user's query after noise removal (for reference)
     * @param identifiedIssues Pre-extracted legal issues (from CaseIssueExtractor)
     * @param evidenceChunks Retrieved evidence chunks
     * @param authoritySummaries Legal authorities and rule summaries for each issue
     * @return CaseAnalysisContext with issues, facts, missing facts, and authorities
     */
    CaseAnalysisContext buildContextWithAuthorities(
        String originalQuery,
        String cleanedQuery,
        List<CaseIssue> identifiedIssues,
        List<EvidenceChunk> evidenceChunks,
        List<AuthoritySummary> authoritySummaries
    );
}
