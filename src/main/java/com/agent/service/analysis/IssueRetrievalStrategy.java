package com.agent.service.analysis;

import com.agent.model.analysis.CaseIssue;

import java.util.List;

/**
 * Strategy for generating issue-driven retrieval queries.
 *
 * Maps detected legal issues to relevant retrieval keywords and constructs
 * optimized query variants to improve case fact retrieval quality.
 *
 * Implementation is rule-based and deterministic to support
 * debugging and incremental improvement.
 */
public interface IssueRetrievalStrategy {

    /**
     * Build issue-driven retrieval queries from detected issues and cleaned query.
     *
     * Strategy generates 1-5 optimized query variants by combining:
     * - The cleaned user query (fact-bearing terms)
     * - Issue-specific keywords (legal terminology for each issue type)
     * - Multi-term combinations for comprehensive coverage
     *
     * Example:
     * Input:
     *   cleanedQuery = "mortgage payments post-separation"
     *   issues = [REIMBURSEMENT, PROPERTY_CHARACTERIZATION]
     *
     * Output:
     *   [
     *     "mortgage payments post-separation",                    [core query]
     *     "reimbursement reimburse payment expense mortgage",     [REIMBURSEMENT keywords]
     *     "reimbursement expense",                                [short form]
     *     "property characterization community separate",         [PROPERTY keywords]
     *     "mortgage post-separation"                              [multi-term]
     *   ]
     *
     * @param cleanedQuery The user's query after analysis framing noise removal
     * @param issues Detected legal issues from the query
     * @return List of 1-5 optimized retrieval queries (deduplicated, ordered by relevance)
     */
    List<String> buildQueries(String cleanedQuery, List<CaseIssue> issues);
}
