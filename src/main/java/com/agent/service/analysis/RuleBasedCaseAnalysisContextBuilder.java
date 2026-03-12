package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based case analysis context builder.
 * 
 * Orchestrates issue extraction, fact extraction, and missing fact identification
 * to build a complete analysis context.
 */
@Service
public class RuleBasedCaseAnalysisContextBuilder implements CaseAnalysisContextBuilder {
    private static final Logger logger = LoggerFactory.getLogger(RuleBasedCaseAnalysisContextBuilder.class);
    
    private final CaseIssueExtractor issueExtractor;
    private final CaseFactExtractor factExtractor;
    
    public RuleBasedCaseAnalysisContextBuilder(
        CaseIssueExtractor issueExtractor,
        CaseFactExtractor factExtractor
    ) {
        this.issueExtractor = issueExtractor;
        this.factExtractor = factExtractor;
    }

    /**
     * Build case analysis context from pre-extracted issues and evidence.
     * 
     * Receives issues that have already been extracted by CaseIssueExtractor
     * to ensure consistency across the pipeline and avoid recomputation.
     * 
     * @param originalQuery The user's original case question
     * @param cleanedQuery The case question after noise removal
     * @param identifiedIssues Pre-extracted legal issues
     * @param evidenceChunks Retrieved evidence
     * @return Complete CaseAnalysisContext with issues, facts, and missing facts
     */
    @Override
    public CaseAnalysisContext buildContext(
        String originalQuery,
        String cleanedQuery,
        List<CaseIssue> identifiedIssues,
        List<EvidenceChunk> evidenceChunks
    ) {
        logger.info("Building case analysis context for query: {}", originalQuery);
        logger.debug("Cleaned query: {}", cleanedQuery);
        logger.debug("Using pre-extracted issues: {} identified", identifiedIssues.size());
        
        // Step 1: Use pre-extracted issues (no recomputation)
        List<CaseIssue> issues = identifiedIssues;
        logger.info("Using {} pre-identified legal issues", issues.size());
        
        // Step 2: Extract facts from evidence
        List<CaseFact> extractedFacts = new ArrayList<>();
        for (CaseIssue issue : issues) {
            List<CaseFact> issueFacts = factExtractor.extractFacts(evidenceChunks, issue.getType());
            extractedFacts.addAll(issueFacts);
        }
        logger.info("Extracted {} case facts from evidence", extractedFacts.size());
        
        // Step 3: Identify missing facts for each issue type
        List<CaseFact> missingFacts = identifyMissingFacts(issues, extractedFacts);
        logger.info("Identified {} missing or needed facts", missingFacts.size());
        
        // Step 4: Combine facts (extracted + missing)
        List<CaseFact> allFacts = new ArrayList<>(extractedFacts);
        allFacts.addAll(missingFacts);
        
        // Step 5: Generate legal standard summary
        String standardSummary = generateLegalStandardSummary(issues);
        
        // Step 6: Build context with original query
        CaseAnalysisContext context = new CaseAnalysisContext(
            originalQuery,
            issues,
            allFacts,
            standardSummary
        );
        
        logger.info("Context built: {} issues, {} facts, summary length: {}",
            issues.size(), allFacts.size(), standardSummary.length());
        
        return context;
    }

    /**
     * Identify facts that are needed but may be missing from evidence.
     * 
     * Uses issue-specific heuristics to determine what facts are critical.
     * 
     * @param issues Identified legal issues
     * @param extractedFacts Facts already found in evidence
     * @return List of missing/needed CaseFact objects
     */
    private List<CaseFact> identifyMissingFacts(List<CaseIssue> issues, List<CaseFact> extractedFacts) {
        List<CaseFact> missingFacts = new ArrayList<>();
        String combinedFacts = extractedFacts.stream()
            .map(CaseFact::getDescription)
            .collect(Collectors.joining(" "))
            .toLowerCase();
        
        logger.info("Combined facts for missing-fact detection: '{}'", combinedFacts);

        for (CaseIssue issue : issues) {
            List<String> neededFacts = getNeededFactsForIssue(issue.getType());
            logger.info("Checking {} needed facts for issue type {}", neededFacts.size(), issue.getType());
            
            for (String neededFact : neededFacts) {
                // Check if we have this fact by keyword matching
                boolean available = hasKeywordMatch(combinedFacts, neededFact);
                logger.info("  Needed fact '{}' - available: {}", neededFact, available);
                
                if (!available) {
                    // Create placeholder missing fact
                    CaseFact missingFact = new CaseFact(
                        "[NEEDED FACT] " + neededFact,
                        false,  // Mark as not yet favorable
                        "[Missing from evidence]",
                        issue.getType()
                    );
                    missingFacts.add(missingFact);
                    logger.info("    ADDED MISSING FACT: {}", missingFact.getDescription());
                }
            }
        }
        
        logger.info("Identified {} missing facts total", missingFacts.size());
        return missingFacts;
    }

