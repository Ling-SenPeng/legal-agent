package com.agent.service.analysis;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds optimized retrieval queries for CASE_ANALYSIS mode.
 *
 * Transforms a cleaned query + detected issues into multiple retrieval subqueries
 * to improve coverage of relevant facts.
 *
 * V1 Implementation: Rule-based subquery generation, deterministic, no ML.
 */
@Service
public class CaseAnalysisRetrievalQueryBuilder {
    private static final Logger logger = LoggerFactory.getLogger(CaseAnalysisRetrievalQueryBuilder.class);

    /**
     * Issue-specific keyword expansions for retrieval.
     *
     * Maps issue type → keywords that should be included in retrieval queries.
     * Allows retrieval to find evidence even if original query doesn't use
     * these exact keywords.
     */
    private static final Map<LegalIssueType, List<String>> ISSUE_KEYWORDS = Map.ofEntries(
        Map.entry(LegalIssueType.REIMBURSEMENT, Arrays.asList(
            "reimbursement", "reimburse", "payment", "expense", "spent",
            "separated", "post-separation", "mortgage", "benefit"
        )),
        Map.entry(LegalIssueType.SUPPORT, Arrays.asList(
            "support", "alimony", "spousal", "child support", "guideline",
            "income", "earning", "duration", "length of marriage"
        )),
        Map.entry(LegalIssueType.PROPERTY_CHARACTERIZATION, Arrays.asList(
            "property", "characterization", "community", "separate",
            "acquisition", "down payment", "contribution", "title"
        )),
        Map.entry(LegalIssueType.TRACING, Arrays.asList(
            "tracing", "trace", "separate property", "commingled", "source",
            "funds", "contribution", "documentation", "evidence"
        )),
        Map.entry(LegalIssueType.CUSTODY, Arrays.asList(
            "custody", "parenting", "children", "best interests", "schedule",
            "arrangement", "care", "preference", "stability"
        )),
        Map.entry(LegalIssueType.EXCLUSIVE_USE, Arrays.asList(
            "exclusive use", "family home", "occupancy", "inability", "set-off",
            "dwelling", "residence", "possession"
        )),
        Map.entry(LegalIssueType.RESTRAINING_ORDER, Arrays.asList(
            "restraining order", "protective order", "abuse", "threat",
            "harassment", "stalking", "injunction", "violence"
        )),
        Map.entry(LegalIssueType.OTHER, Arrays.asList(
            "legal", "issue", "claim", "dispute", "question", "analysis"
        ))
    );

    /**
     * Build optimized retrieval queries from cleaned query and detected issues.
     *
     * Strategy:
     * 1. Extract key terms from cleaned query (multi-word phrases + individual words)
     * 2. For each detected issue, add issue-specific keywords
     * 3. Generate subqueries combining:
     *    - Core terms from cleaned query
     *    - Issue-specific keywords
     *    - Various combinations to improve retrieval recall
     * 4. Deduplicate and return
     *
     * @param cleanedQuery Query after analysis noise removal
     * @param issues Detected legal issues from query
     * @return List of retrieval queries to execute
     */
    public List<String> buildQueries(String cleanedQuery, List<CaseIssue> issues) {
        Set<String> queries = new LinkedHashSet<>(); // Maintains order, prevents duplicates

        // Always include cleaned query as primary query
        if (cleanedQuery != null && !cleanedQuery.isBlank()) {
            queries.add(cleanedQuery);
        }

        // Extract key terms from cleaned query
        List<String> coreTerms = extractKeyTerms(cleanedQuery);

        // Add issue-specific subqueries
        for (CaseIssue issue : issues) {
            List<String> issueKeywords = ISSUE_KEYWORDS.getOrDefault(
                issue.getType(),
                List.of()
            );

            // Subquery 1: Issue-specific keywords alone
            if (!issueKeywords.isEmpty()) {
                String issueQuery = String.join(" ", issueKeywords.stream()
                    .limit(3) // Limit to top 3 keywords
                    .collect(Collectors.toList()));
                queries.add(issueQuery);
            }

            // Subquery 2: Core terms + first issue keyword
            if (!coreTerms.isEmpty() && !issueKeywords.isEmpty()) {
                String combined = coreTerms.get(0) + " " + issueKeywords.get(0);
                queries.add(combined);
            }

            // Subquery 3: Multiple core terms (if available)
            if (coreTerms.size() >= 2) {
                String multiTermQuery = String.join(" ",
                    coreTerms.stream().limit(3).collect(Collectors.toList()));
                queries.add(multiTermQuery);
            }
        }

        // Fallback: If no issues detected, break cleaned query into phrases
        if (issues.isEmpty() && !coreTerms.isEmpty()) {
            if (coreTerms.size() >= 2) {
                String phraseQuery = String.join(" ",
                    coreTerms.stream().limit(4).collect(Collectors.toList()));
                queries.add(phraseQuery);
            }
        }

        // Remove empty queries and limit to reasonable size
        List<String> finalQueries = queries.stream()
            .filter(q -> !q.isBlank())
            .distinct()
            .limit(5) // Max 5 retrieval queries to prevent explosion
            .collect(Collectors.toList());

        logger.debug("[CaseAnalysisRetrievalQueryBuilder] Built {} retrieval queries " +
                "from cleanedQuery='{}' and {} issues: {}",
            finalQueries.size(), cleanedQuery, issues.size(), finalQueries);

        return finalQueries;
    }

    /**
     * Extract key terms from cleaned query.
     *
     * Simple heuristic: Split query into words, keep words > 4 characters
     * (filters out common short words like "the", "and", "for").
     *
     * @param cleanedQuery The cleaned query
     * @return List of key terms (longer words)
     */
    private List<String> extractKeyTerms(String cleanedQuery) {
        if (cleanedQuery == null || cleanedQuery.isBlank()) {
            return List.of();
        }

        return Arrays.stream(cleanedQuery.split("\\s+"))
            .filter(word -> word.length() > 3) // Keep words > 3 chars
            .distinct()
            .limit(8) // Max 8 key terms
            .collect(Collectors.toList());
    }
}
