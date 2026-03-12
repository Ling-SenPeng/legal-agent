package com.agent.service.analysis.authority;

import com.agent.model.analysis.CaseIssue;

import java.util.List;

/**
 * Strategy for generating authority retrieval queries based on issue type.
 * 
 * Different issue types require different authority search approaches.
 * This interface allows pluggable implementations for different
 * authority retrieval strategies.
 */
public interface IssueAuthorityRetrievalStrategy {
    /**
     * Build authority retrieval queries for a given issue.
     * 
     * @param issue The case issue to find authorities for
     * @return List of retrieval queries optimized for authority search
     */
    List<String> buildAuthorityQueries(CaseIssue issue);
}
