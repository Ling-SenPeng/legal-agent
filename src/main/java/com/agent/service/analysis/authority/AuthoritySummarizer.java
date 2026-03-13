package com.agent.service.analysis.authority;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.authority.AuthorityMatch;
import com.agent.model.analysis.authority.AuthoritySummary;
import com.agent.model.analysis.authority.AuthorityType;
import com.agent.model.analysis.authority.LegalAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
     * Strategy:
     * 1. Always preserve at least one STATUTE if available (high statutory authority)
     * 2. Preserve top CASE_LAW authorities by match score
     * 3. Limit total to 5 unique authorities by authorityId
     * 4. Maintain deterministic ordering by match score
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
        
        // Log raw input for debugging
        if (logger.isDebugEnabled()) {
            StringBuilder inputLog = new StringBuilder("\n[AUTHORITY_SUMMARIZER_INPUT] Issue: ");
            inputLog.append(issue.getType()).append(" (").append(authorityMatches.size()).append(" matches)\n");
            for (AuthorityMatch match : authorityMatches) {
                inputLog.append(String.format("  - %s | %s | %s | match_score=%.3f\n",
                    match.getAuthority().getAuthorityId(),
                    match.getAuthority().getTitle(),
                    match.getAuthority().getAuthorityType(),
                    match.getMatchScore()
                ));
            }
            logger.debug(inputLog.toString());
        }
        
        // Select top authorities preserving statute diversity
        List<LegalAuthority> selectedAuthorities = selectTopAuthoritiesPreservingTypes(authorityMatches);
        
        // Log selection results
        if (logger.isDebugEnabled()) {
            StringBuilder selectionLog = new StringBuilder("\n[AUTHORITY_SUMMARIZER_SELECTED] ");
            selectionLog.append("Keeping ").append(selectedAuthorities.size()).append(" authorities:\n");
            for (LegalAuthority auth : selectedAuthorities) {
                selectionLog.append(String.format("  - %s | %s | %s\n",
                    auth.getAuthorityId(),
                    auth.getTitle(),
                    auth.getAuthorityType()
                ));
            }
            
            // Log dropped authorities for visibility
            Set<String> selectedIds = new LinkedHashSet<>();
            selectedAuthorities.forEach(a -> selectedIds.add(a.getAuthorityId()));
            
            List<String> droppedIds = new ArrayList<>();
            for (AuthorityMatch match : authorityMatches) {
                if (!selectedIds.contains(match.getAuthority().getAuthorityId())) {
                    droppedIds.add(match.getAuthority().getAuthorityId());
                }
            }
            
            if (!droppedIds.isEmpty()) {
                selectionLog.append("Dropped ").append(droppedIds.size()).append(" authorities:\n");
                for (AuthorityMatch match : authorityMatches) {
                    if (droppedIds.contains(match.getAuthority().getAuthorityId())) {
                        selectionLog.append(String.format("  - %s | %s | %s (match_score=%.3f)\n",
                            match.getAuthority().getAuthorityId(),
                            match.getAuthority().getTitle(),
                            match.getAuthority().getAuthorityType(),
                            match.getMatchScore()
                        ));
                    }
                }
            }
            selectionLog.append("[AUTHORITY_SUMMARIZER_SELECTED_END]\n");
            logger.debug(selectionLog.toString());
        }
        
        // Generate summarized rule from selected authorities
        String summarizedRule = generateRuleSummary(issue, selectedAuthorities);
        
        AuthoritySummary summary = new AuthoritySummary(
            issue.getType(),
            selectedAuthorities.size(),
            summarizedRule,
            selectedAuthorities
        );
        
        logger.info("AuthoritySummarizer: Generated rule summary for issue type: {} with {} authorities",
            issue.getType(), selectedAuthorities.size());
        
        return summary;
    }
    
    /**
     * Select top authorities while preserving important authority types.
     * 
     * Strategy:
     * 1. If there are STATUTE authorities, keep the top-scoring one
     * 2. Fill remaining slots (up to 5 total) with next-best authorities by match score
     * 3. Ensure no duplicate authorityIds
     * 4. Maintain scoring order for determinism
     * 
     * @param authorityMatches All authority matches sorted by relevance
     * @return List of selected authorities (max 5, with at least 1 statute if available)
     */
    private List<LegalAuthority> selectTopAuthoritiesPreservingTypes(List<AuthorityMatch> authorityMatches) {
        final int MAX_AUTHORITIES = 5;
        List<LegalAuthority> selected = new ArrayList<>();
        Set<String> selectedIds = new LinkedHashSet<>();
        
        // Sort by match score (descending) for processing
        List<AuthorityMatch> sorted = authorityMatches.stream()
            .sorted((m1, m2) -> Double.compare(m2.getMatchScore(), m1.getMatchScore()))
            .toList();
        
        // First pass: find and reserve the top-scoring STATUTE if any exists
        LegalAuthority reserved_statute = null;
        for (AuthorityMatch match : sorted) {
            if (match.getAuthority().getAuthorityType() == AuthorityType.STATUTE) {
                reserved_statute = match.getAuthority();
                break;  // Take the first (highest-scoring) statute
            }
        }
        
        // Second pass: add authorities in score order, preserving the statute
        for (AuthorityMatch match : sorted) {
            if (selected.size() >= MAX_AUTHORITIES) {
                break;
            }
            
            LegalAuthority auth = match.getAuthority();
            
            // Skip if already selected
            if (selectedIds.contains(auth.getAuthorityId())) {
                continue;
            }
            
            // Always include the reserved statute, even if it means dropping a similar-scoring case law
            if (reserved_statute != null && auth.getAuthorityId().equals(reserved_statute.getAuthorityId())) {
                selected.add(auth);
                selectedIds.add(auth.getAuthorityId());
                reserved_statute = null;  // Mark as added
            } else if (reserved_statute == null || selected.size() < MAX_AUTHORITIES - 1) {
                // Add as long as we have room or haven't reserved a statute yet
                selected.add(auth);
                selectedIds.add(auth.getAuthorityId());
            }
        }
        
        // If statute was reserved but not yet added, add it now (shouldn't happen, but be safe)
        if (reserved_statute != null && !selectedIds.contains(reserved_statute.getAuthorityId())) {
            if (selected.size() < MAX_AUTHORITIES) {
                selected.add(reserved_statute);
                selectedIds.add(reserved_statute.getAuthorityId());
            }
        }
        
        return selected;
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
