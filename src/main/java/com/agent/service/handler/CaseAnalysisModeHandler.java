package com.agent.service.handler;

import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.EvidenceChunk;
import com.agent.model.PaymentRecord;
import com.agent.model.CaseProfile;
import com.agent.model.analysis.*;
import com.agent.model.analysis.authority.AuthoritySummary;
import com.agent.model.analysis.authority.LegalAuthority;
import com.agent.model.analysis.authority.AuthorityType;
import com.agent.service.TaskModeHandler;
import com.agent.service.RetrievalService;
import com.agent.service.analysis.CaseAnalysisContextBuilder;
import com.agent.service.analysis.CaseAnalysisQueryCleaner;
import com.agent.service.analysis.CaseAnalysisRetrievalQueryBuilder;
import com.agent.service.analysis.CaseIssueExtractor;
import com.agent.service.analysis.authority.IssueAuthorityRetrievalStrategy;
import com.agent.service.analysis.authority.AuthorityRetrievalService;
import com.agent.service.analysis.authority.AuthoritySummarizer;
import com.agent.service.analysis.FactClassifier;
import com.agent.service.extraction.PaymentRecordExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * Handler for CASE_ANALYSIS mode.
 * 
 * Evaluates facts against legal principles to assess claim strength and predict outcomes.
 * 
 * V2 Pipeline (with Authority Retrieval):
 * 1. Extract legal issues from query
 * 2. Retrieve relevant case facts using existing retrieval flow
 * 3. Retrieve legal authorities (statutes, cases, practice guides) for each issue
 * 4. Summarize authorities into rule descriptions
 * 5. Convert evidence chunks into CaseFact objects
 * 6. Build comprehensive CaseAnalysisContext with facts and authorities
 * 7. Generate CaseAnalysisResult with strength assessment
 * 8. Format answer with analysis sections (including relevant authorities)
 * 9. Return ModeExecutionResult with metadata
 */
@Component
public class CaseAnalysisModeHandler implements TaskModeHandler {
    private static final Logger logger = LoggerFactory.getLogger(CaseAnalysisModeHandler.class);
    
    private final RetrievalService retrievalService;
    private final CaseAnalysisContextBuilder contextBuilder;
    private final CaseAnalysisQueryCleaner queryCleaner;
    private final CaseAnalysisRetrievalQueryBuilder queryBuilder;
    private final CaseIssueExtractor issueExtractor;
    private final IssueAuthorityRetrievalStrategy authorityQueryBuilder;
    private final AuthorityRetrievalService authorityRetrievalService;
    private final AuthoritySummarizer authoritySummarizer;
    private final FactClassifier factClassifier;
    private final PaymentRecordExtractor paymentRecordExtractor;
    
    // ThreadLocal to store evidence chunks during execution for access in nested methods
    private final ThreadLocal<List<EvidenceChunk>> currentEvidenceChunks = new ThreadLocal<>();
    
    // ThreadLocal to store CaseProfile (contains DOS and other case-level facts)
    private final ThreadLocal<CaseProfile> currentCaseProfile = new ThreadLocal<>();

    public CaseAnalysisModeHandler(
        RetrievalService retrievalService,
        CaseAnalysisContextBuilder contextBuilder,
        CaseAnalysisQueryCleaner queryCleaner,
        CaseAnalysisRetrievalQueryBuilder queryBuilder,
        CaseIssueExtractor issueExtractor,
        IssueAuthorityRetrievalStrategy authorityQueryBuilder,
        AuthorityRetrievalService authorityRetrievalService,
        AuthoritySummarizer authoritySummarizer,
        FactClassifier factClassifier,
        PaymentRecordExtractor paymentRecordExtractor
    ) {
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.queryCleaner = queryCleaner;
        this.queryBuilder = queryBuilder;
        this.issueExtractor = issueExtractor;
        this.authorityQueryBuilder = authorityQueryBuilder;
        this.authorityRetrievalService = authorityRetrievalService;
        this.authoritySummarizer = authoritySummarizer;
        this.factClassifier = factClassifier;
        this.paymentRecordExtractor = paymentRecordExtractor;
    }

    @Override
    public TaskMode getMode() {
        return TaskMode.CASE_ANALYSIS;
    }

    /**
     * Execute CASE_ANALYSIS query to evaluate legal position and predict outcomes.
     * 
     * Implements query preprocessing pipeline:
     * 1. Strip analysis framing noise from query
     * 2. Extract legal issues from cleaned query
     * 3. Build optimized retrieval queries using cleaned query + issues
     * 4. Retrieve evidence for each query and merge results
     * 5. Build analysis context with merged evidence
     * 6. Generate case analysis result with strength assessment
     * 7. Format and return answer
     * 
     * @param query The user's analytical question
     * @param topK Number of relevant facts/authorities to consider
     * @return ModeExecutionResult with analysis findings
     */
    @Override
    public ModeExecutionResult execute(String query, int topK) {
        logger.info("[CASE_ANALYSIS] Processing: {}", query);
        
        try {
            // ===== PREPROCESSING PHASE =====
            
            // Step 1: Strip analysis framing noise from query
            logger.debug("[CASE_ANALYSIS] Step 1: Stripping analysis noise");
            String cleanedQuery = queryCleaner.stripAnalysisNoise(query);
            logger.debug("[CASE_ANALYSIS] Original: '{}' → Cleaned: '{}'", query, cleanedQuery);
            
            // Validate that cleaning didn't remove all content
            if (!queryCleaner.hasSignificantContent(cleanedQuery)) {
                logger.warn("[CASE_ANALYSIS] Cleaned query has insufficient content, falling back to original");
                cleanedQuery = query;
            }
            
            // Step 2: Extract legal issues from cleaned query
            logger.debug("[CASE_ANALYSIS] Step 2: Extracting legal issues");
            List<CaseIssue> issues = issueExtractor.extractIssues(cleanedQuery);
            logger.info("[CASE_ANALYSIS] Identified {} legal issues", issues.size());
            
            // Step 3: Build optimized retrieval queries
            logger.debug("[CASE_ANALYSIS] Step 3: Building optimized retrieval queries");
            List<String> retrievalQueries = queryBuilder.buildQueries(cleanedQuery, issues);
            logger.info("[CASE_ANALYSIS] Generated {} retrieval queries", retrievalQueries.size());
            
            for (int i = 0; i < retrievalQueries.size(); i++) {
                logger.debug("[CASE_ANALYSIS]   Query {}: '{}'", i + 1, retrievalQueries.get(i));
            }
            
            // ===== RETRIEVAL PHASE =====
            
            // Step 4: Retrieve evidence for each query and merge results
            logger.debug("[CASE_ANALYSIS] Step 4: Retrieving evidence for {} queries", retrievalQueries.size());
            List<EvidenceChunk> mergedEvidenceChunks = retrieveAndMergeEvidence(retrievalQueries, topK);
            
            // Store evidence chunks for access in nested methods (e.g., for PaymentRecord extraction)
            currentEvidenceChunks.set(mergedEvidenceChunks);
            
            if (mergedEvidenceChunks.isEmpty()) {
                logger.warn("[CASE_ANALYSIS] No evidence found across all retrieval queries");
                return new ModeExecutionResult(
                    TaskMode.CASE_ANALYSIS,
                    "No relevant case facts found in the evidence collection. Unable to perform case analysis.",
                    "Facts: 0, Issues: 0"
                );
            }
            
            logger.info("[CASE_ANALYSIS] Retrieved and merged {} evidence chunks from {} queries",
                mergedEvidenceChunks.size(), retrievalQueries.size());
            
            // ===== ANALYSIS PHASE =====
            
            // Step 5: Retrieve and summarize legal authorities for each issue
            logger.debug("[CASE_ANALYSIS] Step 5: Retrieving and summarizing legal authorities");
            List<AuthoritySummary> authoritySummaries = retrieveAndSummarizeAuthorities(issues, 3);
            logger.info("[CASE_ANALYSIS] Retrieved authority summaries for {} issues", authoritySummaries.size());
            
            for (int i = 0; i < authoritySummaries.size(); i++) {
                logger.debug("[CASE_ANALYSIS]   Authority {} for {}: {} authorities",
                    i + 1,
                    authoritySummaries.get(i).getIssueType(),
                    authoritySummaries.get(i).getAuthorityCount());
            }
            
            // Step 6: Build analysis context (uses pre-extracted issues, evidence, and authorities)
            logger.debug("[CASE_ANALYSIS] Step 6: Building case analysis context");
            CaseAnalysisContext context = contextBuilder.buildContextWithAuthorities(
                query, 
                cleanedQuery,
                issues,
                mergedEvidenceChunks,
                authoritySummaries
            );
            
            logger.info("[CASE_ANALYSIS] Context built with {} issues, {} facts, {} authorities",
                context.getIdentifiedIssues().size(),
                context.getRelevantFacts().size(),
                context.getAuthoritySummaries().size());
            
            // Step 7: Generate case analysis result with strength assessment
            logger.debug("[CASE_ANALYSIS] Step 7: Generating analysis result");
            CaseAnalysisResult result = generateAnalysisResult(context);
            
            logger.info("[CASE_ANALYSIS] Analysis complete - Strength: {}, Confidence: {}",
                result.getStrengthLevel(),
                String.format("%.2f", result.getConfidenceScore()));
            
            // Step 8: Format answer with analysis sections
            String answer = formatAnalysisAnswer(query, context, result);
            
            // Build metadata
            String metadata = String.format(
                "Mode: CASE_ANALYSIS | Issues: %d | Facts: %d | Authorities: %d | Strength: %s | Confidence: %.2f%% | Query: %s",
                context.getIdentifiedIssues().size(),
                context.getRelevantFacts().size(),
                context.getAuthoritySummaries().size(),
                result.getStrengthLevel(),
                result.getConfidenceScore() * 100,
                query
            );
            
            // Return result
            return new ModeExecutionResult(
                TaskMode.CASE_ANALYSIS,
                answer,
                metadata
            );
            
        } catch (Exception e) {
            logger.error("[CASE_ANALYSIS] Error processing query: {}", query, e);
            return new ModeExecutionResult(
                TaskMode.CASE_ANALYSIS,
                "Error performing case analysis: " + e.getMessage()
            );
        } finally {
            // Clean up ThreadLocal to prevent memory leaks
            currentEvidenceChunks.remove();
            currentCaseProfile.remove();
        }
    }
    
    /**
     * Set the CaseProfile for the current case.
     * Includes case-level facts like Date of Separation for reimbursement analysis.
     * 
     * @param caseProfile The CaseProfile with DOS and other case facts
     */
    public void setCaseProfile(CaseProfile caseProfile) {
        if (caseProfile != null) {
            currentCaseProfile.set(caseProfile);
            if (caseProfile.hasSeparationDate()) {
                logger.info("[CASE_PROFILE] dos={}", caseProfile.getDateOfSeparation());
            }
        }
    }
    
    /**
     * Get the current CaseProfile.
     * 
     * @return CaseProfile or null if not set
     */
    public CaseProfile getCaseProfile() {
        return currentCaseProfile.get();
    }
    
    /**
     * Set the Date of Separation (DOS) for filtering reimbursement payment records.
     * Format: "MM/DD/YYYY" or "MM/DD/YY" (same as payment dates)
     * Only payments AFTER DOS are included in post-separation reimbursement evidence.
     * 
     * Creates a CaseProfile with the given DOS and sets it.
     * (Convenience method for backward compatibility)
     * 
     * @param dateOfSeparation DOS string in format MM/DD/YYYY or MM/DD/YY
     */
    public void setDateOfSeparation(String dateOfSeparation) {
        if (dateOfSeparation != null && !dateOfSeparation.isBlank()) {
            currentCaseProfile.set(new CaseProfile(dateOfSeparation));
            logger.info("[CASE_PROFILE] dos={}", dateOfSeparation);
        }
    }
    
    /**
     * Get the current Date of Separation from CaseProfile.
     * 
     * @return DOS string or null if not set
     */
    public String getDateOfSeparation() {
        CaseProfile profile = currentCaseProfile.get();
        return profile != null ? profile.getDateOfSeparation() : null;
    }

    /**
     * Retrieve and summarize legal authorities for identified issues.
     * 
     * For each issue:
     * 1. Generate authority retrieval queries
     * 2. Retrieve matching authorities
     * 3. Summarize authorities into rule descriptions
     * 4. Return AuthoritySummary for issue
     * 
     * @param issues Identified legal issues
     * @param topK Number of top authorities per issue
     * @return List of AuthoritySummary objects
     */
    private List<AuthoritySummary> retrieveAndSummarizeAuthorities(List<CaseIssue> issues, int topK) {
        List<AuthoritySummary> summaries = new ArrayList<>();
        
        for (CaseIssue issue : issues) {
            logger.debug("[CASE_ANALYSIS] Retrieving authorities for issue: {}", issue.getType());
            
            // Generate authority queries for this issue
            List<String> authorityQueries = authorityQueryBuilder.buildAuthorityQueries(issue);
            logger.debug("[CASE_ANALYSIS] Generated {} authority queries for issue: {}",
                authorityQueries.size(), issue.getType());
            
            // Retrieve matching authorities
            List<com.agent.model.analysis.authority.AuthorityMatch> authorityMatches = authorityRetrievalService.retrieveAuthorities(
                authorityQueries,
                issue.getType(),
                topK
            );
            
            // Log raw retrieved authorities with full details
            if (logger.isDebugEnabled() && !authorityMatches.isEmpty()) {
                StringBuilder rawAuthLog = new StringBuilder("\n[CASE_ANALYSIS_RAW_AUTHORITIES] Issue: ");
                rawAuthLog.append(issue.getType()).append("\n");
                for (com.agent.model.analysis.authority.AuthorityMatch match : authorityMatches) {
                    LegalAuthority auth = match.getAuthority();
                    rawAuthLog.append(String.format("  - %s | %s | %s | score=%.3f\n",
                        auth.getAuthorityId(),
                        auth.getTitle(),
                        auth.getAuthorityType(),
                        auth.getRelevanceScore()
                    ));
                }
                logger.debug(rawAuthLog.toString());
            }
            
            // Summarize authorities into rule description
            AuthoritySummary summary = authoritySummarizer.summarize(issue, authorityMatches);
            summaries.add(summary);
            
            logger.debug("[CASE_ANALYSIS] Authority summary for {}: {} authorities with {} match records",
                issue.getType(), summary.getAuthorityCount(), authorityMatches.size());
        }
        
        return summaries;
    }