    /**
     * Check if combined facts contain keywords matching a needed fact.
     * 
     * @param combinedFacts All extracted facts combined as one string (lowercase)
     * @param neededFact The needed fact to check for
     * @return true if keywords match the needed fact
     */
    private boolean hasKeywordMatch(String combinedFacts, String neededFact) {
        String needle = neededFact.toLowerCase()
            .replace("presence of ", "")
            .replace("lack of ", "");
        
        // For stricter matching, we require that key concepts from the needed fact are present
        // Key concepts are typically the first 1-2 words of the needed fact
        String[] keywords = needle.split("\\s+");
        
        if (keywords.length == 0) {
            return false;
        }
        
        // Check if primary keyword (first word) is present
        String primaryKeyword = keywords[0];
        if (primaryKeyword.length() > 2 && combinedFacts.contains(primaryKeyword)) {
            // Found primary keyword - now check if supporting keywords exist
            // For facts about amounts, timelines, sources, etc., we need the specific concept
            long supportCount = Arrays.stream(keywords)
                .skip(1)  // Skip primary keyword
                .filter(kw -> kw.length() > 2 && combinedFacts.contains(kw))
                .count();
            
            // Need primary keyword + at least one supporting keyword (or just primary for very short facts)
            if (keywords.length <= 2) {
                return true;  // Single concept word
            } else {
                return supportCount >= 1;  // Multi-word facts need primary + support
            }
        }
        
        return false;
    }

    /**
     * Get list of needed facts for a specific issue type.
     * 
     * These represent critical information needed for case analysis.
     * 
     * @param issueType The legal issue type
     * @return List of needed fact descriptions
     */
    private List<String> getNeededFactsForIssue(LegalIssueType issueType) {
        return switch (issueType) {
            case REIMBURSEMENT -> List.of(
                "Exact payment amounts made post-separation",
                "Timeline of mortgage payments",
                "Source of funds for payments",
                "Timeline of occupancy by other spouse",
                "Whether other spouse benefited from improvements"
            );
            
            case SUPPORT -> List.of(
                "Income of supporting spouse",
                "Income of receiving spouse",
                "Number and ages of children",
                "Custody arrangements",
                "Duration of marriage"
            );
            
            case PROPERTY_CHARACTERIZATION -> List.of(
                "Date property purchased",
                "Title status at time of purchase",
                "Source of down payment",
                "Whether funds were commingled",
                "Contributions to payments post-acquisition"
            );
            
            case TRACING -> List.of(
                "Source of down payment documentation",
                "Evidence of separate property contribution",
                "Timeline of payments and sources",
                "Documentation of fund sources",
                "Bank statements or financial records"
            );
            
            case EXCLUSIVE_USE -> List.of(
                "Timeline of occupancy by each party",
                "Who occupied the family home",
                "Whether offset set-off is available",
                "Fair rental value of property",
                "Condition of property before/after occupancy"
            );
            
            case CUSTODY -> List.of(
                "Current parenting schedule and arrangement",
                "School and daycare information",
                "Special needs or healthcare considerations",
                "Each parent's work schedule",
                "Children's preferences (if age-appropriate)"
            );
            
            case RESTRAINING_ORDER -> List.of(
                "Specific alleged incidents or threats",
                "Documentation of abuse or harm",
                "Witness statements",
                "Police reports if applicable",
                "Prior protective orders"
            );
            
            default -> List.of(
                "Key factual assertions",
                "Timeline of events",
                "Parties involved and their roles"
            );
        };
    }

    /**
     * Generate a summary of applicable legal standards for identified issues.
     * 
     * @param issues The identified legal issues
     * @return Summary string of legal standards
     */
    private String generateLegalStandardSummary(List<CaseIssue> issues) {
        StringBuilder summary = new StringBuilder();
        summary.append("Applicable Legal Standards:\n");

        for (CaseIssue issue : issues) {
            summary.append("- ").append(issue.getType()).append(": ");
            summary.append(getLegalStandardForIssue(issue.getType())).append("\n");
        }

        return summary.toString();
    }

    /**
     * Get legal standard description for an issue type.
     * 
     * Placeholder for V1 - in V2 would reference actual case law.
     * 
     * @param issueType The legal issue type
     * @return Standard description
     */
    private String getLegalStandardForIssue(LegalIssueType issueType) {
        return switch (issueType) {
            case REIMBURSEMENT -> "Request for reimbursement evaluated under Epstein factors; compare benefit received against payments made";
            case SUPPORT -> "Child support calculated per guideline; spousal support varies by jurisdiction and length of marriage";
            case PROPERTY_CHARACTERIZATION -> "Community property presumption applies to marriage earnings; can be overcome with tracing";
            case TRACING -> "Tracing burden on party claiming separate property; requires clear and convincing evidence";
            case EXCLUSIVE_USE -> "Exclusive use requires showing inability to occupy and lack of set-off availability";
            case CUSTODY -> "Best interests of child standard; factors include stability, parental fitness, and child preferences";
            case RESTRAINING_ORDER -> "Requires showing of abuse, threat, stalking, or credible threat; may be temporary or permanent";
            default -> "Legal analysis required for this issue type";
        };
    }
}
