package com.agent.service.analysis;

import com.agent.model.analysis.CaseIssue;
import java.util.List;

/**
 * Interface for extracting legal issues from case context.
 * 
 * Implementations use various techniques (heuristics, ML, etc.)
 * to identify relevant legal issues in a case based on query/context.
 */
public interface CaseIssueExtractor {
    
    /**
     * Extract legal issues from case query.
     * 
     * @param caseQuery The user's case question or statement
     * @return List of identified CaseIssue objects
     */
    List<CaseIssue> extractIssues(String caseQuery);
    
    /**
     * Extract legal issues with context hint.
     * 
     * @param caseQuery The case question
     * @param context Additional context (e.g., document text or facts)
     * @return List of identified CaseIssue objects
     */
    List<CaseIssue> extractIssues(String caseQuery, String context);
}
