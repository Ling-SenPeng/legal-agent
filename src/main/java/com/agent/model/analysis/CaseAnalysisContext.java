package com.agent.model.analysis;

import java.util.List;

/**
 * Context for case analysis containing extracted issues and facts.
 * 
 * Used to organize information extracted from a case file
 * before performing legal analysis and strength assessment.
 */
public class CaseAnalysisContext {
    private final String caseQuery;
    private final List<CaseIssue> identifiedIssues;
    private final List<CaseFact> relevantFacts;
    private final String legalStandardSummary;  // Relevant legal standards/authorities

    public CaseAnalysisContext(
        String caseQuery,
        List<CaseIssue> identifiedIssues,
        List<CaseFact> relevantFacts,
        String legalStandardSummary
    ) {
        this.caseQuery = caseQuery;
        this.identifiedIssues = identifiedIssues;
        this.relevantFacts = relevantFacts;
        this.legalStandardSummary = legalStandardSummary;
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

    public String getLegalStandardSummary() {
        return legalStandardSummary;
    }

    @Override
    public String toString() {
        return "CaseAnalysisContext{" +
                "issues=" + identifiedIssues.size() +
                ", facts=" + relevantFacts.size() +
                '}';
    }
}