    /**
     * Rank and score authorities based on relevance to the identified issue.
     * 
     * Uses issue type and keywords to score authorities more intelligently:
     * - For REIMBURSEMENT: checks for Epstein, reimbursement, post-separation keywords
     * - For TRACING: checks for tracing, contribution, fund source keywords
     * - Prefers CASE_LAW and STATUTE types
     * - Uses title/summary content matching and original relevance score
     * 
     * Returns top authorities sorted by calculated relevance score.
     * 
     * @param issue The legal issue being analyzed
     * @param authorities List of authorities to rank
     * @return Ranked list of authorities (best matches first)
     */
    private List<LegalAuthority> rankAuthoritiesForIssue(CaseIssue issue, List<LegalAuthority> authorities) {
        if (authorities.isEmpty()) {
            return authorities;
        }
        
        // Score each authority
        Map<LegalAuthority, Double> authorityScores = new LinkedHashMap<>();
        
        for (LegalAuthority auth : authorities) {
            double score = calculateAuthorityRelevanceScore(issue, auth);
            authorityScores.put(auth, score);
        }
        
        // Sort by score (descending) and return
        return authorityScores.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Calculate relevance score for an authority relative to a specific issue.
     * 
     * Scoring factors:
     * 1. Base score from relevanceScore (0.5x weight)
     * 2. Authority type bonus: Issue-aware bonus, higher for STATUTE on REIMBURSEMENT (0.25x weight)
     * 3. Issue-specific keyword matching in title/summary (0.25x weight)
     * 
     * @param issue The legal issue
     * @param authority The authority being scored
     * @return Relevance score (0.0 - 2.0+)
     */
    private double calculateAuthorityRelevanceScore(CaseIssue issue, LegalAuthority authority) {
        double baseScore = authority.getRelevanceScore() * 0.5;  // 0.0 - 0.5
        
        // Authority type bonus: Issue-aware, stronger boost for STATUTE on REIMBURSEMENT
        double typeBonus = 0.0;
        com.agent.model.analysis.authority.AuthorityType authType = authority.getAuthorityType();
        
        if (authType == com.agent.model.analysis.authority.AuthorityType.STATUTE) {
            // STATUTE gets strong boost, especially for REIMBURSEMENT (relevant statutes like Cal. Fam. Code § 750)
            if (issue.getType() == com.agent.model.analysis.LegalIssueType.REIMBURSEMENT) {
                typeBonus = 0.25;  // Prioritize statutes for reimbursement issues
            } else {
                typeBonus = 0.20;  // Still strong boost for other issue types
            }
        } else if (authType == com.agent.model.analysis.authority.AuthorityType.CASE_LAW) {
            typeBonus = 0.10;  // Case law gets moderate boost
        } else {
            typeBonus = 0.05;  // Practice guides and commentary get light boost
        }
        
        // Issue-specific keyword matching in title and summary
        double keywordScore = calculateIssueSpecificKeywordScore(issue, authority) * 0.25;
        
        return baseScore + typeBonus + keywordScore;
    }

    /**
     * Calculate keyword match score for specific issue type.
     * 
     * @param issue The legal issue
     * @param authority The authority being evaluated
     * @return Keyword match score (0.0 - 1.0)
     */
    private double calculateIssueSpecificKeywordScore(CaseIssue issue, LegalAuthority authority) {
        String titleLower = authority.getTitle().toLowerCase();
        String summaryLower = authority.getSummary().toLowerCase();
        String combined = titleLower + " " + summaryLower;
        
        switch (issue.getType()) {
            case REIMBURSEMENT:
                return scoreReimbursementAuthority(titleLower, summaryLower, combined);
            case TRACING:
                return scoreTracingAuthority(titleLower, summaryLower, combined);
            case PROPERTY_CHARACTERIZATION:
                return scorePropertyCharacterizationAuthority(titleLower, summaryLower, combined);
            case SUPPORT:
                return scoreSupportAuthority(titleLower, summaryLower, combined);
            default:
                return scoreGenericAuthority(titleLower, summaryLower, combined);
        }
    }

    /**
     * Score authority for REIMBURSEMENT issues.
     * Looks for: Epstein, reimbursement, post-separation payments, contribution tracing, Cal. Fam. Code § 750 topics
     */
    private double scoreReimbursementAuthority(String titleLower, String summaryLower, String combined) {
        double score = 0.0;
        
        // Strong match: Epstein (landmark reimbursement case)
        if (titleLower.contains("epstein")) {
            score += 0.8;
        } else if (summaryLower.contains("epstein")) {
            score += 0.5;
        }
        
        // Strong match: reimbursement keyword (directly relevant)
        if (titleLower.contains("reimbursement")) {
            score += 0.5;  // Increased from 0.4 - reimbursement is key issue
        } else if (summaryLower.contains("reimbursement")) {
            score += 0.3;  // Increased from 0.2
        }
        
        // Strong match: Cal. Fam. Code § 750 topics (statute about reimbursement)
        // Topics: reimbursement, post_separation_payments, community_debt, separate_property_contribution
        if (combined.contains("family code") || combined.contains("fam. code") || combined.contains("fam code")) {
            score += 0.4;  // Statute about family law reimbursement
        }
        if (combined.contains("post-separation") && (combined.contains("payment") || combined.contains("mortgage"))) {
            score += 0.35;  // Increased from 0.3
        } else if (combined.contains("post-separation")) {
            score += 0.2;  // Increased from 0.15
        }
        
        // Good match: contribution or tracing (reimbursement mechanics)
        if (combined.contains("contribution") || combined.contains("tracing")) {
            score += 0.25;  // Increased from 0.2
        }
        
        // Strong de-prioritization: generic property references without reimbursement keyword
        // (Moore, property characterization, etc. that don't mention reimbursement)
        if (titleLower.contains("moore") && !combined.contains("reimbursement")) {
            score -= 0.4;  // Increased penalty from 0.3
        }
        // De-prioritize "division" or "characterization" focused cases without post-sep/reimbursement mention
        if ((titleLower.contains("division") || titleLower.contains("characterization")) && 
            !combined.contains("reimbursement") && !combined.contains("post-separation")) {
            score -= 0.2;
        }
        
        return Math.max(0.0, Math.min(1.0, score));  // Clamp to 0.0-1.0
    }

    /**
     * Score authority for TRACING issues.
     * Looks for: tracing, fund source, contribution, down payment, separate property contribution
     */
    private double scoreTracingAuthority(String titleLower, String summaryLower, String combined) {
        double score = 0.0;
        
        if (combined.contains("tracing")) {
            score += 0.5;
        }
        if (combined.contains("fund") || combined.contains("down payment")) {
            score += 0.3;
        }
        if (combined.contains("contribution") || combined.contains("separate property")) {
            score += 0.2;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Score authority for PROPERTY_CHARACTERIZATION issues.
     * Looks for: characterization, community property, separate property, transmutation
     */
    private double scorePropertyCharacterizationAuthority(String titleLower, String summaryLower, String combined) {
        double score = 0.0;
        
        if (combined.contains("characterization")) {
            score += 0.4;
        }
        if (combined.contains("community property") || combined.contains("separate property")) {
            score += 0.3;
        }
        if (combined.contains("transmutation")) {
            score += 0.2;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Score authority for SUPPORT issues.
     * Looks for: support, alimony, maintenance, child support
     */
    private double scoreSupportAuthority(String titleLower, String summaryLower, String combined) {
        double score = 0.0;
        
        if (combined.contains("support") || combined.contains("alimony") || combined.contains("maintenance")) {
            score += 0.5;
        }
        if (combined.contains("child support")) {
            score += 0.3;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Generic scoring for other issue types - just check if authority words appear in combined text
     */
    private double scoreGenericAuthority(String titleLower, String summaryLower, String combined) {
        // For generic issues, return slight preference for case law
        return 0.2;
    }

    /**
     * Retrieve evidence for multiple queries and merge results.
     * 
     * Deduplicates chunks by ID and keeps the highest similarity score
     * for chunks that appear in multiple retrieval results.
     * 
     * @param retrievalQueries List of retrieval queries to execute
     * @param topK Number of results per query
     * @return Merged list of unique evidence chunks
     */
    private List<EvidenceChunk> retrieveAndMergeEvidence(List<String> retrievalQueries, int topK) {
        // Use map to deduplicate chunks by ID, keeping the highest similarity score
        Map<Long, EvidenceChunk> chunkMap = new HashMap<>();
        
        for (String retrievalQuery : retrievalQueries) {
            logger.debug("[CASE_ANALYSIS] Retrieving evidence for pre-planned query: '{}'", retrievalQuery);
            
            // Use retrieveEvidenceWithoutPlanning to bypass generic planner
            // CASE_ANALYSIS queries are already optimized and don't need re-planning
            List<EvidenceChunk> queryResults = retrievalService.retrieveEvidenceWithoutPlanning(retrievalQuery, topK);
            logger.debug("[CASE_ANALYSIS] Retrieved {} chunks from query", queryResults.size());
            
            for (EvidenceChunk chunk : queryResults) {
                Long chunkId = chunk.chunkId();
                
                if (chunkMap.containsKey(chunkId)) {
                    // Chunk already exists - keep version with higher similarity
                    EvidenceChunk existing = chunkMap.get(chunkId);
                    double existingSimilarity = existing.similarity() != null ? existing.similarity() : 0.0;
                    double newSimilarity = chunk.similarity() != null ? chunk.similarity() : 0.0;
                    
                    if (newSimilarity > existingSimilarity) {
                        logger.debug("[CASE_ANALYSIS] Updating chunk similarity: {} → {}",
                            String.format("%.2f", existingSimilarity), String.format("%.2f", newSimilarity));
                        chunkMap.put(chunkId, chunk);
                    }
                } else {
                    // New unique chunk
                    chunkMap.put(chunkId, chunk);
                }
            }
        }
        
        // Convert to list and sort deterministically by similarity score (descending), then by chunkId (ascending)
        List<EvidenceChunk> mergedChunks = new ArrayList<>(chunkMap.values());
        mergedChunks.sort((c1, c2) -> {
            double sim1 = c1.similarity() != null ? c1.similarity() : 0.0;
            double sim2 = c2.similarity() != null ? c2.similarity() : 0.0;
            int similarityResult = Double.compare(sim2, sim1); // descending order (highest score first)
            if (similarityResult != 0) return similarityResult;
            // Secondary sort by chunkId for full determinism
            return Long.compare(c1.chunkId(), c2.chunkId());
        });
        
        logger.debug("[CASE_ANALYSIS] Total unique chunks after merging: {} (sorted by similarity)",
            mergedChunks.size());
        
        return mergedChunks;
    }

    /**
     * Generate case analysis result with strength assessment.
     * 
     * @param context The case analysis context
     * @return CaseAnalysisResult with strength level and confidence
     */
    private CaseAnalysisResult generateAnalysisResult(CaseAnalysisContext context) {
        logger.debug("[CASE_ANALYSIS] Assessing claim strength");
        
        List<CaseIssue> issues = context.getIdentifiedIssues();
        List<CaseFact> facts = context.getRelevantFacts();
        List<MissingFact> missingFactsList = context.getMissingFacts();
        
        // Count facts by polarity (supporting vs adverse)
        long supportingFacts = facts.stream()
            .filter(CaseFact::isFavorable)
            .count();
        long adverseFacts = facts.stream()
            .filter(CaseFact::isAdverse)
            .count();
        long neutralFacts = facts.stream()
            .filter(f -> f.getPolarity() == com.agent.model.analysis.FactPolarity.NEUTRAL)
            .count();
        long unknownFacts = facts.stream()
            .filter(f -> f.getPolarity() == com.agent.model.analysis.FactPolarity.UNKNOWN)
            .count();
        long missingFacts = missingFactsList.size();
        
        logger.debug("[CASE_ANALYSIS] Fact analysis - Supporting: {}, Adverse: {}, Neutral: {}, Unknown: {}, Missing: {}",
            supportingFacts, adverseFacts, neutralFacts, unknownFacts, missingFacts);
        
        // Calculate confidence based on determined facts (exclude UNKNOWN)
        double determinedFacts = (double) (supportingFacts + adverseFacts + neutralFacts);
        double factAvailability = (determinedFacts + unknownFacts) > 0 ? 
            (determinedFacts / (determinedFacts + unknownFacts + missingFacts)) : 0.5;
        
        // Average issue confidence
        double avgIssueConfidence = issues.isEmpty() ? 0.5 :
            issues.stream()
                .mapToDouble(CaseIssue::getConfidence)
                .average()
                .orElse(0.5);
        
        // Overall confidence = combination of issue confidence and fact availability
        double confidence = (avgIssueConfidence * 0.6) + (factAvailability * 0.4);
        
        // Assess strength level
        CaseAnalysisResult.StrengthLevel strengthLevel = assessStrength(
            supportingFacts,
            adverseFacts,
            missingFacts,
            issues.size()
        );
        
        // Build analysis narrative
        String analysis = buildAnalysisNarrative(context, strengthLevel);
        
        // Build recommendations
        String recommendations = buildRecommendations(context, strengthLevel);
        
        return new CaseAnalysisResult(
            context,
            strengthLevel,
            confidence,
            analysis,
            recommendations
        );
    }

    /**
     * Assess strength level based on facts and issues.
     * 
     * @return StrengthLevel assessment
     */
    private CaseAnalysisResult.StrengthLevel assessStrength(
        long supporting,
        long adverse,
        long missing,
        int issueCount
    ) {
        if (issueCount == 0) {
            return CaseAnalysisResult.StrengthLevel.MODERATE;
        }
        
        long total = supporting + adverse;
        if (total == 0) {
            return CaseAnalysisResult.StrengthLevel.WEAK;
        }
        
        double supportingRatio = (double) supporting / total;
        double missingRatio = missing / (double) (total + missing);
        
        // Strong case: mostly supporting facts, few missing
        if (supportingRatio > 0.75 && missingRatio < 0.2) {
            return CaseAnalysisResult.StrengthLevel.VERY_STRONG;
        }
        if (supportingRatio > 0.65 && missingRatio < 0.3) {
            return CaseAnalysisResult.StrengthLevel.STRONG;
        }
        
        // Moderate case: mixed facts
        if (supportingRatio >= 0.4 && supportingRatio <= 0.65) {
            return CaseAnalysisResult.StrengthLevel.MODERATE;
        }
        
        // Weak case: mostly adverse or many missing
        if (supportingRatio < 0.4 || missingRatio > 0.5) {
            return CaseAnalysisResult.StrengthLevel.WEAK;
        }
        
        if (supportingRatio < 0.25) {
            return CaseAnalysisResult.StrengthLevel.VERY_WEAK;
        }
        
        return CaseAnalysisResult.StrengthLevel.MODERATE;
    }

    /**
     * Build detailed analysis narrative.
     * 
     * @param context The case analysis context
     * @param strengthLevel The assessed strength level
     * @return Analysis narrative string
     */
    private String buildAnalysisNarrative(CaseAnalysisContext context, CaseAnalysisResult.StrengthLevel strengthLevel) {
        StringBuilder narrative = new StringBuilder();
        
        // Use actual retrieved facts (not missing facts)
        List<CaseFact> extractedFacts = context.getRelevantFacts();
        List<MissingFact> missingFacts = context.getMissingFacts();
        
        long favorableCount = extractedFacts.stream().filter(CaseFact::isFavorable).count();
        long unfavorableCount = extractedFacts.stream().filter(f -> !f.isFavorable()).count();
        
        narrative.append("PRELIMINARY ANALYSIS (based on available evidence):\n\n");
        narrative.append(String.format(
            "- Identified %d legal issue(s) for assessment\n",
            context.getIdentifiedIssues().size()
        ));
        narrative.append(String.format(
            "- Retrieved %d supporting fact(s) from evidence\n",
            favorableCount
        ));
        narrative.append(String.format(
            "- Retrieved %d challenging fact(s) from evidence\n",
            unfavorableCount
        ));
        narrative.append(String.format(
            "- Identified %d missing fact(s) needed for complete analysis\n\n",
            missingFacts.size()
        ));
        
        narrative.append("Initial Assessment:\n");
        narrative.append(String.format(
            "Based on the limited evidence available, the claim position appears %s. " +
            "This assessment is preliminary and should be validated with complete evidence.\n",
            strengthLevel.toString().toLowerCase().replace("_", " ")
        ));
        
        return narrative.toString();
    }

    /**
     * Build recommendations for case.
     * 
     * @param context The case analysis context
     * @param strengthLevel The assessed strength level
     * @return Recommendations string
     */
    private String buildRecommendations(CaseAnalysisContext context, CaseAnalysisResult.StrengthLevel strengthLevel) {
        StringBuilder recommendations = new StringBuilder();
        
        // Use separate missing facts list
        List<MissingFact> missingFacts = context.getMissingFacts();
        
        recommendations.append("RECOMMENDED NEXT STEPS:\n\n");
        
        if (!missingFacts.isEmpty()) {
            recommendations.append("Critical missing information:\n");
            missingFacts.stream()
                .limit(3)
                .forEach(fact -> recommendations.append("- Obtain: ")
                    .append(fact.getDescription())
                    .append("\n"));
            recommendations.append("\n");
        }
        
        // Provide conservative strength-specific recommendations
        switch (strengthLevel) {
            case VERY_STRONG -> recommendations.append("Assessment: Strong evidentiary support observed. " +
                    "However, verify all facts and consult counsel before relying on this analysis.");
            case STRONG -> recommendations.append("Assessment: Favorable evidence noted. " +
                    "Recommend obtaining the missing facts above and consulting counsel for full evaluation.");
            case MODERATE -> recommendations.append("Assessment: Mixed evidence. " +
                    "Strongly recommend focused fact-gathering on the gaps identified above.");
            case WEAK, VERY_WEAK -> recommendations.append("Assessment: Significant challenges present. " +
                    "Additional evidence gathering and legal counsel consultation are essential before proceeding.");
        }
        
        return recommendations.toString();
    }

    /**
     * Format the complete analysis answer with sections.
     * 
     * @param query Original query
     * @param context Case analysis context
     * @param result Analysis result
     * @return Formatted answer string
     */
    private String formatAnalysisAnswer(String query, CaseAnalysisContext context, CaseAnalysisResult result) {
        StringBuilder answer = new StringBuilder();
        
        answer.append("=== CASE ANALYSIS REPORT ===\n\n");
        
        answer.append("ISSUE SUMMARY\n");
        answer.append("---\n");
        if (context.getIdentifiedIssues().isEmpty()) {
            answer.append("No legal issues detected in the query.\n\n");
        } else {
            for (CaseIssue issue : context.getIdentifiedIssues()) {
                answer.append(String.format("- %s: %s (Confidence: %.0f%%)\n",
                    issue.getType(),
                    issue.getDescription(),
                    issue.getConfidence() * 100));
            }
            answer.append("\n");
        }
        
        // CHANGED: Get final rendered authorities BEFORE generating LEGAL RULE
        // This ensures LEGAL RULE only references authorities that actually appear
        if (logger.isDebugEnabled()) {
            logger.debug("\n========== CASE_ANALYSIS LEGAL RULE GENERATION START ==========");
        }
        List<LegalAuthority> finalRenderedAuthorities = extractAndRankFinalAuthorities(context);
        
        // NEW: LEGAL RULE section integrated from final rendered authorities only
        answer.append("LEGAL RULE\n");
        answer.append("---\n");
        appendLegalRuleFromFinalAuthorities(answer, context, finalRenderedAuthorities);
        answer.append("\n");
        
        if (logger.isDebugEnabled()) {
            logger.debug("========== CASE_ANALYSIS LEGAL RULE GENERATION END ==========\n");
        }
        
        // Collect all unique authorities for deduplication across sections
        Set<String> renderedAuthorityIds = new LinkedHashSet<>();
        
        // RELEVANT AUTHORITIES section with final authorities
        answer.append("RELEVANT AUTHORITIES\n");
        answer.append("---\n");
        appendRelevantAuthoritiesSectionWithFinal(answer, finalRenderedAuthorities, renderedAuthorityIds);
        
        answer.append("APPLICATION TO RULE\n");
        answer.append("---\n");
        appendApplicationToRuleSection(answer, context);
        
        answer.append("COUNTERARGUMENTS\n");
        answer.append("---\n");
        answer.append(String.format(
            "The opposing party could argue: %s\n\n",
            counterclaim(context, result.getStrengthLevel())
        ));
        
        answer.append("MISSING EVIDENCE\n");
        answer.append("---\n");
        List<MissingFact> missingFacts = context.getMissingFacts();
        if (missingFacts.isEmpty()) {
            answer.append("All critical facts appear to be documented.\n\n");
        } else {
            for (MissingFact fact : missingFacts) {
                answer.append("- ").append(fact.getDescription()).append("\n");
            }
            answer.append("\n");
        }
        
        answer.append("TENTATIVE CONCLUSION\n");
        answer.append("---\n");
        answer.append(String.format(
            "Claim Strength: %s\n",
            result.getStrengthLevel().toString().replace("_", " ")
        ));
        answer.append(String.format(
            "Confidence: %.0f%%\n\n",
            result.getConfidenceScore() * 100
        ));
        answer.append(result.getRecommendations()).append("\n\n");
        
        answer.append("*** PRELIMINARY ANALYSIS ONLY ***\n");
        answer.append("This analysis is based on available evidence and is subject to change ");
        answer.append("as additional facts emerge or legal standards are applied. ");
        answer.append("Consult with legal counsel for definitive advice.\n");
        
        return answer.toString();
    }

    /**
     * Extract, rank, and select the final top 2 authorities to be rendered.
     * This ensures the LEGAL RULE section only references authorities that
     * actually appear in the RELEVANT AUTHORITIES section.
     * 
     * @param context Case analysis context containing authority summaries
     * @return List of top 2 authorities after ranking
     */
    private List<LegalAuthority> extractAndRankFinalAuthorities(CaseAnalysisContext context) {
        // Collect all unique authorities from all summaries
        Map<String, LegalAuthority> authorityMap = new LinkedHashMap<>();
        
        if (logger.isDebugEnabled()) {
            logger.debug("[CASE_ANALYSIS_RULE_GEN_START] Extracting authorities for LEGAL RULE generation");
        }
        
        for (AuthoritySummary summary : context.getAuthoritySummaries()) {
            for (LegalAuthority auth : summary.getAuthorities()) {
                // Deduplicate by authorityId
                if (!authorityMap.containsKey(auth.getAuthorityId())) {
                    authorityMap.put(auth.getAuthorityId(), auth);
                }
            }
        }
        
        List<LegalAuthority> uniqueAuthorities = new ArrayList<>(authorityMap.values());
        
        // Log all extracted authorities before ranking
        if (logger.isDebugEnabled() && !uniqueAuthorities.isEmpty()) {
            StringBuilder extractedLog = new StringBuilder("\nExtracted unique authorities from summaries (" + uniqueAuthorities.size() + " total):\n");
            for (LegalAuthority auth : uniqueAuthorities) {
                extractedLog.append(String.format("  - %s | %s | %s | score=%.3f\n",
                    auth.getAuthorityId(),
                    auth.getTitle(),
                    auth.getCitation(),
                    auth.getRelevanceScore()
                ));
            }
            logger.debug(extractedLog.toString());
        }
        
        // Re-rank authorities by relevance to the identified issues
        if (!uniqueAuthorities.isEmpty() && !context.getIdentifiedIssues().isEmpty()) {
            CaseIssue primaryIssue = context.getIdentifiedIssues().get(0);
            uniqueAuthorities = rankAuthoritiesForIssue(primaryIssue, uniqueAuthorities);
            
            // Log ranked authorities
            if (logger.isDebugEnabled()) {
                StringBuilder rankedLog = new StringBuilder("\nRe-ranked authorities for LEGAL RULE generation (issue=" + primaryIssue.getType() + "):\n");
                for (int i = 0; i < uniqueAuthorities.size(); i++) {
                    LegalAuthority auth = uniqueAuthorities.get(i);
                    double calcScore = calculateAuthorityRelevanceScore(primaryIssue, auth);
                    rankedLog.append(String.format("  [%d] %s | %s | %s | rankScore=%.3f\n",
                        (i + 1),
                        auth.getAuthorityId(),
                        auth.getTitle(),
                        auth.getCitation(),
                        calcScore
                    ));
                }
                logger.debug(rankedLog.toString());
            }
        } else {
            // Fallback: sort by citation if no issues
            uniqueAuthorities.sort((a, b) -> a.getCitation().compareTo(b.getCitation()));
        }
        
        // Return top 2 (or as many as available if less than 2)
        List<LegalAuthority> finalList = uniqueAuthorities.stream()
            .limit(2)
            .toList();
        
        // Log final authorities selected for LEGAL RULE
        if (logger.isDebugEnabled()) {
            StringBuilder finalLog = new StringBuilder("\nFinal authorities selected for LEGAL RULE (" + finalList.size() + " authorities):\n");
            for (LegalAuthority auth : finalList) {
                finalLog.append(String.format("  - %s | %s | %s\n",
                    auth.getAuthorityId(),
                    auth.getTitle(),
                    auth.getCitation()
                ));
            }
            logger.debug(finalLog.toString());
        }
        
        return finalList;
    }

    /**
     * Generate LEGAL RULE text from the final rendered authorities.
     * Creates rule summary based only on authorities that actually appear in output.
     * 
     * @param answer StringBuilder to append to
     * @param context Case analysis context
     * @param finalAuthorities Final authorities selected for rendering
     */
    private void appendLegalRuleFromFinalAuthorities(
        StringBuilder answer,
        CaseAnalysisContext context,
        List<LegalAuthority> finalAuthorities
    ) {
        if (logger.isDebugEnabled()) {
            logger.debug("\n[CASE_ANALYSIS_RULE_GEN] Building LEGAL RULE from final authorities");
        }
        
        if (finalAuthorities.isEmpty()) {
            answer.append("No specific legal authorities retrieved for analysis.\n");
            if (logger.isDebugEnabled()) {
                logger.debug("[CASE_ANALYSIS_RULE_GEN] No final authorities available");
            }
        } else {
            // Build rule text from final authorities only
            StringBuilder ruleText = new StringBuilder();
            
            // Collect citations of final authorities
            List<String> citations = finalAuthorities.stream()
                .map(auth -> String.format("%s (%s)", auth.getTitle(), auth.getCitation()))
                .toList();
            
            // Get the primary issue for context
            CaseIssue primaryIssue = context.getIdentifiedIssues().isEmpty() 
                ? null 
                : context.getIdentifiedIssues().get(0);
            
            if (primaryIssue != null) {
                // Find matching authority summary for this issue
                for (AuthoritySummary summary : context.getAuthoritySummaries()) {
                    if (summary.getIssueType() == primaryIssue.getType()) {
                        // Use the summarized rule but ensure it only references final authorities
                        String originalRule = summary.getSummarizedRule();
                        
                        if (logger.isDebugEnabled()) {
                            logger.debug(String.format(
                                "\nAuthoritySummary for issue %s found.\nOriginal rule text: %s",
                                primaryIssue.getType(),
                                originalRule
                            ));
                        }
                        
                        // Filter the rule to only reference final authorities
                        String filteredRule = filterRuleToFinalAuthorities(originalRule, finalAuthorities, primaryIssue.getType());
                        ruleText.append(filteredRule);
                        break;
                    }
                }
            }
            
            // If no matching summary or rule is empty, create a simple rule from authority titles
            if (ruleText.length() == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("\n[CASE_ANALYSIS_RULE_GEN] No matching summary or rule is empty. Generating generic rule.");
                }
                ruleText.append("Based on ");
                for (int i = 0; i < citations.size(); i++) {
                    if (i > 0) ruleText.append(" and ");
                    ruleText.append(citations.get(i));
                }
                ruleText.append(", legal principles apply to this issue.\n");
            }
            
            if (logger.isDebugEnabled()) {
                logger.debug(String.format(
                    "\n[CASE_ANALYSIS_RULE_GEN_FINAL]\nFinal LEGAL RULE generated:\n%s\n[CASE_ANALYSIS_RULE_GEN_END]",
                    ruleText.toString()
                ));
            }
            
            answer.append(ruleText.toString());
        }
    }

    /**
     * Filter rule text to only mention authorities that are in the final rendered list.
     * STRICT: Rule must mention ONLY final authorities, not contain stale citations.
     * 
     * @param originalRule Original rule text from authority summary
     * @param finalAuthorities Final authorities to keep references to
     * @param issueType Issue type for generating concrete fallback rules
     * @return Filtered rule text, or regenerated rule if stale citations found
     */
    private String filterRuleToFinalAuthorities(String originalRule, List<LegalAuthority> finalAuthorities, LegalIssueType issueType) {
        if (finalAuthorities.isEmpty() || originalRule == null) {
            return originalRule;
        }
        
        // Collect citations of final authorities for comparison
        Set<String> finalCitations = new HashSet<>();
        Set<String> finalCitationsLower = new HashSet<>();
        
        for (LegalAuthority auth : finalAuthorities) {
            finalCitations.add(auth.getCitation());
            finalCitationsLower.add(auth.getCitation().toLowerCase());
        }
        
        if (logger.isDebugEnabled()) {
            StringBuilder filterLog = new StringBuilder("\n[CASE_ANALYSIS_RULE_FILTER] Strict citation filtering.\n");
            filterLog.append("Final authority citations (comparison set):\n");
            for (String citation : finalCitations) {
                filterLog.append("  - ").append(citation).append("\n");
            }
            logger.debug(filterLog.toString());
        }
        
        // STRICT: Extract all citations mentioned in the rule text
        Set<String> citedInRule = extractCitationsFromRuleText(originalRule);
        
        if (logger.isDebugEnabled()) {
            StringBuilder citedLog = new StringBuilder("Citations mentioned in original rule:\n");
            if (citedInRule.isEmpty()) {
                citedLog.append("  (none found)\n");
            } else {
                for (String cited : citedInRule) {
                    citedLog.append("  - ").append(cited).append("\n");
                }
            }
            logger.debug(citedLog.toString());
        }
        
        // STRICT: Check if rule contains any citations NOT in final authority set
        Set<String> staleCitations = new HashSet<>();
        for (String ruleCitation : citedInRule) {
            boolean foundInFinal = finalCitationsLower.stream()
                .anyMatch(finalCit -> normalizeForComparison(finalCit)
                    .contains(normalizeForComparison(ruleCitation)));
            
            if (!foundInFinal) {
                staleCitations.add(ruleCitation);
            }
        }
        
        if (logger.isDebugEnabled()) {
            StringBuilder staleLog = new StringBuilder("Stale citations found (in rule but NOT in final authorities):\n");
            if (staleCitations.isEmpty()) {
                staleLog.append("  (none - rule is clean)\n");
            } else {
                for (String stale : staleCitations) {
                    staleLog.append("  - ").append(stale).append("\n");
                }
            }
            logger.debug(staleLog.toString());
        }
        
        // Decision: If stale citations found, MUST regenerate
        if (!staleCitations.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("[CASE_ANALYSIS_RULE_FILTER] Decision: REGENERATE - Rule contains stale citations " + staleCitations);
            }
            return createRuleFromFinalAuthorities(finalAuthorities, issueType);
        }
        
        // If rule mentions at least one final authority (by citation OR by name) and has no stale citations, reuse it
        String ruleLower = originalRule.toLowerCase();
        boolean mentionsAtLeastOneFinalAuthority = citedInRule.stream()
            .anyMatch(ruleCit -> finalCitationsLower.stream()
                .anyMatch(finalCit -> normalizeForComparison(finalCit)
                    .contains(normalizeForComparison(ruleCit))))
            || // Also check if rule mentions any authority by NAME (e.g., "Epstein", "Garcia")
            finalAuthorities.stream()
                .anyMatch(auth -> ruleLower.contains(auth.getTitle().toLowerCase().split("\\(")[0].trim()));
        
        if (mentionsAtLeastOneFinalAuthority) {
            if (logger.isDebugEnabled()) {
                logger.debug("[CASE_ANALYSIS_RULE_FILTER] Decision: REUSE - Rule mentions final authorities (by citation or name) and has no stale citations.");
            }
            return originalRule;
        } else {
            // Rule doesn't mention any final authority at all
            if (logger.isDebugEnabled()) {
                logger.debug("[CASE_ANALYSIS_RULE_FILTER] Decision: REGENERATE - Rule mentions no final authorities.");
            }
            return createRuleFromFinalAuthorities(finalAuthorities, issueType);
        }
    }
    
    /**
     * Extract all citations mentioned in rule text.
     * Looks for patterns like: "191 Cal.App.3d 592", "28 Cal.4th 366", "Cal. Fam. Code § 750", etc.
     * 
     * California Case Citation Format: volume + reporter + page
     * Examples: "191 Cal.App.3d 592", "28 Cal.4th 366", "24 Cal.3d 76"
     * 
     * California Code Citation Format: jurisdiction + code name + section
     * Examples: "Cal. Fam. Code § 750", "Cal. Fam. Code §2640"
     * 
     * @param ruleText Text to extract citations from
     * @return Set of citation strings found in text
     */
    private Set<String> extractCitationsFromRuleText(String ruleText) {
        Set<String> citations = new HashSet<>();
        
        if (ruleText == null || ruleText.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("[CITATION_EXTRACT] Rule text is null or empty");
            }
            return citations;
        }
        
        // Pattern 1: California case citations
        // Format: volume + Cal[.reporter.series] + page
        // Examples: "191 Cal.App.3d 592", "28 Cal.4th 366", "24 Cal.3d 76", "33 Cal.App.4th 277"
        // Simpler pattern: \d+ (volume) + Cal\S* (reporter like Cal, Cal.App.3d, Cal.2d) + \d+ (page)
        java.util.regex.Pattern casePattern = java.util.regex.Pattern
            .compile("\\b(\\d+\\s+Cal\\S*\\s+\\d+)\\b");
        
        java.util.regex.Matcher caseMatcher = casePattern.matcher(ruleText);
        while (caseMatcher.find()) {
            String match = caseMatcher.group(1).trim();
            citations.add(match);
            if (logger.isDebugEnabled()) {
                logger.debug("[CITATION_EXTRACT] Found case citation: {}", match);
            }
        }
        
        // Pattern 2: California code sections
        // Format: Cal[ifornia] [Fam.] Code § [section number]
        // Examples: "Cal. Fam. Code § 750", "Cal. Fam. Code §2640", "Cal. Code § 1234"
        java.util.regex.Pattern codePattern = java.util.regex.Pattern
            .compile("\\b(Cal(?:ifornia)?(?:\\.|\\s)\\s*(?:Family|Fam\\.?)?\\s*Code\\s+(?:§|sections?)?\\s*\\d+)\\b");
        
        java.util.regex.Matcher codeMatcher = codePattern.matcher(ruleText);
        while (codeMatcher.find()) {
            String match = codeMatcher.group(1).trim();
            citations.add(match);
            if (logger.isDebugEnabled()) {
                logger.debug("[CITATION_EXTRACT] Found code citation: {}", match);
            }
        }
        
        if (logger.isDebugEnabled()) {
            if (citations.isEmpty()) {
                logger.debug("[CITATION_EXTRACT] No citations found in rule text");
            } else {
                logger.debug("[CITATION_EXTRACT] Extracted {} citations total", citations.size());
            }
        }
        
        return citations;
    }
    
    /**
     * Find stale citations in rule text (citations not in final authorities).
     * Looks for patterns like: "191 Cal.App.3d 592", "28 Cal.4th 366", "Cal. Fam. Code § 750", etc.
     * 
     * @param ruleText Text to extract citations from
     * @param finalCitationsNormalized Set of normalized final authority citations
     * @return Set of stale citation strings found in text but not in final authorities
     */
    private Set<String> findStaleCitationsInRule(String ruleText, Set<String> finalCitationsNormalized) {
        Set<String> staleCitations = new HashSet<>();
        
        if (ruleText == null || ruleText.isEmpty() || finalCitationsNormalized.isEmpty()) {
            return staleCitations;
        }
        
        // Simple pattern: Look for "Cal.*" or "California" followed by citation-like text
        // Pattern: digits + Cal + digits (e.g., "191 Cal.App.3d 592", "28 Cal.4th 366")
        java.util.regex.Pattern reporterPattern = java.util.regex.Pattern
            .compile("(\\d+\\s+Cal[\\w.]*\\s+\\d+\\s+\\d+)");
        
        java.util.regex.Matcher reporterMatcher = reporterPattern.matcher(ruleText);
        while (reporterMatcher.find()) {
            String citation = reporterMatcher.group(1).trim();
            String normalized = normalizeForComparison(citation);
            
            // Check if this citation is in the final authorities (with fuzzy matching)
            boolean foundInFinal = finalCitationsNormalized.stream()
                .anyMatch(finalNorm -> finalNorm.contains(normalized) || normalized.contains(finalNorm) || 
                    ruleText.toLowerCase().contains(normalizeForComparison(citation)));
            
            if (!foundInFinal) {
                staleCitations.add(citation);
            }
        }
        
        // Pattern: "Cal. Fam. Code § [digits]" or "Cal. Code § [digits]"
        java.util.regex.Pattern codePattern = java.util.regex.Pattern
            .compile("(Cal[\\w.]*\\s+Code\\s+§\\s+\\d+)");
        
        java.util.regex.Matcher codeMatcher = codePattern.matcher(ruleText);
        while (codeMatcher.find()) {
            String citation = codeMatcher.group(1).trim();
            String normalized = normalizeForComparison(citation);
            
            boolean foundInFinal = finalCitationsNormalized.stream()
                .anyMatch(finalNorm -> finalNorm.contains(normalized) || normalized.contains(finalNorm));
            
            if (!foundInFinal) {
                staleCitations.add(citation);
            }
        }
        
        return staleCitations;
    }
    
    /**
     * Normalize citation for comparison by removing spaces and periods.
     * E.g., "Cal. Fam. Code § 750" → "calfamcode750"
     * 
     * @param citation Citation to normalize
     * @return Normalized citation string
     */
    private String normalizeForComparison(String citation) {
        if (citation == null) {
            return "";
        }
        return citation.toLowerCase()
            .replaceAll("\\s+", "")      // Remove spaces
            .replaceAll("\\.", "")        // Remove periods  
            .replaceAll("§", "");         // Remove section symbol
    }

    /**
     * Create a concrete rule text from the final authorities.
     * Used when the original rule doesn't match the final rendered authorities.
     * Generates deterministic, citation-specific rules based on issue type and authority names.
     * 
     * @param finalAuthorities Final authorities to base rule on
     * @param issueType Issue type for context-specific rule generation
     * @return Generated concrete rule text
     */
    private String createRuleFromFinalAuthorities(List<LegalAuthority> finalAuthorities, LegalIssueType issueType) {
        if (finalAuthorities.isEmpty()) {
            return "No specific legal rule available.\n";
        }
        
        if (logger.isDebugEnabled()) {
            StringBuilder fallbackLog = new StringBuilder("\n[CASE_ANALYSIS_RULE_FALLBACK] Creating concrete fallback rule from final authorities:\n");
            for (LegalAuthority auth : finalAuthorities) {
                fallbackLog.append(String.format("  - %s | %s | %s\n",
                    auth.getAuthorityId(),
                    auth.getTitle(),
                    auth.getCitation()
                ));
            }
            logger.debug(fallbackLog.toString());
        }
        
        // Build a concrete rule based on issue type and recognized authority names
        String rule = generateConcreteRule(finalAuthorities, issueType);
        
        if (logger.isDebugEnabled()) {
            logger.debug("[CASE_ANALYSIS_RULE_FALLBACK] Generated rule: " + rule);
        }
        
        return rule + "\n";
    }
    
    /**
     * Generate a concrete rule text based on issue type and authority names.
     * Uses templates for recognized authorities and falls back to citation-aware generic.
     * 
     * @param finalAuthorities Final authorities
     * @param issueType Issue type for rule template selection
     * @return Concrete rule text
     */
    private String generateConcreteRule(List<LegalAuthority> finalAuthorities, LegalIssueType issueType) {
        if (finalAuthorities.isEmpty()) {
            return "Legal principles apply to this issue.";
        }
        
        // Collect authority titles and citations for rule generation
        List<String> caseAuthorities = new ArrayList<>();
        List<String> statuteAuthorities = new ArrayList<>();
        
        for (LegalAuthority auth : finalAuthorities) {
            String titleWithCitation = auth.getTitle() + " (" + auth.getCitation() + ")";
            if (auth.getAuthorityType() == AuthorityType.CASE_LAW) {
                caseAuthorities.add(titleWithCitation);
            } else if (auth.getAuthorityType() == AuthorityType.STATUTE) {
                statuteAuthorities.add(titleWithCitation);
            }
        }
        
        // Generate issue-specific rule templates
        switch (issueType) {
            case REIMBURSEMENT:
                return generateReimbursementRule(caseAuthorities, statuteAuthorities);
            case PROPERTY_CHARACTERIZATION:
                return generatePropertyCharacterizationRule(caseAuthorities, statuteAuthorities);
            case SUPPORT:
                return generateSpousalSupportRule(caseAuthorities, statuteAuthorities);
            case CUSTODY:
                return generateCustodyRule(caseAuthorities, statuteAuthorities);
            default:
                return generateGenericCitationAwareRule(finalAuthorities);
        }
    }
    
    /**
     * Generate concrete rule for REIMBURSEMENT issues.
     */
    private String generateReimbursementRule(List<String> caseAuthorities, List<String> statuteAuthorities) {
        StringBuilder rule = new StringBuilder();
        
        // Check for recognized authority names
        boolean hasEpstein = caseAuthorities.stream().anyMatch(c -> c.toLowerCase().contains("epstein"));
        boolean hasWatts = caseAuthorities.stream().anyMatch(c -> c.toLowerCase().contains("watts"));
        boolean hasStatute = !statuteAuthorities.isEmpty();
        
        if (hasEpstein && hasStatute) {
            rule.append("A spouse who uses separate funds after separation to pay community obligations may seek reimbursement. ");
            rule.append("Under the Epstein principles, reimbursement is available subject to applicable California family law requirements. ");
            rule.append("See ").append(String.join(", ", statuteAuthorities)).append(" for statutory requirements.");
        } else if (hasEpstein) {
            rule.append("Reimbursement rights are governed by the Epstein principles established in case law. ");
            rule.append("A spouse who pays community obligations with separate property may be entitled to reimbursement.");
        } else if (hasStatute) {
            rule.append("Reimbursement of family support obligations is governed by statute. ");
            rule.append("An obligation to reimburse a spouse for payments made to community property obligations is subject to statutory requirements. ");
            rule.append("See ").append(String.join(", ", statuteAuthorities)).append(" for applicable rules.");
        } else {
            rule.append("Reimbursement rights may be available to a spouse who uses separate funds to pay community obligations, ");
            rule.append("subject to established legal principles and statutory requirements.");
        }
        
        return rule.toString();
    }
    
    /**
     * Generate concrete rule for PROPERTY_CHARACTERIZATION issues.
     */
    private String generatePropertyCharacterizationRule(List<String> caseAuthorities, List<String> statuteAuthorities) {
        StringBuilder rule = new StringBuilder();
        
        boolean hasStatute = !statuteAuthorities.isEmpty();
        
        if (hasStatute) {
            rule.append("Property characterization determines whether assets are community property or separate property. ");
            rule.append("California law establishes rules for characterizing property acquired during marriage based on the source of funds. ");
            rule.append("See ").append(String.join(", ", statuteAuthorities)).append(" for statutory definitions and rules.");
        } else if (!caseAuthorities.isEmpty()) {
            rule.append("Property characterization is determined by the source of funds used to acquire the property. ");
            rule.append("Case law establishes principles for determining whether property is community or separate based on payment timing and source.");
        } else {
            rule.append("Property must be properly characterized as community or separate property according to established legal principles.");
        }
        
        return rule.toString();
    }
    
    /**
     * Generate concrete rule for SPOUSAL_SUPPORT issues.
     */
    private String generateSpousalSupportRule(List<String> caseAuthorities, List<String> statuteAuthorities) {
        StringBuilder rule = new StringBuilder();
        
        if (!statuteAuthorities.isEmpty()) {
            rule.append("Spousal support obligations are determined according to statutory guidelines. ");
            rule.append("The court shall order one spouse to pay reasonable support to the other as provided by statute. ");
            rule.append("See ").append(String.join(", ", statuteAuthorities)).append(" for calculation and factors.");
        } else {
            rule.append("Spousal support is determined based on established legal factors and statutory provisions. ");
            rule.append("The amount and duration of support depend on the financial circumstances and needs of the parties.");
        }
        
        return rule.toString();
    }
    
    /**
     * Generate concrete rule for CUSTODY issues.
     */
    private String generateCustodyRule(List<String> caseAuthorities, List<String> statuteAuthorities) {
        StringBuilder rule = new StringBuilder();
        
        if (!statuteAuthorities.isEmpty()) {
            rule.append("Child custody determinations are made in the best interests of the child. ");
            rule.append("The court shall consider statutory factors when determining custody arrangements. ");
            rule.append("See ").append(String.join(", ", statuteAuthorities)).append(" for custody factors and procedures.");
        } else {
            rule.append("Custody decisions are made based on the best interests of the child, ");
            rule.append("considering factors such as parental relationship, child's needs, and family stability.");
        }
        
        return rule.toString();
    }
    
    /**
     * Generate a generic but citation-aware rule for unrecognized issue types.
     */
    private String generateGenericCitationAwareRule(List<LegalAuthority> finalAuthorities) {
        StringBuilder rule = new StringBuilder();
        
        rule.append("Legal principles from relevant authorities apply to this issue. ");
        rule.append("See ");
        
        List<String> citations = finalAuthorities.stream()
            .map(auth -> auth.getTitle() + " (" + auth.getCitation() + ")")
            .toList();
        
        for (int i = 0; i < citations.size(); i++) {
            if (i > 0 && i < citations.size() - 1) rule.append(", ");
            else if (i > 0) rule.append(" and ");
            rule.append(citations.get(i));
        }
        
        rule.append(" for applicable legal principles.");
        return rule.toString();
    }

    /**
     * Append RELEVANT AUTHORITIES section using pre-selected final authorities.
     * 
     * @param answer StringBuilder to append to
     * @param finalAuthorities Final authorities to display
     * @param renderedAuthorityIds Set to track rendered authorities
     */
    private void appendRelevantAuthoritiesSectionWithFinal(
        StringBuilder answer,
        List<LegalAuthority> finalAuthorities,
        Set<String> renderedAuthorityIds
    ) {
        if (finalAuthorities.isEmpty()) {
            answer.append("No authorities retrieved for the identified issues.\n\n");
        } else {
            finalAuthorities.forEach(auth -> {
                answer.append(String.format("- %s (%s): %s\n",
                    auth.getCitation(),
                    auth.getAuthorityType().toString().replace("_", " "),
                    auth.getTitle()
                ));
                renderedAuthorityIds.add(auth.getAuthorityId());
            });
            answer.append("\n");
        }
    }

    /**
     * Append the LEGAL RULE section using authority summaries.
     * Integrates authority summaries as the rule section of the IRAC structure,
     * directly connecting legal authorities to the analysis reasoning.
     * 
     * @param answer StringBuilder to append to
     * @param context Case analysis context containing authority summaries
     */
    private void appendLegalRuleSection(StringBuilder answer, CaseAnalysisContext context) {
        if (context.getAuthoritySummaries().isEmpty()) {
            answer.append("No specific legal authorities retrieved for analysis.\n");
        } else {
            boolean first = true;
            for (AuthoritySummary summary : context.getAuthoritySummaries()) {
                if (!first) {
                    answer.append("\n");
                }
                answer.append(summary.getSummarizedRule());
                first = false;
            }
        }
    }

    /**
     * Append the Relevant Authorities section with top 2 unique authorities.
     * Includes debug logging to track raw, ranked, and rendered authorities.
     * 
     * @param answer StringBuilder to append to
     * @param context Case analysis context containing authority summaries
     * @param renderedAuthorityIds Set to track which authorities have been rendered (for dedup)
     */
    private void appendRelevantAuthoritiesSection(
        StringBuilder answer, 
        CaseAnalysisContext context, 
        Set<String> renderedAuthorityIds
    ) {
        // Collect all unique authorities from all summaries
        Map<String, LegalAuthority> authorityMap = new LinkedHashMap<>();
        
        for (AuthoritySummary summary : context.getAuthoritySummaries()) {
            for (LegalAuthority auth : summary.getAuthorities()) {
                // Deduplicate by authorityId
                if (!authorityMap.containsKey(auth.getAuthorityId())) {
                    authorityMap.put(auth.getAuthorityId(), auth);
                }
            }
        }
        
        List<LegalAuthority> uniqueAuthorities = new ArrayList<>(authorityMap.values());
        
        // Log raw authorities before ranking
        if (logger.isDebugEnabled() && !uniqueAuthorities.isEmpty() && !context.getIdentifiedIssues().isEmpty()) {
            StringBuilder rawLog = new StringBuilder("\n[CASE_ANALYSIS_AUTHORITY_SELECTION_START]\n");
            CaseIssue primaryIssue = context.getIdentifiedIssues().get(0);
            rawLog.append("Issue: ").append(primaryIssue.getType()).append("\n");
            rawLog.append("Raw authorities before re-ranking (").append(uniqueAuthorities.size()).append(" total):\n");
            for (LegalAuthority auth : uniqueAuthorities) {
                rawLog.append(String.format("  - %s | %s | %s | score=%.3f\n",
                    auth.getAuthorityId(),
                    auth.getTitle(),
                    auth.getAuthorityType(),
                    auth.getRelevanceScore()
                ));
            }
            logger.debug(rawLog.toString());
        }
        
        // Re-rank authorities by relevance to the identified issues
        // Use the first issue as primary for ranking
        if (!uniqueAuthorities.isEmpty() && !context.getIdentifiedIssues().isEmpty()) {
            CaseIssue primaryIssue = context.getIdentifiedIssues().get(0);
            uniqueAuthorities = rankAuthoritiesForIssue(primaryIssue, uniqueAuthorities);
            
            // Log ranked authorities with calculated scores
            if (logger.isDebugEnabled()) {
                StringBuilder rankedLog = new StringBuilder("\nRe-ranked authorities for issue ");
                rankedLog.append(primaryIssue.getType()).append(":\n");
                for (int i = 0; i < uniqueAuthorities.size(); i++) {
                    LegalAuthority auth = uniqueAuthorities.get(i);
                    double calculatedScore = calculateAuthorityRelevanceScore(primaryIssue, auth);
                    double keywordScore = calculateIssueSpecificKeywordScore(primaryIssue, auth);
                    rankedLog.append(String.format("  [%d] %s | %s\n",
                        (i + 1),
                        auth.getAuthorityId(),
                        auth.getTitle()
                    ));
                    rankedLog.append(String.format("       base_score=%.3f, calc_score=%.3f (keyword_score=%.3f)\n",
                        auth.getRelevanceScore(),
                        calculatedScore,
                        keywordScore
                    ));
                }
                logger.debug(rankedLog.toString());
            }
        } else {
            // Fallback: sort by citation if no issues
            uniqueAuthorities.sort((a, b) -> a.getCitation().compareTo(b.getCitation()));
        }
        
        if (uniqueAuthorities.isEmpty()) {
            answer.append("No authorities retrieved for the identified issues.\n\n");
        } else {
            // Show top 2 authorities only
            List<LegalAuthority> renderedList = uniqueAuthorities.stream()
                .limit(2)
                .peek(auth -> renderedAuthorityIds.add(auth.getAuthorityId()))
                .toList();
            
            // Log final rendered authorities
            if (logger.isDebugEnabled()) {
                StringBuilder renderedLog = new StringBuilder("\nFinal rendered authorities (");
                renderedLog.append(renderedList.size()).append(" shown):\n");
                for (LegalAuthority auth : renderedList) {
                    renderedLog.append(String.format("  - %s | %s\n",
                        auth.getAuthorityId(),
                        auth.getTitle()
                    ));
                }
                renderedLog.append("[CASE_ANALYSIS_AUTHORITY_SELECTION_END]\n");
                logger.debug(renderedLog.toString());
            }
            
            renderedList.forEach(auth -> {
                answer.append(String.format("- %s (%s): %s\n",
                    auth.getCitation(),
                    auth.getAuthorityType().toString().replace("_", " "),
                    auth.getTitle()
                ));
            });
            answer.append("\n");
        }
    }

    /**
     * Append the Relevant Authorities & Rule Summary section with only unique authorities
     * that have not already been rendered in the main Relevant Authorities section.
     * Includes debug logging to track authority selection per issue.
     * 
     * @param answer StringBuilder to append to
     * @param context Case analysis context
     * @param renderedAuthorityIds Set of authorities already rendered in main section
     */
    private void appendRulesSummarySection(
        StringBuilder answer,
        CaseAnalysisContext context,
        Set<String> renderedAuthorityIds
    ) {
        if (context.getAuthoritySummaries().isEmpty()) {
            answer.append("No specific authorities retrieved for the identified issues.\n\n");
        } else {
            for (int issueIdx = 0; issueIdx < context.getAuthoritySummaries().size(); issueIdx++) {
                AuthoritySummary authoritySummary = context.getAuthoritySummaries().get(issueIdx);
                
                answer.append(String.format("For %s Issue:\n", 
                    authoritySummary.getIssueType().toString().replace("_", " ").toLowerCase()));
                answer.append(String.format("Rule: %s\n", authoritySummary.getSummarizedRule()));
                
                if (!authoritySummary.getAuthorities().isEmpty()) {
                    // Deduplicate and filter out already-rendered authorities
                    Map<String, LegalAuthority> uniqueInSummary = new LinkedHashMap<>();
                    for (LegalAuthority auth : authoritySummary.getAuthorities()) {
                        // Only include if not already rendered in main section and not already in this summary
                        if (!renderedAuthorityIds.contains(auth.getAuthorityId()) &&
                            !uniqueInSummary.containsKey(auth.getAuthorityId())) {
                            uniqueInSummary.put(auth.getAuthorityId(), auth);
                        }
                    }
                    
                    if (!uniqueInSummary.isEmpty()) {
                        // Log raw authorities for this issue before ranking
                        if (logger.isDebugEnabled()) {
                            StringBuilder rawLog = new StringBuilder("\n[CASE_ANALYSIS_RULESUMMARY_START] Issue: ");
                            rawLog.append(authoritySummary.getIssueType()).append("\n");
                            rawLog.append("Raw authorities before re-ranking (").append(uniqueInSummary.size()).append(" total):\n");
                            for (LegalAuthority auth : uniqueInSummary.values()) {
                                rawLog.append(String.format("  - %s | %s | %s | score=%.3f\n",
                                    auth.getAuthorityId(),
                                    auth.getTitle(),
                                    auth.getAuthorityType(),
                                    auth.getRelevanceScore()
                                ));
                            }
                            logger.debug(rawLog.toString());
                        }
                        
                        // Re-rank authorities by issue relevance
                        CaseIssue issueForRanking = null;
                        // Find the matching issue from context
                        for (CaseIssue contextIssue : context.getIdentifiedIssues()) {
                            if (contextIssue.getType() == authoritySummary.getIssueType()) {
                                issueForRanking = contextIssue;
                                break;
                            }
                        }
                        
                        List<LegalAuthority> rankedAuthorities = new ArrayList<>(uniqueInSummary.values());
                        if (issueForRanking != null) {
                            rankedAuthorities = rankAuthoritiesForIssue(issueForRanking, rankedAuthorities);
                            
                            // Log ranked authorities
                            if (logger.isDebugEnabled()) {
                                StringBuilder rankedLog = new StringBuilder("\nRe-ranked authorities for ");
                                rankedLog.append(issueForRanking.getType()).append(":\n");
                                for (int i = 0; i < rankedAuthorities.size(); i++) {
                                    LegalAuthority auth = rankedAuthorities.get(i);
                                    double calculatedScore = calculateAuthorityRelevanceScore(issueForRanking, auth);
                                    double keywordScore = calculateIssueSpecificKeywordScore(issueForRanking, auth);
                                    rankedLog.append(String.format("  [%d] %s | %s\n",
                                        (i + 1),
                                        auth.getAuthorityId(),
                                        auth.getTitle()
                                    ));
                                    rankedLog.append(String.format("       base_score=%.3f, calc_score=%.3f (keyword_score=%.3f)\n",
                                        auth.getRelevanceScore(),
                                        calculatedScore,
                                        keywordScore
                                    ));
                                }
                                logger.debug(rankedLog.toString());
                            }
                        }
                        
                        answer.append("Supporting Authorities:\n");
                        List<LegalAuthority> renderedList = rankedAuthorities.stream()
                            .limit(2)
                            .peek(auth -> renderedAuthorityIds.add(auth.getAuthorityId()))
                            .toList();
                        
                        // Log final rendered authorities for this issue
                        if (logger.isDebugEnabled()) {
                            StringBuilder renderedLog = new StringBuilder("\nFinal rendered authorities (");
                            renderedLog.append(renderedList.size()).append(" shown):\n");
                            for (LegalAuthority auth : renderedList) {
                                renderedLog.append(String.format("  - %s | %s\n",
                                    auth.getAuthorityId(),
                                    auth.getTitle()
                                ));
                            }
                            renderedLog.append("[CASE_ANALYSIS_RULESUMMARY_END]\n");
                            logger.debug(renderedLog.toString());
                        }
                        
                        renderedList.forEach(auth -> {
                            answer.append(String.format(
                                "  - %s (%s) - %s\n",
                                auth.getCitation(),
                                auth.getAuthorityType(),
                                auth.getTitle()
                            ));
                        });
                    }
                }
                answer.append("\n");
            }
        }
    }



    /**
     * Append APPLICATION TO RULE section showing rule element to fact mapping.
     * Maps legal rule elements derived from issue type to supporting/missing facts.
     * Filters out low-quality evidence snippets before rendering.
     * 
     * @param answer StringBuilder to append to
     * @param context Case analysis context
     */
    private void appendApplicationToRuleSection(StringBuilder answer, CaseAnalysisContext context) {
        if (context.getIdentifiedIssues().isEmpty()) {
            answer.append("No identified legal issues to analyze.\n\n");
            return;
        }
        
        // Get primary issue
        CaseIssue primaryIssue = context.getIdentifiedIssues().get(0);
        
        // Derive rule elements for this issue type
        List<String> ruleElements = deriveRuleElements(primaryIssue.getType());
        
        if (ruleElements.isEmpty()) {
            answer.append("No specific rule elements identified for analysis.\n\n");
            return;
        }
        
        // Log entire raw fact pool for this section
        if (logger.isDebugEnabled() && !context.getRelevantFacts().isEmpty()) {
            logFactPoolPreview("[FACT_FILTER_PIPELINE_START]", context.getRelevantFacts());
        }
        
        // For each rule element, show supporting facts, missing facts, and assessment
        for (String element : ruleElements) {
            answer.append("Element: ").append(element).append("\n");
            
            // Find supporting facts for this element (with detailed pipeline logging)
            List<CaseFact> allSupportingFacts = findSupportingFactsForElement(element, context, primaryIssue.getType());
            
            // Log facts after strict filter + keyword matching but before rendering filter
            if (logger.isDebugEnabled()) {
                logRuleElementPipeline(element, context, allSupportingFacts);
            }
            
            // Filter out low-quality snippets before rendering
            List<CaseFact> supportingFacts = filterHighQualityFactsWithLogging(allSupportingFacts);
            
            // Log final rendered facts
            if (logger.isDebugEnabled() && !supportingFacts.isEmpty()) {
                logFinalRenderedFacts(element, supportingFacts);
            }
            
            if (!supportingFacts.isEmpty()) {
                answer.append("Supporting Facts:\n");
                supportingFacts.stream()
                    .limit(3)
                    .forEach(f -> answer.append("  - ").append(f.getDescription()).append("\n"));
            } else {
                answer.append("Supporting Facts:\n");
                answer.append("  (none identified)\n");
            }
            
            // Find missing facts for this element
            List<MissingFact> missingFacts = findMissingFactsForElement(element, context, primaryIssue.getType());
            if (!missingFacts.isEmpty()) {
                answer.append("Missing Facts:\n");
                missingFacts.stream()
                    .limit(2)
                    .forEach(f -> answer.append("  - ").append(f.getDescription()).append("\n"));
            }
            
            // Assessment: determine if element is supported, partially supported, weak, or unknown
            String assessment = assessRuleElement(element, supportingFacts, missingFacts);
            answer.append("Assessment: ").append(assessment).append("\n\n");
        }
    }
    
    /**
     * Log preview of entire fact pool at start of APPLICATION TO RULE section.
     * Shows count of facts by favorability/polarity.
     */
    private void logFactPoolPreview(String stage, List<CaseFact> allFacts) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        
        long favorableCount = allFacts.stream().filter(CaseFact::isFavorable).count();
        long unfavorableCount = allFacts.size() - favorableCount;
        
        logger.debug("{} Fact pool: {} total | {} favorable | {} unfavorable",
            stage, allFacts.size(), favorableCount, unfavorableCount);
    }
    
    /**
     * Log detailed pipeline for a specific rule element.
     * Shows which facts passed strict filter, matched keywords, etc.
     */
    private void logRuleElementPipeline(String element, CaseAnalysisContext context, List<CaseFact> assignedFacts) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        
        StringBuilder pipelineLog = new StringBuilder();
        pipelineLog.append("\n[FACT_FILTER_PIPELINE] Element: \"").append(element).append("\"\n");
        pipelineLog.append("  Assigned through strict filter + keyword matching: ").append(assignedFacts.size()).append(" facts\n");
        
        for (CaseFact fact : assignedFacts) {
            String truncated = fact.getDescription().length() > 70 ? 
                fact.getDescription().substring(0, 70) + "..." : fact.getDescription();
            pipelineLog.append("    - ").append(truncated).append("\n");
        }
        
        logger.debug(pipelineLog.toString());
    }
    
    /**
     * Log final rendered facts after all filtering stages.
     */
    private void logFinalRenderedFacts(String element, List<CaseFact> renderedFacts) {
        if (!logger.isDebugEnabled() || renderedFacts.isEmpty()) {
            return;
        }
        
        StringBuilder finalLog = new StringBuilder();
        finalLog.append("[FACT_FILTER_PIPELINE] Final rendered for \"").append(
            element.substring(0, Math.min(50, element.length()))
        ).append("\": ").append(renderedFacts.size()).append(" facts\n");
        
        for (CaseFact fact : renderedFacts) {
            String truncated = fact.getDescription().length() > 60 ? 
                fact.getDescription().substring(0, 60) + "..." : fact.getDescription();
            finalLog.append("  ✓ RENDERED: ").append(truncated).append("\n");
        }
        
        logger.debug(finalLog.toString());
    }
    
    /**
     * Derive rule elements for a given issue type.
     * Returns 2-4 key elements that must be proven for the issue.
     * 
     * @param issueType The legal issue type
     * @return List of rule element descriptions
     */
    private List<String> deriveRuleElements(LegalIssueType issueType) {
        List<String> elements = new ArrayList<>();
        
        switch (issueType) {
            case REIMBURSEMENT:
                elements.add("Post-separation payment was made to satisfy a community obligation");
                elements.add("The payment was made with separate property funds");
                elements.add("The payment provided a benefit to the community property or other spouse");
                elements.add("No offset or benefit has been received for the payment");
                break;
            case PROPERTY_CHARACTERIZATION:
                elements.add("Property was acquired during the marriage");
                elements.add("Source of funds can be traced (community or separate)");
                elements.add("No transmutation or commingling occurred");
                elements.add("Applicable statutory characterization rules apply");
                break;
            case SUPPORT:
                elements.add("Parties were married or in a registered domestic partnership");
                elements.add("One party has the ability to pay support");
                elements.add("The other party has a need for support");
                elements.add("Financial circumstances meet statutory thresholds");
                break;
            case CUSTODY:
                elements.add("Court has jurisdiction over custody determination");
                elements.add("Child's best interests are the governing standard");
                elements.add("Relevant statutory factors have been considered");
                elements.add("Proposed arrangement serves the child's welfare");
                break;
            default:
                elements.add("Primary legal principles apply");
                elements.add("Statutory or case law requirements are met");
                break;
        }
        
        return elements;
    }
    
    /**
     * Extract PaymentRecords from evidence chunks for REIMBURSEMENT issues.
     * Filters for mortgage/payment-related chunks and extracts structured payment data.
     * 
     * Applies DOS (Date of Separation) filtering using CaseProfile:
     * - Only includes payments made AFTER DOS as post-separation reimbursement evidence
     * - Records before DOS are excluded with reason "before DOS"
     * 
     * @return List of PaymentRecords converted to CaseFacts for rendering
     */
    private List<CaseFact> extractPaymentRecordsAsFacts() {
        List<CaseFact> paymentFacts = new ArrayList<>();
        CaseProfile profile = currentCaseProfile.get();
        String dos = profile != null ? profile.getDateOfSeparation() : null;
        
        List<EvidenceChunk> chunks = currentEvidenceChunks.get();
        if (chunks == null || chunks.isEmpty()) {
            return paymentFacts;
        }
        
        // Keywords that indicate mortgage/payment statements
        String[] paymentKeywords = {
            "mortgage", "payment", "loan", "principal", "interest",
            "monthly", "due date", "amortization", "statement"
        };
        
        for (EvidenceChunk chunk : chunks) {
            String content = chunk.text().toLowerCase();
            String source = String.format("Doc %d, Chunk %d", chunk.docId(), chunk.chunkId());
            
            // Check if this chunk contains payment-related content
            boolean isPaymentChunk = false;
            for (String keyword : paymentKeywords) {
                if (content.contains(keyword)) {
                    isPaymentChunk = true;
                    break;
                }
            }
            
            if (!isPaymentChunk) {
                continue;
            }
            
            // Extract PaymentRecords from this chunk
            List<PaymentRecord> records = paymentRecordExtractor.extract(chunk.text());
            
            // Convert each PaymentRecord to a CaseFact, applying DOS filtering
            for (PaymentRecord record : records) {
                // Apply DOS filtering: only include payments AFTER DOS
                if (dos != null && !dos.isBlank()) {
                    PaymentDateFilterResult filterResult = filterPaymentByDOS(record, dos);
                    if (!filterResult.isIncluded()) {
                        logger.info("[DOS_FILTER] recordDate={} dos={} decision=EXCLUDE reason={}",
                            record.getPaymentDate(), dos, filterResult.getReason());
                        continue;  // Skip records before DOS
                    }
                    logger.info("[DOS_FILTER] recordDate={} dos={} decision=INCLUDE",
                        record.getPaymentDate(), dos);
                }
                
                String factDescription = formatPaymentRecordAsFactDescription(record);
                if (factDescription != null) {
                    CaseFact fact = new CaseFact(
                        factDescription,
                        FactPolarity.SUPPORTING,
                        source,
                        LegalIssueType.REIMBURSEMENT
                    );
                    paymentFacts.add(fact);
                    
                    logger.debug("[PAYMENT_RECORD_EXTRACTION] Extracted payment fact: {} | source={}",
                        factDescription, source);
                }
            }
        }
        
        return paymentFacts;
    }
    
    /**
     * Represents result of DOS filtering a payment record.
     */
    private static class PaymentDateFilterResult {
        private final boolean included;
        private final String reason;
        
        PaymentDateFilterResult(boolean included, String reason) {
            this.included = included;
            this.reason = reason;
        }
        
        boolean isIncluded() {
            return included;
        }
        
        String getReason() {
            return reason;
        }
    }
    
    /**
     * Filter a payment record by Date of Separation.
     * Only includes payments made STRICTLY AFTER DOS.
     * 
     * Date formats supported: "MM/DD/YYYY" and "MM/DD/YY"
     * Comparison is numeric: 2026-01-02 > 2025-12-24
     * 
     * @param record The payment record to filter
     * @param dos Date of Separation (format: MM/DD/YYYY or MM/DD/YY)
     * @return FilterResult with inclusion decision and reason
     */
    private PaymentDateFilterResult filterPaymentByDOS(PaymentRecord record, String dos) {
        String paymentDate = record.getPaymentDate();
        
        if (paymentDate == null || paymentDate.isBlank()) {
            // No date to compare - cannot filter by DOS
            return new PaymentDateFilterResult(true, "no_payment_date");
        }
        
        try {
            // Normalize dates for comparison
            String normalizedPaymentDate = normalizeDateForComparison(paymentDate);
            String normalizedDOS = normalizeDateForComparison(dos);
            
            if (normalizedPaymentDate == null || normalizedDOS == null) {
                // Cannot parse dates - include as fallback
                return new PaymentDateFilterResult(true, "unparseable_date");
            }
            
            // Compare numerically: "YYYYMMDD" format allows direct string comparison
            // Example: "20260102" > "20251224" is true
            int comparison = normalizedPaymentDate.compareTo(normalizedDOS);
            
            if (comparison > 0) {
                // Payment date is AFTER DOS - include
                return new PaymentDateFilterResult(true, "after_dos");
            } else if (comparison == 0) {
                // Payment date equals DOS - exclude (must be strictly after)
                return new PaymentDateFilterResult(false, "on_dos_date");
            } else {
                // Payment date is BEFORE DOS - exclude
                return new PaymentDateFilterResult(false, "before_dos");
            }
        } catch (Exception e) {
            logger.warn("[DOS_FILTER] Error comparing dates: recordDate={} dos={} error={}",
                paymentDate, dos, e.getMessage());
            return new PaymentDateFilterResult(true, "comparison_error");
        }
    }
    
    /**
     * Normalize date strings to "YYYYMMDD" format for numeric string comparison.
     * 
     * Supports input formats:
     * - "MM/DD/YYYY" → "YYYYMMDD"
     * - "MM/DD/YY" → "YYYYMMDD" (assumes 20xx for YY < 50, 19xx for YY >= 50)
     * 
     * @param dateStr Date string in MM/DD/YYYY or MM/DD/YY format
     * @return Normalized date string in YYYYMMDD format, or null if unparseable
     */
    private String normalizeDateForComparison(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        
        String[] parts = dateStr.split("/");
        if (parts.length != 3) {
            return null;
        }
        
        try {
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            
            // Handle 2-digit year
            if (year < 100) {
                year = (year < 50) ? 2000 + year : 1900 + year;
            }
            
            // Validate date components
            if (month < 1 || month > 12 || day < 1 || day > 31 || year < 1900 || year > 2100) {
                return null;
            }
            
            // Format as YYYYMMDD for string comparison
            return String.format("%04d%02d%02d", year, month, day);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Format a PaymentRecord as a human-readable fact description.
     * Example: "Mortgage payment of $4,679.23 on 01/02/26 for Newark property (Loan #2109013512)"
     */
    private String formatPaymentRecordAsFactDescription(PaymentRecord record) {
        if (record.getAmount() == null) {
            return null;
        }
        
        StringBuilder fact = new StringBuilder("Mortgage payment of $");
        fact.append(String.format("%.2f", record.getAmount()));
        
        // Add date if available
        if (record.getPaymentDate() != null && !record.getPaymentDate().isEmpty()) {
            fact.append(" on ").append(record.getPaymentDate());
        }
        
        // Add property if available
        if (record.getPropertyName() != null && !record.getPropertyName().isEmpty()) {
            fact.append(" for ").append(record.getPropertyName()).append(" property");
        }
        
        // Add loan number if available
        if (record.getLoanNumber() != null && !record.getLoanNumber().isEmpty()) {
            fact.append(" (Loan #").append(record.getLoanNumber()).append(")");
        }
        
        return fact.toString();
    }
    
    /**
     * Find facts that support a given rule element.
     * Matches facts based on keywords in the element description.
     * Applies strict quality filtering BEFORE keyword matching.
     * Uses selective multi-keyword matching to avoid reusing generic facts across all elements.
     * 
     * @param element Rule element description
     * @param context Case analysis context
     * @param issueType Issue type for context
     * @return List of supporting facts (best matches only, not generic repeats)
     */
    private List<CaseFact> findSupportingFactsForElement(
        String element, CaseAnalysisContext context, LegalIssueType issueType
    ) {
        List<CaseFact> supportingFacts = new ArrayList<>();
        List<CaseFact> rejectedByStrictFilter = new ArrayList<>();
        List<CaseFact> acceptedByStrictFilter = new ArrayList<>();
        
        // Get all favorable facts for this issue type
        List<CaseFact> allFavorableFacts = context.getRelevantFacts().stream()
            .filter(CaseFact::isFavorable)
            .filter(f -> f.getRelevantIssue() == issueType)
            .toList();
        
        if (logger.isDebugEnabled()) {
            logger.debug("[STRICT_FILTER] Processing {} favorable facts for element: \"{}\"",
                allFavorableFacts.size(), truncateForLogging(element));
        }
        
        // Apply fact classification BEFORE strict filter - exclude noisy/unrelated facts
        List<CaseFact> classifiedFacts = filterFactsByClassification(allFavorableFacts);
        
        // Apply strict quality filtering BEFORE rule-element assignment
        for (CaseFact fact : classifiedFacts) {
            if (isStrictlyHighQualityFact(fact)) {
                acceptedByStrictFilter.add(fact);
            } else {
                rejectedByStrictFilter.add(fact);
            }
        }
        
        // Log which facts passed strict filter
        if (logger.isDebugEnabled() && !acceptedByStrictFilter.isEmpty()) {
            StringBuilder acceptedLog = new StringBuilder("[STRICT_FILTER] Accepted facts:\n");
            for (CaseFact fact : acceptedByStrictFilter) {
                acceptedLog.append("  ✓ ").append(truncateForLogging(fact.getDescription())).append("\n");
            }
            logger.debug(acceptedLog.toString());
        }
        
        // Log which facts were rejected
        if (logger.isDebugEnabled() && !rejectedByStrictFilter.isEmpty()) {
            StringBuilder rejectedLog = new StringBuilder("[STRICT_FILTER] Rejected facts:\n");
            for (CaseFact fact : rejectedByStrictFilter) {
                rejectedLog.append("  ✗ ").append(truncateForLogging(fact.getDescription())).append("\n");
            }
            logger.debug(rejectedLog.toString());
        }
        
        // Apply selective keyword matching to avoid generic facts matching every element
        supportingFacts = selectiveKeywordMatching(element, acceptedByStrictFilter, issueType);
        
        // Log keyword matching results
        if (logger.isDebugEnabled()) {
            logger.debug("[STRICT_FILTER] Keyword matching: {} of {} accepted facts matched element keywords with selective criteria",
                supportingFacts.size(), acceptedByStrictFilter.size());
        }
        
        // For REIMBURSEMENT issues, first element: extract and add PaymentRecords
        // First element: "Post-separation payment was made to satisfy a community obligation"
        if (issueType == LegalIssueType.REIMBURSEMENT && 
            element.toLowerCase().contains("post-separation") && 
            element.toLowerCase().contains("payment")) {
            
            List<CaseFact> paymentFacts = extractPaymentRecordsAsFacts();
            if (!paymentFacts.isEmpty()) {
                supportingFacts.addAll(paymentFacts);
                logger.info("[PAYMENT_RECORD_EXTRACTION] Added {} payment facts to supporting facts", paymentFacts.size());
            }
        }
        
        // Keep at most 2 high-quality supporting facts per rule element
        return supportingFacts.stream().distinct().limit(2).toList();
    }
    
    /**
     * Perform selective keyword matching between rule element and facts.
     * Uses multi-keyword matching to prefer specific matches over generic reuse.
     * 
     * Goal: Assign each fact to the best-matching element, not to every element.
     * 
     * @param element Rule element description
     * @param facts Candidate facts to match
     * @param issueType Issue type for context-aware matching
     * @return Facts that strongly match this element
     */
    private List<CaseFact> selectiveKeywordMatching(String element, List<CaseFact> facts, LegalIssueType issueType) {
        List<CaseFact> matched = new ArrayList<>();
        String elementLower = element.toLowerCase();
        
        for (CaseFact fact : facts) {
            String factDesc = fact.getDescription();
            String factLower = factDesc.toLowerCase();
            
            // For REIMBURSEMENT issues, apply aggressive issue-specific filtering
            if (issueType == LegalIssueType.REIMBURSEMENT) {
                if (!isReimbursementRelevantFact(factDesc, elementLower)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("[REIMBURSEMENT_FILTER] Rejected fact for element '{}': {} | reason=not_reimbursement_relevant",
                            truncateForLogging(elementLower), truncateForLogging(factDesc));
                    }
                    continue;
                }
            }
            
            int matchScore = calculateElementMatchScore(elementLower, factLower, issueType);
            
            // For REIMBURSEMENT, require strong matches (score >= 2)
            // For other issue types, accept meaningful matches (score >= 1)
            int minScore = (issueType == LegalIssueType.REIMBURSEMENT) ? 2 : 1;
            
            if (matchScore >= minScore) {
                matched.add(fact);
                if (logger.isDebugEnabled()) {
                    logger.debug("[ELEMENT_MATCH] Accepted fact for element '{}': {} | score={}",
                        truncateForLogging(elementLower), truncateForLogging(factDesc), matchScore);
                }
            }
        }
        
        return matched;
    }
    
    /**
     * Check if a fact is relevant to reimbursement claims.
     * 
     * AGGRESSIVE filtering for reimbursement facts to prevent weak/misleading associations.
     * 
     * Preferred keywords: post-separation, mortgage payment, paid by me, monthly payment, 
     * loan number, due date, payment amount, principal/interest in readable payment statement
     * 
     * De-prioritizes/rejects:
     * - down payment contribution facts
     * - purchase contribution during marriage
     * - general property acquisition background
     * - unrelated employment/performance issues
     * - OCR fragments / payment warning text without clear relevance
     * 
     * @param factDesc The fact description
     * @param elementLower The lowercase rule element being matched
     * @return true if fact is reimbursement-relevant, false otherwise
     */
    private boolean isReimbursementRelevantFact(String factDesc, String elementLower) {
        if (factDesc == null || factDesc.isEmpty()) {
            return false;
        }
        
        String factLower = factDesc.toLowerCase();
        
        // === AGGRESSIVE REJECTION FILTERS ===
        
        // REJECT: Down payment / initial purchase contributions - NEVER relevant to post-separation reimbursement
        // These are explicitly excluded from reimbursement rights under Epstein factors
        if (isDownPaymentFact(factLower)) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_REJECT] Down payment fact rejected: {}", truncateForLogging(factDesc));
            }
            return false;
        }
        
        // REJECT: OCR/statement fragments and form boilerplate
        if (isOCROrFormFragment(factDesc, factLower)) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_REJECT] OCR/form fragment rejected: {}", truncateForLogging(factDesc));
            }
            return false;
        }
        
        // REJECT: Noisy payment statement fragments without clear reimbursement context
        if (isNoisyPaymentFragment(factDesc, factLower)) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_REJECT] Noisy payment fragment rejected: {}", truncateForLogging(factDesc));
            }
            return false;
        }
        
        // REJECT: Employment/performance-related content (unrelated to payment reimbursement)
        if ((factLower.contains("employment") || factLower.contains("performance") || factLower.contains("salary")) &&
            !factLower.contains("pay") && !factLower.contains("paid") && !factLower.contains("payment")) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_REJECT] Employment-related fact rejected: {}", truncateForLogging(factDesc));
            }
            return false;
        }
        
        // REJECT: General property acquisition background without post-separation context
        if ((factLower.contains("purchased") || factLower.contains("acquired") || factLower.contains("bought")) &&
            !factLower.contains("post-separation") && !factLower.contains("after separation") && 
            !factLower.contains("paid") && !factLower.contains("payment") &&
            !factLower.contains("mortgage") && !factLower.contains("loan")) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_REJECT] Property acquisition (no post-sep context) rejected: {}", truncateForLogging(factDesc));
            }
            return false;
        }
        
        // === POSITIVE ACCEPTANCE WITH STRONG EVIDENCE ===
        
        // ACCEPT: Strong post-separation payment evidence
        if (containsPostSeparationPaymentIndicators(factLower)) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_ACCEPT] Post-separation payment fact: {}", truncateForLogging(factDesc));
            }
            return true;
        }
        
        // ACCEPT: Substantial mortgage/loan payment statements with amounts
        if (containsSubstantialMortgagePaymentEvidence(factDesc, factLower)) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_ACCEPT] Mortgage payment statement: {}", truncateForLogging(factDesc));
            }
            return true;
        }
        
        // ACCEPT: Clear first-person payment statements with monetary amounts
        if (containsClearPaymentStatement(factDesc, factLower)) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_ACCEPT] Clear payment statement: {}", truncateForLogging(factDesc));
            }
            return true;
        }
        
        // ACCEPT: Element-specific relevance (separate property funds, benefit analysis)
        if (matchesElementSpecificRelevance(elementLower, factLower, factDesc)) {
            if (logger.isDebugEnabled()) {
                logger.debug("[REIMBURSEMENT_ACCEPT] Element-specific fact: {}", truncateForLogging(factDesc));
            }
            return true;
        }
        
        // REJECT: No strong reimbursement relevance found
        if (logger.isDebugEnabled()) {
            logger.debug("[REIMBURSEMENT_REJECT] No reimbursement relevance found: {}", truncateForLogging(factDesc));
        }
        return false;
    }
    
    /**
     * Detect down payment / initial purchase contribution facts.
     * These are explicitly excluded from Epstein reimbursement remedies.
     * 
     * @param factLower Lowercase fact text
     * @return true if this is a down payment/initial contribution fact
     */
    private boolean isDownPaymentFact(String factLower) {
        // Direct down payment references
        if (factLower.contains("down payment")) {
            return true;
        }
        
        // Initial contribution during marriage acquisition
        if (factLower.contains("initial contribution") || 
            factLower.contains("initial purchase") ||
            factLower.contains("purchase contribution")) {
            return true;
        }
        
        // Purchase price context without post-separation payment context
        if ((factLower.contains("purchase price") || factLower.contains("purchase amount")) &&
            !factLower.contains("post-separation") && !factLower.contains("after separation") &&
            !factLower.contains("paid") && !factLower.contains("payment")) {
            return true;
        }
        
        // "Purchased for $X" or similar acquisition statements without post-separation context
        if ((factLower.matches(".*purchased\\s+for\\s+\\$.*") || factLower.matches(".*acquisition\\s+cost.*")) &&
            !factLower.contains("post-separation") && !factLower.contains("after separation")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Detect OCR fragments and form boilerplate that shouldn't be facts.
     * 
     * @param factDesc Original fact description
     * @param factLower Lowercase fact text
     * @return true if this is OCR/form boilerplate garbage
     */
    private boolean isOCROrFormFragment(String factDesc, String factLower) {
        // Pattern: "[digit(s)] If payment received after [date]" - common OCR fragment
        if (factLower.matches("^\\d+\\s+if\\s+payment.*") ||
            factLower.matches(".*if\\s+payment\\s+is\\s+received\\s+after\\s+\\d+/\\d+/\\d+.*")) {
            return true;
        }
        
        // Isolated table headers without payment context
        if (factDesc.length() < 20 &&
            (factLower.matches("^(date\\s+paid|received|description|principal|interest|escrow|loan|balance)$") ||
             factLower.matches("^\\w+\\s*:\\s*$") ||
             factLower.matches("^[a-z]\\.\\s*$") ||
             factLower.matches("^\\([a-z]\\)\\s*$"))) {
            return true;
        }
        
        // Form boilerplate
        if (factLower.contains("real and personal") ||
            factLower.contains("check the box") ||
            factLower.startsWith("petitioner") ||
            factLower.startsWith("respondent") ||
            factLower.startsWith("attorney") ||
            factLower.contains("court order") && factLower.length() < 20) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Detect noisy payment statement fragments that lack clear relevance.
     * Examples: isolated amounts, payment conditions without context, warning text.
     * 
     * @param factDesc Original fact description
     * @param factLower Lowercase fact text
     * @return true if this is a noisy fragment
     */
    private boolean isNoisyPaymentFragment(String factDesc, String factLower) {
        // Pure numeric or mostly numeric (amounts without context)
        String noWhitespace = factDesc.replaceAll("\\s+", "");
        if (noWhitespace.matches("^[0-9,.$]+$")) {
            return true;  // e.g., "23" or "1,500.00"
        }
        
        // Conditional payment text without substance ("If X happens then...")
        if (factLower.matches(".*if\\s+(payment|the).*then.*") ||
            factLower.matches(".*unless\\s+.*was\\s+(paid|received).*")) {
            return true;
        }
        
        // Isolated rate/percentage without context (e.g., "4.5%", "7% per annum")
        if (factLower.matches("^\\s*(\\d+\\.?\\d*)\\s*%\\s*.*$") && 
            !factLower.contains("interest") && !factLower.contains("payment")) {
            return true;
        }
        
        // Payment warning/instruction text without substance
        if ((factLower.contains("payment") && (factLower.contains("warning") || factLower.contains("late") || 
                                               factLower.contains("overdue") || factLower.contains("penalty"))) &&
            factDesc.length() < 30) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if fact contains strong post-separation payment indicators.
     * 
     * @param factLower Lowercase fact text
     * @return true if contains explicit post-separation payment evidence
     */
    private boolean containsPostSeparationPaymentIndicators(String factLower) {
        // Strong post-separation payment keywords
        if (factLower.contains("post-separation") || factLower.contains("after separation") ||
            factLower.contains("following separation") || factLower.contains("subsequent to separation")) {
            
            // Must also indicate payment/mortgage context
            return (factLower.contains("paid") || factLower.contains("payment") || 
                    factLower.contains("mortgage") || factLower.contains("loan") ||
                    factLower.contains("principal") || factLower.contains("interest"));
        }
        
        return false;
    }
    
    /**
     * Check if fact contains substantial mortgage/loan payment evidence with amounts.
     * 
     * @param factDesc Original fact description
     * @param factLower Lowercase fact text
     * @return true if is substantial mortgage payment statement
     */
    private boolean containsSubstantialMortgagePaymentEvidence(String factDesc, String factLower) {
        // Mortgage/loan payments with amounts and payment frequency
        boolean hasMortgageContext = (factLower.contains("mortgage") || factLower.contains("loan")) &&
            (factLower.contains("paid") || factLower.contains("payment"));
        
        if (!hasMortgageContext) {
            return false;
        }
        
        // Must have amount indication
        boolean hasAmount = factDesc.contains("$") || 
            factLower.matches(".*\\b\\d+(?:,\\d{3})*(?:\\.\\d{2})?\\b.*");
        
        // Must be substantial (20+ characters for context)
        boolean isSubstantial = factDesc.length() >= 20;
        
        // Must have payment components (principal, interest, monthly, etc.)
        boolean hasPaymentContext = factLower.contains("principal") || factLower.contains("interest") ||
            factLower.contains("monthly") || factLower.contains("payment amount") ||
            factLower.contains("due date");
        
        return hasAmount && isSubstantial && (hasPaymentContext || factLower.contains("mortgage payment"));
    }
    
    /**
     * Check if fact is a clear first-person payment statement with amounts.
     * 
     * @param factDesc Original fact description
     * @param factLower Lowercase fact text
     * @return true if is clear payment statement
     */
    private boolean containsClearPaymentStatement(String factDesc, String factLower) {
        // First-person payment indicators
        boolean isFirstPersonPayment = (factLower.contains("i paid") || factLower.contains("i have paid") ||
                                        factLower.contains("we paid") || factLower.contains("my payment")) ||
            (factLower.contains("paid") && (factLower.contains("by me") || factLower.contains("from my")));
        
        if (!isFirstPersonPayment) {
            return false;
        }
        
        // Must have monetary amount
        if (!factDesc.contains("$")) {
            return false;
        }
        
        // Must have mortgage/loan context
        if (!(factLower.contains("mortgage") || factLower.contains("loan") || 
              factLower.contains("principal") || factLower.contains("property"))) {
            return false;
        }
        
        // Must be substantial (20+ characters)
        return factDesc.length() >= 20;
    }
    
    /**
     * Check for element-specific reimbursement fact relevance.
     * 
     * @param elementLower Lowercase rule element
     * @param factLower Lowercase fact text
     * @param factDesc Original fact description
     * @return true if fact matches this specific element
     */
    private boolean matchesElementSpecificRelevance(String elementLower, String factLower, String factDesc) {
        // "Separate property funds" element
        if (elementLower.contains("separate property") || elementLower.contains("separate property funds")) {
            if (factLower.contains("separate") && 
                (factLower.contains("account") || factLower.contains("fund") || 
                 factLower.contains("property") || factLower.contains("source"))) {
                return factDesc.length() >= 20;  // Require substantial statement
            }
        }
        
        // "Benefit to community" element
        if (elementLower.contains("benefit") && elementLower.contains("community")) {
            if (factLower.contains("community") && 
                (factLower.contains("benefit") || factLower.contains("property") || 
                 factLower.contains("mortgaged") || factLower.contains("valued"))) {
                return factDesc.length() >= 20;
            }
        }
        
        // Direct property characterization for community property context
        if (factLower.contains("community property") && factDesc.length() >= 20 &&
            !factLower.contains("separate property")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Calculate how well a fact matches a specific rule element.
     * Higher score = stronger match = fact is specific to this element.
     * 
     * @param elementLower Lowercase element description
     * @param factLower Lowercase fact text
     * @param issueType The legal issue type
     * @return Match score (0 = no match, 1 = weak, 2+ = strong match)
     */
    private int calculateElementMatchScore(String elementLower, String factLower, LegalIssueType issueType) {
        int score = 0;
        
        if (issueType == LegalIssueType.REIMBURSEMENT) {
            // Element: "Post-separation payment was made..."
            if (elementLower.contains("post-separation")) {
                // Require explicit post-separation evidence
                if (factLower.contains("post-separation") || factLower.contains("after separation") || factLower.contains("following separation")) {
                    score += 2;  // Strong match - fact explicitly mentions post-separation
                }
                // Reject generic payment keywords without post-separation context
            }
            // Element: "payment was made with separate property funds"
            else if (elementLower.contains("separate property funds") || elementLower.contains("separate property")) {
                if (factLower.contains("separate") && (factLower.contains("property") || factLower.contains("fund") || factLower.contains("account"))) {
                    score += 2;  // Strong - mentions separate AND property/funds/account
                }
            }
            // Element: "payment provided benefit to community"
            else if (elementLower.contains("benefit") && elementLower.contains("community")) {
                if (factLower.contains("benefit") && factLower.contains("community")) {
                    score += 2;  // Strong - mentions both benefit and community
                }
            }
            // Element: "No offset or benefit received"
            else if (elementLower.contains("offset") || elementLower.contains("no benefit")) {
                if (factLower.contains("offset") || (factLower.contains("received") && factLower.contains("benefit"))) {
                    score += 2;  // Strong - has offset/benefit logic
                }
            }
            // Generic payment element match
            else if (elementLower.contains("payment")) {
                // Require substantial context for payment facts
                if (factLower.contains("mortgage") && (factLower.contains("paid") || factLower.contains("payment"))) {
                    score += 2;  // Strong - mortgage payment
                } else if (factLower.contains("principal") || factLower.contains("interest")) {
                    score += 2;  // Strong - payment components
                }
                // Reject pure "paid" or "payment" without context
            }
        }
        else if (issueType == LegalIssueType.PROPERTY_CHARACTERIZATION) {
            if (elementLower.contains("acquired")) {
                if (factLower.contains("acquired") || factLower.contains("purchased")) {
                    score += 2;
                }
            } else if (elementLower.contains("source")) {
                if (factLower.contains("source") || factLower.contains("trace") || factLower.contains("fund source")) {
                    score += 2;
                } else if (factLower.contains("separate") || factLower.contains("community")) {
                    score += 1;
                }
            } else if (elementLower.contains("transmutation")) {
                if (factLower.contains("transmutation")) {
                    score += 2;
                }
            } else if (elementLower.contains("property")) {
                if ((factLower.contains("property") && (factLower.contains("separate") || factLower.contains("community")))) {
                    score += 2;  // Strong - property + characterization
                } else if (factLower.contains("property")) {
                    score += 1;  // Weak - just property
                }
            }
        }
        else if (issueType == LegalIssueType.CUSTODY) {
            if (elementLower.contains("best interest")) {
                if (factLower.contains("best interest") || factLower.contains("child") || factLower.contains("welfare")) {
                    score += 2;
                }
            } else if (elementLower.contains("jurisdiction")) {
                if (factLower.contains("jurisdiction") || factLower.contains("custody")) {
                    score += 2;
                }
            } else if (elementLower.contains("custody") || elementLower.contains("child")) {
                if (factLower.contains("custody") || (factLower.contains("child") && (factLower.contains("care") || factLower.contains("custody")))) {
                    score += 2;
                } else if (factLower.contains("child")) {
                    score += 1;
                }
            }
        }
        else if (issueType == LegalIssueType.SUPPORT) {
            if (elementLower.contains("ability")) {
                if (factLower.contains("income") || factLower.contains("earning")) {
                    score += 2;
                } else if (factLower.contains("ability")) {
                    score += 2;
                }
            } else if (elementLower.contains("need")) {
                if (factLower.contains("need") || factLower.contains("expense") || factLower.contains("financial")) {
                    score += 2;
                }
            } else if (elementLower.contains("married")) {
                if (factLower.contains("marriage") || factLower.contains("married")) {
                    score += 2;
                }
            }
        }
        
        return score;
    }
    
    /**
     * Find missing facts for a given rule element.
     * Matches missing facts based on keywords.
     * 
     * @param element Rule element description
     * @param context Case analysis context
     * @param issueType Issue type for context
     * @return List of missing facts
     */
    private List<MissingFact> findMissingFactsForElement(
        String element, CaseAnalysisContext context, LegalIssueType issueType
    ) {
        List<MissingFact> missingFacts = new ArrayList<>();
        
        String elementLower = element.toLowerCase();
        
        for (MissingFact fact : context.getMissingFacts()) {
            String factLower = fact.getDescription().toLowerCase();
            
            // Check for keyword overlap
            if (elementLower.contains("payment") && factLower.contains("payment")) missingFacts.add(fact);
            else if (elementLower.contains("separate") && (factLower.contains("separate") || factLower.contains("funds"))) missingFacts.add(fact);
            else if (elementLower.contains("trace") && (factLower.contains("trace") || factLower.contains("source"))) missingFacts.add(fact);
            else if (elementLower.contains("benefit") && factLower.contains("benefit")) missingFacts.add(fact);
            else if (elementLower.contains("offset") && (factLower.contains("offset") || factLower.contains("reimbursement"))) missingFacts.add(fact);
            else if (elementLower.contains("transmutation") && factLower.contains("transmutation")) missingFacts.add(fact);
            else if (elementLower.contains("income") && factLower.contains("income")) missingFacts.add(fact);
            else if (elementLower.contains("ability") && (factLower.contains("income") || factLower.contains("earning"))) missingFacts.add(fact);
        }
        
        return missingFacts.stream().distinct().toList();
    }
    
    /**
     * Assess whether a rule element is supported by facts.
     * Returns a simple assessment level.
     * 
     * @param element Rule element
     * @param supportingFacts Facts supporting the element
     * @param missingFacts Missing facts for the element
     * @return Assessment string
     */
    private String assessRuleElement(
        String element, List<CaseFact> supportingFacts, List<MissingFact> missingFacts
    ) {
        // Simple scoring: more supporting facts and fewer missing facts = stronger support
        int supportScore = supportingFacts.size();
        int missingScore = missingFacts.size();
        
        if (supportScore >= 2 && missingScore == 0) {
            return "Supported (sufficient facts, no gaps identified)";
        } else if (supportScore >= 1 && missingScore <= 1) {
            return "Partially Supported (some facts present, some missing)";
        } else if (missingScore >= 2) {
            return "Weak (significant facts missing)";
        } else {
            return "Unknown (insufficient information)";
        }
    }

    /**
     * Generate opposing argument based on case facts.
     * 
     * @param context Case analysis context
     * @param strengthLevel Strength assessment
     * @return Counterclaim narrative
     */
    private String counterclaim(CaseAnalysisContext context, CaseAnalysisResult.StrengthLevel strengthLevel) {
        List<CaseFact> unfavorableFacts = context.getRelevantFacts().stream()
            .filter(f -> !f.isFavorable())
            .toList();
        
        if (unfavorableFacts.isEmpty()) {
            return "The opposing party has limited factual support for their position.";
        }
        
        StringBuilder counterclaim = new StringBuilder();
        counterclaim.append("The opposing party could point to the following facts: ");
        counterclaim.append(
            unfavorableFacts.stream()
                .limit(2)
                .map(CaseFact::getDescription)
                .collect(Collectors.joining("; "))
        );
        counterclaim.append(". ");
        
        switch (strengthLevel) {
            case VERY_STRONG, STRONG -> 
                counterclaim.append("However, these do not overcome the weight of supporting evidence.");
            case MODERATE -> 
                counterclaim.append("These present legitimate concerns requiring careful consideration.");
            case WEAK, VERY_WEAK -> 
                counterclaim.append("These substantially undermine the claimed position.");
        }
        
        return counterclaim.toString();
    }

    /**
     * Strict fact-quality filter applied BEFORE rule-element assignment.
     * 
     * Rejects snippets if ANY of the following are true:
     * - Length < 20 characters unless clearly meaningful (contains paid/mortgage/payment)
     * - Numeric-only or mostly numeric (> 70% digits/currency)
     * - Table-header patterns without context
     * - Generic form boilerplate ("real and personal $", "check the box", "petitioner:", "respondent:")
     * - OCR garbage with very low alphabetic ratio (< 50%)
     * 
     * Prefers snippets with:
     * - "paid", "mortgage", "payment"
     * - Property addresses or significant length
     * - Explicit who-paid wording
     * 
     * This filter applies BEFORE facts are assigned to rule elements.
     * 
     * @param fact The case fact to evaluate
     * @return true if the fact is strictly high-quality, false otherwise
     */
    private boolean isStrictlyHighQualityFact(CaseFact fact) {
        String desc = fact.getDescription();
        if (desc == null || desc.isEmpty()) {
            logFactFilter(desc, false, "null or empty");
            return false;
        }
        
        String desc_trimmed = desc.trim();
        String descLower = desc_trimmed.toLowerCase();
        
        // Rejection 1: Length check - too short unless meaningful
        if (desc_trimmed.length() < 20) {
            boolean hasMeaningfulKeywords = descLower.contains("paid") || 
                descLower.contains("mortgage") || descLower.contains("payment") ||
                (descLower.contains("$") && (descLower.contains("paid") || descLower.contains("property")));
            if (!hasMeaningfulKeywords) {
                logFactFilter(desc, false, "too short (< 20 chars) without meaningful keywords");
                return false;
            }
        }
        
        // Early rejection: Clear OCR/statement fragments before other checks
        if (isOCRStatementFragment(desc, descLower)) {
            logFactFilter(desc, false, "OCR statement fragment with conditional/warning text");
            return false;
        }
        
        // Pure numeric or mostly numeric
        String noWhitespace = desc_trimmed.replaceAll("\\s+", "");
        long numericChars = noWhitespace.chars()
            .filter(c -> Character.isDigit(c) || c == ',' || c == '.' || c == '$')
            .count();
        double numericRatio = noWhitespace.isEmpty() ? 0 : (double) numericChars / noWhitespace.length();
        if (numericRatio > 0.7) {
            logFactFilter(desc, false, "numeric-only or mostly numeric (" + String.format("%.1f%%", numericRatio * 100) + ")");
            return false;
        }
        
        // Rejection 3: Pure numeric sequences
        if (noWhitespace.matches("^[0-9,.$]+$")) {
            logFactFilter(desc, false, "pure numeric sequence");
            return false;
        }
        
        // Rejection 4: Table-header patterns without context
        if ((descLower.matches("^(date paid|description|principal|interest|escrow).*") &&
             !descLower.contains("mortgage") && !descLower.contains("payment") && 
             !descLower.contains("$")) ||
            descLower.matches("^\\w+:\\s*$")) {
            logFactFilter(desc, false, "isolated table header without context");
            return false;
        }
        
        // Rejection 5: Generic form boilerplate
        if ((descLower.contains("real and personal") && noWhitespace.endsWith("$")) ||
            descLower.contains("check the box") ||
            descLower.startsWith("petitioner:") ||
            descLower.startsWith("respondent:") ||
            (descLower.matches("^[a-z]\\.\\s*$") || (descLower.matches("^\\([a-z]\\).*") && desc_trimmed.length() < 20))) {
            logFactFilter(desc, false, "generic form boilerplate");
            return false;
        }
        
        // Rejection 6: OCR garbage - very low alphabetic ratio
        long alphabeticChars = desc_trimmed.chars()
            .filter(Character::isLetter)
            .count();
        double alphabeticRatio = desc_trimmed.isEmpty() ? 0 : (double) alphabeticChars / desc_trimmed.length();
        if (alphabeticRatio < 0.5) {
            logFactFilter(desc, false, "OCR garbage / broken text (" + String.format("%.1f%%", alphabeticRatio * 100) + " alphabetic)");
            return false;
        }
        
        // Acceptance: Snippets with meaningful payment/property keywords
        if (descLower.contains("paid") || descLower.contains("mortgage") || 
            descLower.contains("payment") || descLower.contains("property")) {
            logFactFilter(desc, true, "contains meaningful legal keywords");
            return true;
        }
        
        // Acceptance: Significant length with reasonable alphabetic content
        if (desc_trimmed.length() >= 20 && alphabeticRatio >= 0.6) {
            logFactFilter(desc, true, "sufficient length and alphabetic content");
            return true;
        }
        
        logFactFilter(desc, false, "failed all acceptance criteria");
        return false;
    }
    
    /**
     * Detect OCR statement fragments with conditional/warning text.
     * Examples: "23 If payment is received after 10/16/2025..."
     * These are common OCR extraction artifacts from mortgage payment statements.
     * 
     * @param desc Original description
     * @param descLower Lowercase description
     * @return true if this appears to be an OCR statement fragment
     */
    private boolean isOCRStatementFragment(String desc, String descLower) {
        // Pattern: digit(s) followed by conditional statement
        // e.g. "23 If payment is received after..."
        if (descLower.matches("^\\d+\\s+if\\s+(payment|the|you|this).*")) {
            return true;
        }
        
        // Pattern: Payment received after date with no other context
        if (descLower.matches(".*if\\s+payment\\s+(is\\s+)?received\\s+after\\s+\\d{1,2}/\\d{1,2}/\\d{4}.*") &&
            desc.length() < 60) {
            return true;
        }
        
        // Pattern: "X If payment..." where X is a document number or OCR artifact
        if (descLower.matches("^[a-z0-9]+\\s+if\\s+payment.*") && 
            !descLower.contains("mortgage") && !descLower.contains("loan")) {
            return true;
        }
        
        // Warning/instruction text that looks like OCR extraction
        if ((descLower.contains("if payment") || descLower.contains("payment received")) &&
            (descLower.contains("after") || descLower.contains("before") || 
             descLower.contains("penalty") || descLower.contains("late")) &&
            desc.length() < 80 && !descLower.contains("mortgage")) {
            // This looks like conditional payment text without substantive content
            return true;
        }
        
        return false;
    }
    
    /**
     * Log fact filtering decisions for debugging purposes.
     * Format: [FACT_FILTER] STATUS | fact_description | reason: explanation
     * 
     * @param factDesc The fact description
     * @param accepted Whether the fact was accepted
     * @param reason The reason for acceptance/rejection
     */
    private void logFactFilter(String factDesc, boolean accepted, String reason) {
        if (logger.isDebugEnabled()) {
            String status = accepted ? "ACCEPTED" : "REJECTED";
            String truncated = factDesc == null ? "null" : 
                (factDesc.length() > 60 ? factDesc.substring(0, 60) + "..." : factDesc);
            logger.debug("[FACT_FILTER] {} | {} | reason: {}", status, truncated, reason);
        }
    }

    /**
     * Filter out low-quality evidence snippets before rendering in application section.
     * 
     * Removes:
     * - Very short fragments (< 15 characters unless clearly meaningful)
     * - Numeric-only or near-numeric fragments (e.g., "23", "100")
     * - OCR garbage / broken text patterns
     * - Boilerplate form fragments with little legal meaning (e.g., "real and personal $")
     * - Incomplete table rows unless they contain meaningful payment information
     * 
     * Prefers:
     * - Complete sentences with proper structure
     * - Mortgage/payment facts with amounts and context
     * - Property addresses, dates, and payment descriptions
     * - Explicit statements about who paid
     * 
     * @param facts List of case facts to filter
     * @return Filtered list of high-quality facts suitable for rendering
     */
    /**
     * Filter high-quality facts with detailed logging.
     * Reports which facts are accepted/rejected at rendering stage and why.
     * 
     * @param facts Facts to filter
     * @return Filtered high-quality facts
     */
    private List<CaseFact> filterHighQualityFactsWithLogging(List<CaseFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return facts;
        }
        
        List<CaseFact> result = new ArrayList<>();
        List<String> rejectedFacts = new ArrayList<>();
        
        for (CaseFact fact : facts) {
            String desc = fact.getDescription();
            if (desc == null || desc.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    rejectedFacts.add("(null/empty) | reason=null_or_empty");
                }
                continue;
            }
            
            // Filter 1: Length check - too short is likely noise
            if (desc.length() < 15) {
                if (desc.contains("$") || desc.toLowerCase().contains("paid") || 
                    desc.toLowerCase().contains("property")) {
                    result.add(fact);
                    if (logger.isDebugEnabled()) {
                        logger.debug("[RENDERING_FILTER] ACCEPTED | {} | reason=short_with_keywords",
                            truncateForLogging(desc));
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        rejectedFacts.add(truncateForLogging(desc) + " | reason=too_short");
                    }
                }
                continue;
            }
            
            // Filter 2: Numeric-only or near-numeric fragments
            String descNoWhitespace = desc.replaceAll("\\s+", "");
            if (descNoWhitespace.matches("^[0-9,.$]+$")) {
                if (logger.isDebugEnabled()) {
                    rejectedFacts.add(truncateForLogging(desc) + " | reason=numeric_only");
                }
                continue;
            }
            
            // Filter 2.5: Multi-line numeric/table fragments
            // Reject: numeric-only or short numeric first line + table headers in later lines
            if (isNumericTableFragment(desc)) {
                if (logger.isDebugEnabled()) {
                    rejectedFacts.add(truncateForLogging(desc) + " | reason=numeric_table_fragment");
                }
                continue;
            }
            
            // Filter 3: OCR garbage patterns (excessive special chars, broken text)
            long specialCharCount = desc.chars()
                .filter(c -> "!@#%^&*~`|<>?/".indexOf(c) >= 0)
                .count();
            if (specialCharCount > desc.length() * 0.2) {
                if (logger.isDebugEnabled()) {
                    rejectedFacts.add(truncateForLogging(desc) + " | reason=ocr_garbage");
                }
                continue;
            }
            
            // Filter 4: Boilerplate form fragments
            String lowerDesc = desc.toLowerCase();
            if ((lowerDesc.contains("real and personal") && desc.matches(".*real and personal\\s*\\$*.*")) ||
                lowerDesc.matches(".*\\b(and|or)\\s*\\$\\s*") ||
                (lowerDesc.matches("^[A-Z]\\. .*") && !lowerDesc.contains("payment") && 
                 !lowerDesc.contains("mortgage"))) {
                if (logger.isDebugEnabled()) {
                    rejectedFacts.add(truncateForLogging(desc) + " | reason=boilerplate_form_text");
                }
                continue;
            }
            
            // Filter 5: Incomplete table rows (unless meaningful payment info)
            if ((lowerDesc.matches("^\\|.*\\|?$") || (desc.contains(":") && desc.length() < 30)) &&
                !lowerDesc.contains("payment") && !lowerDesc.contains("mortgage") && 
                !lowerDesc.contains("paid")) {
                if (logger.isDebugEnabled()) {
                    rejectedFacts.add(truncateForLogging(desc) + " | reason=table_fragment");
                }
                continue;
            }
            
            // Passes all filters - high quality
            result.add(fact);
            if (logger.isDebugEnabled()) {
                logger.debug("[RENDERING_FILTER] ACCEPTED | {} | reason=passes_all_filters",
                    truncateForLogging(desc));
            }
        }
        
        // Log rejected facts as a group
        if (logger.isDebugEnabled() && !rejectedFacts.isEmpty()) {
            StringBuilder rejectedLog = new StringBuilder("[RENDERING_FILTER] Rejected at rendering stage:\n");
            for (String rejected : rejectedFacts) {
                rejectedLog.append("  ✗ ").append(rejected).append("\n");
            }
            logger.debug(rejectedLog.toString());
        }
        
        return result;
    }
    
    private List<CaseFact> filterHighQualityFacts(List<CaseFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return facts;
        }
        
        return facts.stream()
            .filter(fact -> {
                String desc = fact.getDescription();
                if (desc == null || desc.isEmpty()) {
                    return false;
                }
                
                // Filter 1: Length check - too short is likely noise
                // Exception: meaningful short phrases with payment/property info
                if (desc.length() < 15) {
                    // Allow short facts that contain payment/property keywords
                    if (desc.contains("$") || desc.toLowerCase().contains("paid") || 
                        desc.toLowerCase().contains("property")) {
                        return true;
                    }
                    return false;
                }
                
                // Filter 2: Numeric-only or near-numeric fragments
                String descNoWhitespace = desc.replaceAll("\\s+", "");
                if (descNoWhitespace.matches("^[0-9,.$]+$")) {
                    return false;  // Pure numeric/currency like "23" or "1,500"
                }
                
                // Filter 3: OCR garbage patterns (excessive special chars, broken text)
                long specialCharCount = desc.chars()
                    .filter(c -> "!@#%^&*~`|<>?/".indexOf(c) >= 0)
                    .count();
                if (specialCharCount > desc.length() * 0.2) {
                    return false;  // More than 20% special chars = likely OCR garbage
                }
                
                // Filter 4: Boilerplate form fragments
                String lowerDesc = desc.toLowerCase();
                if ((lowerDesc.contains("real and personal") && desc.matches(".*real and personal\\s*\\$*.*")) ||
                    lowerDesc.matches(".*\\b(and|or)\\s*\\$\\s*") ||
                    (lowerDesc.matches("^[A-Z]\\. .*") && !lowerDesc.contains("payment") && 
                     !lowerDesc.contains("mortgage"))) {
                    return false;  // Generic form text with minimal legal meaning
                }
                
                // Filter 5: Incomplete table rows (unless meaningful payment info)
                if ((lowerDesc.matches("^\\|.*\\|?$") || (desc.contains(":") && desc.length() < 30)) &&
                    !lowerDesc.contains("payment") && !lowerDesc.contains("mortgage") && 
                    !lowerDesc.contains("paid")) {
                    return false;  // Table fragment without context
                }
                
                return true;  // Passes all filters - high quality
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Helper to truncate text for logging (max 70 chars).
     */
    private String truncateForLogging(String text) {
        if (text == null) {
            return "(null)";
        }
        return text.length() > 70 ? text.substring(0, 70) + "..." : text;
    }
    
    /**
     * Detect multi-line numeric/table fragments that should be rejected.
     * 
     * Pattern: short numeric line(s) followed by table headers
     * Examples:
     * - "23\n\nMonthly Fees and Payment\nDate Paid | Description | Principal..."
     * - Any text starting with numeric-only line + multiple lines with table structure
     * 
     * @param text The fact text to check
     * @return true if this is a numeric/table fragment that should be rejected
     */
    private boolean isNumericTableFragment(String text) {
        if (text == null || !text.contains("\n")) {
            return false;  // Single-line, not a table fragment
        }
        
        String[] lines = text.split("\\n");
        if (lines.length < 2) {
            return false;  // Need at least 2 lines
        }
        
        // Check first non-empty line
        String firstLine = null;
        int firstLineIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                firstLine = trimmed;
                firstLineIdx = i;
                break;
            }
        }
        
        if (firstLine == null || firstLine.isEmpty()) {
            return false;
        }
        
        // Check if first line is numeric-only or very short numeric
        String noWhiteSpace = firstLine.replaceAll("\\s+", "");
        boolean firstLineIsNumeric = noWhiteSpace.matches("^[0-9,.$]+$");
        if (!firstLineIsNumeric) {
            return false;  // First line must be numeric
        }
        
        // Check if later lines contain table-like patterns
        String lowerText = text.toLowerCase();
        String[] tableHeaders = {
            "monthly fees", "monthly payment", "date paid", "description",
            "principal", "interest", "escrow", "charges", "unapplied",
            "amount paid", "total due", "payment history", "transaction history",
            "posting date", "balance", "payment schedule", "amortization"
        };
        
        int tablePatternCount = 0;
        for (String header : tableHeaders) {
            if (lowerText.contains(header)) {
                tablePatternCount++;
            }
        }
        
        // If first line is numeric AND we find multiple table patterns in later lines, it's likely a table fragment
        if (tablePatternCount >= 2) {
            return true;  // Looks like a numeric/table fragment
        }
        
        // Also check for pipe-separated columns with table-like structure
        if (text.contains("|") && lines.length >= 3) {
            // Count how many lines have pipe separators
            int pipeLineCount = 0;
            for (String line : lines) {
                if (line.contains("|")) {
                    pipeLineCount++;
                }
            }
            if (pipeLineCount >= 2) {
                return true;  // Looks like a table with multiple pipe-separated lines
            }
        }
        
        return false;
    }
    
    /**
     * Filter facts using FactClassifier - exclude NOISY_FACT and UNRELATED_FACT.
     * This is part of the APPLICATION TO RULE path, called before strict filtering
     * and assignment to rule elements.
     * 
     * @param facts Favorable facts to classify and filter
     * @return Facts that are not noisy or unrelated
     */
    private List<CaseFact> filterFactsByClassification(List<CaseFact> facts) {
        List<CaseFact> filtered = new ArrayList<>();
        
        for (CaseFact fact : facts) {
            String description = fact.getDescription();
            if (description == null || description.isEmpty()) {
                continue;
            }
            
            // Classify the fact
            ClassifiedFact classified = factClassifier.classify(description);
            FactCategory category = classified.getCategory();
            
            // Log classification with reason
            logger.debug("[FACT_CLASSIFIER] category={} reason={}", category, classified.getReason());
            
            // Exclude noisy and unrelated facts
            if (category != FactCategory.NOISY_FACT && category != FactCategory.UNRELATED_FACT) {
                filtered.add(fact);
            }
        }
        
        return filtered;
    }
}
