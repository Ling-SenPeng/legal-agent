package com.agent.service.handler;

import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.EvidenceChunk;
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

    public CaseAnalysisModeHandler(
        RetrievalService retrievalService,
        CaseAnalysisContextBuilder contextBuilder,
        CaseAnalysisQueryCleaner queryCleaner,
        CaseAnalysisRetrievalQueryBuilder queryBuilder,
        CaseIssueExtractor issueExtractor,
        IssueAuthorityRetrievalStrategy authorityQueryBuilder,
        AuthorityRetrievalService authorityRetrievalService,
        AuthoritySummarizer authoritySummarizer
    ) {
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.queryCleaner = queryCleaner;
        this.queryBuilder = queryBuilder;
        this.issueExtractor = issueExtractor;
        this.authorityQueryBuilder = authorityQueryBuilder;
        this.authorityRetrievalService = authorityRetrievalService;
        this.authoritySummarizer = authoritySummarizer;
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
        }
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
        List<LegalAuthority> finalRenderedAuthorities = extractAndRankFinalAuthorities(context);
        
        // NEW: LEGAL RULE section integrated from final rendered authorities only
        answer.append("LEGAL RULE\n");
        answer.append("---\n");
        appendLegalRuleFromFinalAuthorities(answer, context, finalRenderedAuthorities);
        answer.append("\n");
        
        // Collect all unique authorities for deduplication across sections
        Set<String> renderedAuthorityIds = new LinkedHashSet<>();
        
        // RELEVANT AUTHORITIES section with final authorities
        answer.append("RELEVANT AUTHORITIES\n");
        answer.append("---\n");
        appendRelevantAuthoritiesSectionWithFinal(answer, finalRenderedAuthorities, renderedAuthorityIds);
        
        answer.append("APPLICATION SUMMARY\n");
        answer.append("---\n");
        
        // Favorable facts
        List<CaseFact> favorableFacts = context.getRelevantFacts().stream()
            .filter(CaseFact::isFavorable)
            .toList();
        if (!favorableFacts.isEmpty()) {
            answer.append("Supporting Facts:\n");
            favorableFacts.stream()
                .limit(3)
                .forEach(f -> answer.append("- ").append(f.getDescription()).append("\n"));
        }
        
        // Unfavorable facts
        List<CaseFact> unfavorableFacts = context.getRelevantFacts().stream()
            .filter(f -> !f.isFavorable())
            .toList();
        if (!unfavorableFacts.isEmpty()) {
            answer.append("\nChallenging Facts:\n");
            unfavorableFacts.stream()
                .limit(3)
                .forEach(f -> answer.append("- ").append(f.getDescription()).append("\n"));
        }
        answer.append("\n");
        
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
        
        for (AuthoritySummary summary : context.getAuthoritySummaries()) {
            for (LegalAuthority auth : summary.getAuthorities()) {
                // Deduplicate by authorityId
                if (!authorityMap.containsKey(auth.getAuthorityId())) {
                    authorityMap.put(auth.getAuthorityId(), auth);
                }
            }
        }
        
        List<LegalAuthority> uniqueAuthorities = new ArrayList<>(authorityMap.values());
        
        // Re-rank authorities by relevance to the identified issues
        if (!uniqueAuthorities.isEmpty() && !context.getIdentifiedIssues().isEmpty()) {
            CaseIssue primaryIssue = context.getIdentifiedIssues().get(0);
            uniqueAuthorities = rankAuthoritiesForIssue(primaryIssue, uniqueAuthorities);
        } else {
            // Fallback: sort by citation if no issues
            uniqueAuthorities.sort((a, b) -> a.getCitation().compareTo(b.getCitation()));
        }
        
        // Return top 2 (or as many as available if less than 2)
        return uniqueAuthorities.stream()
            .limit(2)
            .toList();
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
        if (finalAuthorities.isEmpty()) {
            answer.append("No specific legal authorities retrieved for analysis.\n");
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
                        
                        // Filter the rule to only reference final authorities
                        String filteredRule = filterRuleToFinalAuthorities(originalRule, finalAuthorities);
                        ruleText.append(filteredRule);
                        break;
                    }
                }
            }
            
            // If no matching summary or rule is empty, create a simple rule from authority titles
            if (ruleText.length() == 0) {
                ruleText.append("Based on ");
                for (int i = 0; i < citations.size(); i++) {
                    if (i > 0) ruleText.append(" and ");
                    ruleText.append(citations.get(i));
                }
                ruleText.append(", legal principles apply to this issue.\n");
            }
            
            answer.append(ruleText.toString());
        }
    }

    /**
     * Filter rule text to only mention authorities that are in the final rendered list.
     * Removes references to authorities that were dropped during ranking.
     * 
     * @param originalRule Original rule text from authority summary
     * @param finalAuthorities Final authorities to keep references to
     * @return Filtered rule text
     */
    private String filterRuleToFinalAuthorities(String originalRule, List<LegalAuthority> finalAuthorities) {
        if (finalAuthorities.isEmpty() || originalRule == null) {
            return originalRule;
        }
        
        // Collect titles and citations of final authorities
        Set<String> finalTitles = new HashSet<>();
        Set<String> finalCitations = new HashSet<>();
        
        for (LegalAuthority auth : finalAuthorities) {
            finalTitles.add(auth.getTitle().toLowerCase());
            finalCitations.add(auth.getCitation().toLowerCase());
            // Also add just the name for case references like "Marriage of Epstein"
            String[] parts = auth.getTitle().split(" ");
            if (parts.length > 0) {
                finalTitles.add(parts[parts.length - 1].toLowerCase());
            }
        }
        
        // The original rule should already mention the final authorities
        // If not, just return it as-is (the summary was generated with different authorities)
        String ruleLower = originalRule.toLowerCase();
        boolean mentionsFinalAuthority = finalTitles.stream()
            .anyMatch(ruleLower::contains) ||
            finalCitations.stream()
            .anyMatch(ruleLower::contains);
        
        if (mentionsFinalAuthority) {
            return originalRule;
        } else {
            // Rule doesn't match final authorities - create new rule from scratch
            return createRuleFromFinalAuthorities(finalAuthorities);
        }
    }

    /**
     * Create a simple rule text from the final authorities.
     * Used when the original rule doesn't match the final rendered authorities.
     * 
     * @param finalAuthorities Final authorities
     * @return Generated rule text
     */
    private String createRuleFromFinalAuthorities(List<LegalAuthority> finalAuthorities) {
        if (finalAuthorities.isEmpty()) {
            return "No specific legal rule available.\n";
        }
        
        // Just create a generic rule statement without repeating authority names
        // Authority names will be shown in the RELEVANT AUTHORITIES section
        StringBuilder rule = new StringBuilder();
        
        // Check authority types to create appropriate rule text
        boolean hasStatute = finalAuthorities.stream().anyMatch(a -> a.getAuthorityType() == AuthorityType.STATUTE);
        boolean hasCaseLaw = finalAuthorities.stream().anyMatch(a -> a.getAuthorityType() == AuthorityType.CASE_LAW);
        
        rule.append("Legal principles from relevant ");
        
        if (hasStatute && hasCaseLaw) {
            rule.append("statutes and case law ");
        } else if (hasStatute) {
            rule.append("statutory law ");
        } else if (hasCaseLaw) {
            rule.append("case law ");
        } else {
            rule.append("authorities ");
        }
        
        rule.append("apply to this issue. Consult the cited authorities for detailed rules and applicable requirements.\n");
        
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
}
