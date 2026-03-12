package com.agent.service.analysis.authority;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.authority.AuthorityMatch;
import com.agent.model.analysis.authority.AuthoritySummary;
import com.agent.model.analysis.authority.LegalAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for summarizing retrieved authorities into rule descriptions.
 * 
 * Takes authority matches and combines their summaries into a cohesive
 * rule description for each issue type.
 * 
 * V1 Implementation: Simple aggregation of top authorities and their summaries.
 */
@Service
public class AuthoritySummarizer {
    private static final Logger logger = LoggerFactory.getLogger(AuthoritySummarizer.class);

    /**
     * Summarize authorities for a given issue.
     * 
     * @param issue The case issue
     * @param authorityMatches All authority matches for this issue
     * @return AuthoritySummary containing rule description and top authorities
     */
    public AuthoritySummary summarize(CaseIssue issue, List<AuthorityMatch> authorityMatches) {
        logger.debug("AuthoritySummarizer: Summarizing authorities for issue type: {}", issue.getType());
        
        if (authorityMatches.isEmpty()) {
            logger.warn("AuthoritySummarizer: No authorities found for issue type: {}", issue.getType());
            return createEmptySummary(issue);
        }
        
        // Get top 3 authorities by match score
        List<LegalAuthority> topAuthorities = authorityMatches.stream()
            .sorted((m1, m2) -> Double.compare(m2.getMatchScore(), m1.getMatchScore()))
            .limit(3)
            .map(AuthorityMatch::getAuthority)
            .toList();
        
        logger.debug("AuthoritySummarizer: Selected {} top authorities", topAuthorities.size());
        
        // Generate summarized rule from top authorities
        String summarizedRule = generateRuleSummary(issue, topAuthorities);
        
        AuthoritySummary summary = new AuthoritySummary(
            issue.getType(),
            topAuthorities.size(),
            summarizedRule,
            topAuthorities
        );
        
        logger.info("AuthoritySummarizer: Generated rule summary for issue type: {}", issue.getType());
        
        return summary;
    }

    /**
     * Generate rule summary from authorities.
     * 
     * Simple V1 approach:
     * 1. Combine summaries of top authorities
     * 2. Create cohesive rule description
     * 3. Reference authorities by citation
     * 
     * @param issue The case issue
     * @param authorities Top authorities for this issue (max 3)
     * @return Rule summary string
     */
    private String generateRuleSummary(CaseIssue issue, List<LegalAuthority> authorities) {
        StringBuilder rule = new StringBuilder();
        
        switch (issue.getType()) {
            case REIMBURSEMENT -> {
                rule.append("A spouse who uses separate property after separation to pay community obligations ");
                rule.append("may seek reimbursement. ");
                if (!authorities.isEmpty()) {
                    rule.append(String.format("See %s", authorities.get(0).getCitation()));
                    if (authorities.size() > 1) {
                        rule.append(String.format(", %s", authorities.get(1).getCitation()));
                    }
                    rule.append(".");
                }
            }
            case SUPPORT -> {
                rule.append("Post-separation support obligations are determined by considering multiple statutory factors, ");
                rule.append("including the standard of living established during marriage, earning capacity, and non-remunerative service to family. ");
                if (!authorities.isEmpty()) {
                    rule.append(String.format("See %s", authorities.get(0).getCitation()));
                    rule.append(".");
                }
            }
            case EXCLUSIVE_USE -> {
                rule.append("A court may award exclusive use and occupancy of the family residence as part of property division, ");
                rule.append("with the value offset against other assets. ");
                if (!authorities.isEmpty()) {
                    rule.append(String.format("See %s", authorities.get(0).getCitation()));
                    rule.append(".");
                }
            }
            case PROPERTY_CHARACTERIZATION -> {
                rule.append("Community property consists of all property acquired during marriage except separate property. ");
                rule.append("Separate property must be clearly traced to its source. ");
                if (!authorities.isEmpty()) {
                    rule.append(String.format("See %s", authorities.get(0).getCitation()));
                    if (authorities.size() > 1) {
                        rule.append(String.format(", %s", authorities.get(1).getCitation()));
                    }
                    rule.append(".");
                }
            }
            case TRACING -> {
                rule.append("Separate property sources must be traced with sufficient clarity to overcome the presumption ");
                rule.append("that property acquired during marriage is community property. ");
                if (!authorities.isEmpty()) {
                    rule.append(String.format("See %s", authorities.get(0).getCitation()));
                    rule.append(".");
                }
            }
            case CUSTODY -> {
                rule.append("Custody determinations are made based on the best interest of the child, considering health, safety, welfare, ");
                rule.append("history of parental involvement, and the child's own preferences when appropriate. ");
                if (!authorities.isEmpty()) {
                    rule.append(String.format("See %s", authorities.get(0).getCitation()));
                    rule.append(".");
                }
            }
            case RESTRAINING_ORDER -> {
                rule.append("Protective orders require clear and convincing evidence of abuse or credible threat of abuse. ");
                rule.append("Temporary orders may be issued to provide immediate protection pending a full hearing. ");
                if (!authorities.isEmpty()) {
                    rule.append(String.format("See %s", authorities.get(0).getCitation()));
                    rule.append(".");
                }
            }
            case OTHER -> {
                rule.append("Legal principles applicable to this issue depend on specific facts and circumstances. ");
                if (!authorities.isEmpty()) {
                    rule.append("Relevant authorities include: ");
                    rule.append(authorities.stream()
                        .map(LegalAuthority::getCitation)
                        .collect(Collectors.joining(", ")));
                    rule.append(".");
                }
            }
        }
        
        return rule.toString();
    }

    /**
     * Create empty summary when no authorities found.
     * 
     * @param issue The case issue
     * @return Empty AuthoritySummary
     */
    private AuthoritySummary createEmptySummary(CaseIssue issue) {
        String defaultRule = "No authorities currently available for this issue type. Further research recommended.";
        
        return new AuthoritySummary(
            issue.getType(),
            0,
            defaultRule,
            new ArrayList<>()
        );
    }
}
