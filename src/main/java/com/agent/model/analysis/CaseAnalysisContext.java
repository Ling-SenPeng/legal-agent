package com.agent.model.analysis;

import com.agent.model.analysis.authority.AuthoritySummary;

import java.util.List;

/**
 * Context for case analysis containing extracted issues, facts, and legal authorities.
 * 
 * Used to organize information extracted from a case file
 * before performing legal analysis and strength assessment.
 * 
 * Includes:
 * - Identified legal issues
 * - Retrieved case facts (favorable and unfavorable)
 * - Missing facts needed for complete analysis
 * - Legal standard summaries
 * - Relevant legal authorities (statutes, cases, practice guides)
 */
public class CaseAnalysisContext {
    private final String caseQuery;
    private final List<CaseIssue> identifiedIssues;
    private final List<CaseFact> relevantFacts;
    private final List<MissingFact> missingFacts;  // Facts needed but not found in evidence
    private final String legalStandardSummary;  // Relevant legal standards/authorities
    private final List<AuthoritySummary> authoritySummaries;  // Summarized legal authorities per issue

    public CaseAnalysisContext(
        String caseQuery,
        List<CaseIssue> identifiedIssues,
        List<CaseFact> relevantFacts,
        List<MissingFact> missingFacts,
        String legalStandardSummary,
        List<AuthoritySummary> authoritySummaries
    ) {
        this.caseQuery = caseQuery;
        this.identifiedIssues = identifiedIssues;
        this.relevantFacts = relevantFacts;
        this.missingFacts = missingFacts;
        this.legalStandardSummary = legalStandardSummary;
        this.authoritySummaries = authoritySummaries;
    }
    
    /**
     * Convenience constructor for backwards compatibility.
     * Creates context with empty missing facts and authority summaries lists.
     */
    public CaseAnalysisContext(
        String caseQuery,
        List<CaseIssue> identifiedIssues,
        List<CaseFact> relevantFacts,
        String legalStandardSummary
    ) {
        this(caseQuery, identifiedIssues, relevantFacts, List.of(), legalStandardSummary, List.of());
    }
    
    /**
     * Convenience constructor with missing facts but no authorities.
     */
    public CaseAnalysisContext(
        String caseQuery,
        List<CaseIssue> identifiedIssues,
        List<CaseFact> relevantFacts,
        List<MissingFact> missingFacts,
        String legalStandardSummary
    ) {
        this(caseQuery, identifiedIssues, relevantFacts, missingFacts, legalStandardSummary, List.of());
    }

    public String getCaseQuery() {
        return caseQuery;
    }

    public List<CaseIssue> getIdentifiedIssues() {
        return identifiedIssues;
    }

    public List<CaseFact> getRelevantFacts() {
        return relevantFacts;
    }

    public List<MissingFact> getMissingFacts() {
        return missingFacts;
    }

    public String getLegalStandardSummary() {
        return legalStandardSummary;
    }

    public List<AuthoritySummary> getAuthoritySummaries() {
        return authoritySummaries;
    }

    @Override
    public String toString() {
        return "CaseAnalysisContext{" +
                "issues=" + identifiedIssues.size() +
                ", facts=" + relevantFacts.size() +
                ", missingFacts=" + missingFacts.size() +
                ", authorities=" + authoritySummaries.size() +
                '}';
    }
}
