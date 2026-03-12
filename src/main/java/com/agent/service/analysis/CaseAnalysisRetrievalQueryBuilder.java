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
 * Implements IssueRetrievalStrategy for issue-driven retrieval.
 *
 * V1 Implementation: Rule-based subquery generation, deterministic, no ML.
 */
@Service
public class CaseAnalysisRetrievalQueryBuilder implements IssueRetrievalStrategy {
    private static final Logger logger = LoggerFactory.getLogger(CaseAnalysisRetrievalQueryBuilder.class);

    /**
     * Issue-specific fact-driven keywords for retrieval.
     *
     * Maps issue type → concrete fact phrases and actionable terms instead of abstract concepts.
     * Focuses on specific behaviors, transactions, and observable actions that constitute evidence.
     * Avoids generic legal terms ("claim", "dispute", "analysis") in favor of factual specifics.
     */
    private static final Map<LegalIssueType, List<String>> ISSUE_KEYWORDS = Map.ofEntries(
        Map.entry(LegalIssueType.REIMBURSEMENT, Arrays.asList(
            "paid mortgage", "mortgage payment", "down payment", "paid expense",
            "post-separation expense", "reimbursement", "receipt", "invoice",
            "mortgage paid", "contributed to mortgage", "paid for improvement"
        )),
        Map.entry(LegalIssueType.SUPPORT, Arrays.asList(
            "child support payment", "spousal support", "income documentation",
            "earning capacity", "employed at", "monthly income", "financial support",
            "years of marriage", "duration of relationship"
        )),
        Map.entry(LegalIssueType.PROPERTY_CHARACTERIZATION, Arrays.asList(
            "down payment made", "down payment contributed", "acquired the property",
            "purchased before marriage", "inherited property", "property",
            "community property", "property title", "deed shows"
        )),
        Map.entry(LegalIssueType.TRACING, Arrays.asList(
            "separate property commingled", "source of funds", "inherited funds",
            "gift received", "premarital contribution", "documented source",
            "traced to separate account", "account statements"
        )),
        Map.entry(LegalIssueType.CUSTODY, Arrays.asList(
            "parenting schedule", "custody arrangement", "best interests of child",
            "school records", "doctor visits", "childcare provided", "custody",
            "overnight care", "stable environment", "child lives with", "custody agreement"
        )),
        Map.entry(LegalIssueType.EXCLUSIVE_USE, Arrays.asList(
            "occupied the family home", "exclusive occupancy", "unable to reside",
            "resided in the property", "set-off against support", "housing exclusive use",
            "family residence occupied by"
        )),
        Map.entry(LegalIssueType.RESTRAINING_ORDER, Arrays.asList(
            "threatening behavior", "harassment occurred", "abuse allegation",
            "protective order", "abuse", "documented threat", "witness to abuse",
            "police report of abuse", "domestic violence incident"
        )),
        Map.entry(LegalIssueType.OTHER, Arrays.asList(
            "occurred", "documented", "evidence shows", "records indicate", "payments made"
        ))
    );

    /**
     * Build optimized retrieval queries from cleaned query and detected issues.
     *
     * Strategy:
     * 1. Prioritize fact-driven phrases from issue-specific keywords
     * 2. Extract key terms from cleaned query (multi-word phrases + individual words)
     * 3. Extract fact-driven action words from cleaned query (paid, lived, worked, etc.)
     * 4. For each detected issue, add fact-specific subqueries
     * 5. Combine core terms with factual phrases to improve recall
     * 6. Deduplicate and return
     *
     * @param cleanedQuery Query after analysis noise removal
     * @param issues Detected legal issues from query
     * @return List of retrieval queries to execute
     */
    @Override
    public List<String> buildQueries(String cleanedQuery, List<CaseIssue> issues) {
        Set<String> queries = new LinkedHashSet<>(); // Maintains order, prevents duplicates

        // Always include cleaned query as primary query
        if (cleanedQuery != null && !cleanedQuery.isBlank()) {
            queries.add(cleanedQuery);
        }

        // Extract key terms from cleaned query
        List<String> coreTerms = extractKeyTerms(cleanedQuery);
        
        // Extract fact-driven action words (paid, lived, worked, etc.)
        List<String> factDrivenTerms = extractFactDrivenTerms(cleanedQuery);

        // Add issue-specific fact-driven subqueries (prioritized over generic keywords)
        for (CaseIssue issue : issues) {
            List<String> issueKeywords = ISSUE_KEYWORDS.getOrDefault(
                issue.getType(),
                List.of()
            );

            // Subquery 1A: Most specific fact phrase alone (highest priority)
            if (!issueKeywords.isEmpty()) {
                String firstFactPhrase = issueKeywords.get(0);
                queries.add(firstFactPhrase);
            }

            // Subquery 1B: Fact-driven term from query + issue-specific fact phrase
            if (!factDrivenTerms.isEmpty() && !issueKeywords.isEmpty()) {
                String combined = factDrivenTerms.get(0) + " " + issueKeywords.get(0);
                queries.add(combined);
            }

            // Subquery 2: Multiple fact phrases together for specific case patterns
            if (issueKeywords.size() >= 2) {
                // Look for single-word keywords (like "reimbursement", "custody") to ensure coverage
                List<String> singleWordKeywords = issueKeywords.stream()
                    .filter(kw -> !kw.contains(" "))
                    .limit(2)
                    .collect(Collectors.toList());
                
                if (!singleWordKeywords.isEmpty()) {
                    String singleKeywords = String.join(" ", singleWordKeywords);
                    queries.add(singleKeywords);
                } else {
                    // Fallback: use multi-word phrases
                    String multiFactPhrase = String.join(" ",
                        issueKeywords.stream().limit(2).collect(Collectors.toList()));
                    queries.add(multiFactPhrase);
                }
            }

            // Subquery 3: Core terms combined (context from original query) - only if available
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

        logger.debug("[CaseAnalysisRetrievalQueryBuilder] Built {} fact-driven retrieval queries " +
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

    /**
     * Extract fact-driven terms from cleaned query.
     *
     * Identifies concrete action/state words that represent factual events or conditions.
     * Examples: "paid", "purchased", "lived", "worked", "owned", "received"
     * These are prioritized over abstract concepts for evidence retrieval.
     *
     * @param cleanedQuery The cleaned query
     * @return List of fact-driven action/state terms
     */
    private List<String> extractFactDrivenTerms(String cleanedQuery) {
        if (cleanedQuery == null || cleanedQuery.isBlank()) {
            return List.of();
        }

        // Action/state words that indicate concrete facts
        Set<String> factDrivenWords = Set.of(
            "paid", "payment", "payments", "mortgage", "expense", "spent",
            "purchased", "bought", "acquired", "owned",
            "lived", "resided", "occupied", "occupying", "occupancy",
            "worked", "employed", "employment", "earned", "earning",
            "received", "gift", "inherited",
            "contributed", "contribution", "provided",
            "document", "documented", "evidence", "receipt", "invoice",
            "schedule", "arrangement", "care", "childcare",
            "threatened", "harassment", "abuse", "abused"
        );

        return Arrays.stream(cleanedQuery.split("\\s+"))
            .filter(word -> factDrivenWords.contains(word.toLowerCase()))
            .distinct()
            .collect(Collectors.toList());
    }
}
