package com.agent.service.analysis.authority;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Rule-based implementation of authority retrieval strategy.
 * 
 * Maps issue types to optimized authority retrieval queries.
 * Queries are designed to find relevant statutes, cases, and practice guides
 * for each legal issue type.
 */
@Component
public class RuleBasedIssueAuthorityRetrievalStrategy implements IssueAuthorityRetrievalStrategy {
    private static final Map<LegalIssueType, List<String>> AUTHORITY_QUERIES = initializeQueries();

    private static Map<LegalIssueType, List<String>> initializeQueries() {
        Map<LegalIssueType, List<String>> queries = new HashMap<>();
        
        // REIMBURSEMENT: post-separation payment reimbursement doctrine
        queries.put(LegalIssueType.REIMBURSEMENT, List.of(
            "Epstein reimbursement",
            "post separation payment reimbursement",
            "spouse reimbursement mortgage payment"
        ));
        
        // SUPPORT: post-separation spousal or child support
        queries.put(LegalIssueType.SUPPORT, List.of(
            "post separation payment reimbursement law",
            "community debt payment after separation",
            "spousal support obligation"
        ));
        
        // EXCLUSIVE_USE: exclusive use and occupancy
        queries.put(LegalIssueType.EXCLUSIVE_USE, List.of(
            "exclusive use property offset family law",
            "Watts charges occupancy",
            "family residence exclusive use"
        ));
        
        // PROPERTY_CHARACTERIZATION: community vs separate property
        queries.put(LegalIssueType.PROPERTY_CHARACTERIZATION, List.of(
            "community property characterization",
            "separate property tracing rule",
            "property division characterization"
        ));
        
        // TRACING: funds tracing and source identification
        queries.put(LegalIssueType.TRACING, List.of(
            "separate property tracing family law",
            "source of funds tracing rule",
            "fund tracing doctrine"
        ));
        
        // CUSTODY: custody and visitation rights
        queries.put(LegalIssueType.CUSTODY, List.of(
            "best interest of child custody rule",
            "custody determination factors",
            "child custody standard"
        ));
        
        // RESTRAINING_ORDER: restraining orders and protective orders
        queries.put(LegalIssueType.RESTRAINING_ORDER, List.of(
            "temporary restraining order standard",
            "domestic violence restraining order law",
            "protective order requirements"
        ));
        
        // OTHER: generic fallback queries
        queries.put(LegalIssueType.OTHER, List.of(
            "family law issue",
            "legal issue"
        ));
        
        return queries;
    }

    @Override
    public List<String> buildAuthorityQueries(CaseIssue issue) {
        List<String> issueQueries = AUTHORITY_QUERIES.getOrDefault(
            issue.getType(),
            AUTHORITY_QUERIES.get(LegalIssueType.OTHER)
        );
        
        return issueQueries;
    }
}
