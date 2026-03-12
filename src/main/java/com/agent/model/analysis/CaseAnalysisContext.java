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
    private final List<MissingFact> missingFacts;  // Facts needed but not found in evidence
    private final String legalStandardSummary;  // Relevant legal standards/authorities

    public CaseAnalysisContext(
        String caseQuery,
        List<CaseIssue> identifiedIssues,
        List<CaseFact> relevantFacts,
        List<MissingFact> missingFacts,
        String legalStandardSummary
    ) {
        this.caseQuery = caseQuery;
        this.identifiedIssues = identifiedIssues;
        this.relevantFacts = relevantFacts;
        this.missingFacts = missingFacts;
        this.legalStandardSummary = legalStandardSummary;
    }
    
    /**
     * Convenience constructor for backwards compatibility.
     * Creates context with empty missing facts list.
     */
    public CaseAnalysisContext(
        String caseQuery,
        List<CaseIssue> identifiedIssues,
        List<CaseFact> relevantFacts,
        String legalStandardSummary
    ) {
        this(caseQuery, identifiedIssues, relevantFacts, List.of(), legalStandardSummary);
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

    @Override
    public String toString() {
        return "CaseAnalysisContext{" +
                "issues=" + identifiedIssues.size() +
                ", facts=" + relevantFacts.size() +
                ", missingFacts=" + missingFacts.size() +
                '}';
    }
}
