package com.agent.service.analysis;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Rule-based case issue extractor using keyword heuristics.
 * 
 * Maps keywords and phrases found in case queries to legal issues.
 * Uses multiphrase patterns and prioritization for accurate detection.
 */
@Service
public class RuleBasedCaseIssueExtractor implements CaseIssueExtractor {
    private static final Logger logger = LoggerFactory.getLogger(RuleBasedCaseIssueExtractor.class);
    
    // Issue-specific keyword patterns
    private static final String[] REIMBURSEMENT_KEYWORDS = {
        "reimbursement", "epstein", "mortgage payment", "loan", "reimbursed",
        "post-separation", "post separation", "third party payment"
    };
    
    private static final String[] SUPPORT_KEYWORDS = {
        "support", "child support", "spousal support", "alimony", "maintenance",
        "family support", "temporary support"
    };
    
    private static final String[] PROPERTY_CHARACTERIZATION_KEYWORDS = {
        "characterization", "characterize", "community property", "separate property", 
        "character of property", "commingling", "transmutation"
    };
    
    private static final String[] TRACING_KEYWORDS = {
        "tracing", "down payment", "source of funds", "contribution",
        "trace funds", "source tracing", "fund source"
    };
    
    private static final String[] EXCLUSIVE_USE_KEYWORDS = {
        "exclusive use", "occupancy", "exclusive occupancy", "family home",
        "use and occupancy"
    };
    
    private static final String[] CUSTODY_KEYWORDS = {
        "custody", "visitation", "parenting", "care and control", "guardianship",
        "child visitation", "custody arrangement"
    };
    
    private static final String[] RESTRAINING_ORDER_KEYWORDS = {
        "tro", "restraining order", "dvro", "protective order",
        "domestic violence", "order of protection", "cease and desist"
    };

    private static final Map<String[], LegalIssueType> ISSUE_PATTERNS = Map.ofEntries(
        Map.entry(REIMBURSEMENT_KEYWORDS, LegalIssueType.REIMBURSEMENT),
        Map.entry(SUPPORT_KEYWORDS, LegalIssueType.SUPPORT),
        Map.entry(PROPERTY_CHARACTERIZATION_KEYWORDS, LegalIssueType.PROPERTY_CHARACTERIZATION),
        Map.entry(TRACING_KEYWORDS, LegalIssueType.TRACING),
        Map.entry(EXCLUSIVE_USE_KEYWORDS, LegalIssueType.EXCLUSIVE_USE),
        Map.entry(CUSTODY_KEYWORDS, LegalIssueType.CUSTODY),
        Map.entry(RESTRAINING_ORDER_KEYWORDS, LegalIssueType.RESTRAINING_ORDER)
    );

    /**
     * Extract issues from case query.
     * 
     * @param caseQuery The user's case question
     * @return List of identified issues
     */
    @Override
    public List<CaseIssue> extractIssues(String caseQuery) {
        return extractIssues(caseQuery, null);
    }

    /**
     * Extract issues from case query with optional context.
     * 
     * @param caseQuery The case question
     * @param context Additional context (ignored in rule-based version)
     * @return List of identified issues
     */
    @Override
    public List<CaseIssue> extractIssues(String caseQuery, String context) {
        if (caseQuery == null || caseQuery.trim().isEmpty()) {
            logger.warn("Empty case query provided");
            return List.of();
        }

        String lowerQuery = caseQuery.toLowerCase();
        List<CaseIssue> issues = new ArrayList<>();
        Set<LegalIssueType> detectedTypes = new HashSet<>();

        // Match each pattern against the query
        for (Map.Entry<String[], LegalIssueType> entry : ISSUE_PATTERNS.entrySet()) {
            String[] keywords = entry.getKey();
            LegalIssueType issueType = entry.getValue();

            MatchResult result = matchKeywords(lowerQuery, keywords);
            
            if (result.count > 0 && !detectedTypes.contains(issueType)) {
                double confidence = Math.min(0.95, 0.6 + result.count * 0.15);
                
                CaseIssue issue = new CaseIssue(
                    issueType,
                    getIssueDescription(issueType),
                    confidence,
                    result.keywords
                );
                
                issues.add(issue);
                detectedTypes.add(issueType);
                
                logger.debug("Detected issue: {} with confidence {}", issueType, 
                    String.format("%.2f", confidence));
            }
        }

        // If no specific issues matched, return generic OTHER issue
        if (issues.isEmpty()) {
            logger.debug("No specific issues detected, returning generic OTHER");
            CaseIssue genericIssue = new CaseIssue(
                LegalIssueType.OTHER,
                "Generic legal issue requiring further analysis",
                0.5,
                "No specific pattern matched"
            );
            issues.add(genericIssue);
        }

        logger.info("Extracted {} issues from query", issues.size());
        return issues;
    }

    /**
     * Match keywords in query and return count and matched terms.
     * 
     * @param query Lowercased query string
     * @param keywords Keywords to search for
     * @return MatchResult with count and matched keyword list
     */
    private MatchResult matchKeywords(String query, String[] keywords) {
        int count = 0;
        StringBuilder matchedKeywords = new StringBuilder();

        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                count++;
                if (matchedKeywords.length() > 0) {
                    matchedKeywords.append(", ");
                }
                matchedKeywords.append("\"").append(keyword).append("\"");
            }
        }

        return new MatchResult(count, matchedKeywords.toString());
    }

    /**
     * Get human-readable description for an issue type.
     * 
     * @param issueType The LegalIssueType
     * @return Description string
     */
    private String getIssueDescription(LegalIssueType issueType) {
        return switch (issueType) {
            case REIMBURSEMENT -> "Request for reimbursement (e.g., post-separation mortgage, loans)";
            case SUPPORT -> "Spousal or child support obligation";
            case PROPERTY_CHARACTERIZATION -> "Characterization of community vs. separate property";
            case TRACING -> "Fund tracing and source of funds issues";
            case EXCLUSIVE_USE -> "Exclusive use and occupancy of property";
            case CUSTODY -> "Custody and visitation of minor children";
            case RESTRAINING_ORDER -> "Restraining order or protective order";
            case OTHER -> "Generic legal issue";
        };
    }

    /**
     * Inner class to hold keyword match results.
     */
    private static class MatchResult {
        final int count;
        final String keywords;

        MatchResult(int count, String keywords) {
            this.count = count;
            this.keywords = keywords;
        }
    }
}
