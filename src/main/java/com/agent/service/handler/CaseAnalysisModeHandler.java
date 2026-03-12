package com.agent.service.handler;

import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.*;
import com.agent.service.TaskModeHandler;
import com.agent.service.RetrievalService;
import com.agent.service.analysis.CaseAnalysisContextBuilder;
import com.agent.service.analysis.CaseAnalysisQueryCleaner;
import com.agent.service.analysis.CaseAnalysisRetrievalQueryBuilder;
import com.agent.service.analysis.CaseIssueExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Handler for CASE_ANALYSIS mode.
 * 
 * Evaluates facts against legal principles to assess claim strength and predict outcomes.
 * 
 * V1 Pipeline:
 * 1. Extract legal issues from query
 * 2. Retrieve relevant case facts using existing retrieval flow
 * 3. Convert evidence chunks into CaseFact objects
 * 4. Build comprehensive CaseAnalysisContext
 * 5. Generate CaseAnalysisResult with strength assessment
 * 6. Format answer with analysis sections
 * 7. Return ModeExecutionResult with metadata
 */
@Component
public class CaseAnalysisModeHandler implements TaskModeHandler {
    private static final Logger logger = LoggerFactory.getLogger(CaseAnalysisModeHandler.class);
    
    private final RetrievalService retrievalService;
    private final CaseAnalysisContextBuilder contextBuilder;
    private final CaseAnalysisQueryCleaner queryCleaner;
    private final CaseAnalysisRetrievalQueryBuilder queryBuilder;
    private final CaseIssueExtractor issueExtractor;

    public CaseAnalysisModeHandler(
        RetrievalService retrievalService,
        CaseAnalysisContextBuilder contextBuilder,
        CaseAnalysisQueryCleaner queryCleaner,
        CaseAnalysisRetrievalQueryBuilder queryBuilder,
        CaseIssueExtractor issueExtractor
    ) {
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.queryCleaner = queryCleaner;
        this.queryBuilder = queryBuilder;
        this.issueExtractor = issueExtractor;
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
            
            // Step 5: Build analysis context (orchestrates fact extraction, missing fact identification)
            logger.debug("[CASE_ANALYSIS] Step 5: Building case analysis context");
            CaseAnalysisContext context = contextBuilder.buildContext(query, mergedEvidenceChunks);
            
            logger.info("[CASE_ANALYSIS] Context built with {} issues and {} facts",
                context.getIdentifiedIssues().size(),
                context.getRelevantFacts().size());
            
            // Step 6: Generate case analysis result with strength assessment
            logger.debug("[CASE_ANALYSIS] Step 6: Generating analysis result");
            CaseAnalysisResult result = generateAnalysisResult(context);
            
            logger.info("[CASE_ANALYSIS] Analysis complete - Strength: {}, Confidence: {}",
                result.getStrengthLevel(),
                String.format("%.2f", result.getConfidenceScore()));
            
            // Step 7: Format answer with analysis sections
            String answer = formatAnalysisAnswer(query, context, result);
            
            // Build metadata
            String metadata = String.format(
                "Mode: CASE_ANALYSIS | Issues: %d | Facts: %d | Strength: %s | Confidence: %.2f%% | Query: %s",
                context.getIdentifiedIssues().size(),
                context.getRelevantFacts().size(),
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
        // Use map to track unique chunks by ID and keep highest similarity
        Map<Long, EvidenceChunk> chunkMap = new HashMap<>();
        
        for (String retrievalQuery : retrievalQueries) {
            logger.debug("[CASE_ANALYSIS] Retrieving evidence for: '{}'", retrievalQuery);
            
            List<EvidenceChunk> queryResults = retrievalService.retrieveEvidence(retrievalQuery, topK);
            logger.debug("[CASE_ANALYSIS] Retrieved {} chunks from query", queryResults.size());
            
            for (EvidenceChunk chunk : queryResults) {
                Long chunkId = chunk.chunkId();
                
                if (chunkMap.containsKey(chunkId)) {
                    // Chunk already exists - keep version with higher similarity
                    EvidenceChunk existing = chunkMap.get(chunkId);
                    double existingSimilarity = existing.similarity() != null ? existing.similarity() : 0.0;
                    double newSimilarity = chunk.similarity() != null ? chunk.similarity() : 0.0;
                    
                    if (newSimilarity > existingSimilarity) {
                        logger.debug("[CASE_ANALYSIS] Updating chunk similarity: {:.2f} → {:.2f}",
                            existingSimilarity, newSimilarity);
                        chunkMap.put(chunkId, chunk);
                    }
                } else {
                    // New unique chunk
                    chunkMap.put(chunkId, chunk);
                }
            }
        }
        
        // Return merged chunks as list (preserving order via LinkedHashMap would be better, but HashMap is fine)
        List<EvidenceChunk> mergedChunks = List.copyOf(chunkMap.values());
        logger.debug("[CASE_ANALYSIS] Total unique chunks after merging: {}", mergedChunks.size());
        
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
        
        // Count favorable vs unfavorable facts
        long favorableFacts = facts.stream()
            .filter(CaseFact::isFavorable)
            .count();
        long unfavorableFacts = facts.stream()
            .filter(f -> !f.isFavorable())
            .count();
        long missingFacts = facts.stream()
            .filter(f -> f.getDescription().startsWith("[NEEDED FACT]"))
            .count();
        
        logger.debug("[CASE_ANALYSIS] Fact analysis - Favorable: {}, Unfavorable: {}, Missing: {}",
            favorableFacts, unfavorableFacts, missingFacts);
        
        // Calculate confidence based on available facts
        double baseFacts = (double) (favorableFacts + unfavorableFacts);
        double factAvailability = baseFacts > 0 ? (baseFacts / (baseFacts + missingFacts)) : 0.5;
        
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
            favorableFacts,
            unfavorableFacts,
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
        long favorable,
        long unfavorable,
        long missing,
        int issueCount
    ) {
        if (issueCount == 0) {
            return CaseAnalysisResult.StrengthLevel.MODERATE;
        }
        
        long total = favorable + unfavorable;
        if (total == 0) {
            return CaseAnalysisResult.StrengthLevel.WEAK;
        }
        
        double favorableRatio = (double) favorable / total;
        double missingRatio = missing / (double) (total + missing);
        
        // Strong case: mostly favorable facts, few missing
        if (favorableRatio > 0.75 && missingRatio < 0.2) {
            return CaseAnalysisResult.StrengthLevel.VERY_STRONG;
        }
        if (favorableRatio > 0.65 && missingRatio < 0.3) {
            return CaseAnalysisResult.StrengthLevel.STRONG;
        }
        
        // Moderate case: mixed facts
        if (favorableRatio >= 0.4 && favorableRatio <= 0.65) {
            return CaseAnalysisResult.StrengthLevel.MODERATE;
        }
        
        // Weak case: mostly unfavorable or many missing
        if (favorableRatio < 0.4 || missingRatio > 0.5) {
            return CaseAnalysisResult.StrengthLevel.WEAK;
        }
        
        if (favorableRatio < 0.25) {
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
        
        // Count facts by type
        List<CaseFact> extractedFacts = context.getRelevantFacts().stream()
            .filter(f -> !f.getDescription().startsWith("[NEEDED FACT]"))
            .toList();
        List<CaseFact> missingFacts = context.getRelevantFacts().stream()
            .filter(f -> f.getDescription().startsWith("[NEEDED FACT]"))
            .toList();
        
        long favorableCount = extractedFacts.stream().filter(CaseFact::isFavorable).count();
        long unfavorableCount = extractedFacts.stream().filter(f -> !f.isFavorable()).count();
        
        narrative.append("Based on the evidence analysis:\n\n");
        narrative.append(String.format(
            "- Identified %d legal issue(s) requiring assessment\n",
            context.getIdentifiedIssues().size()
        ));
        narrative.append(String.format(
            "- Found %d favorable fact(s) supporting the claim\n",
            favorableCount
        ));
        narrative.append(String.format(
            "- Found %d unfavorable fact(s) challenging the claim\n",
            unfavorableCount
        ));
        narrative.append(String.format(
            "- Identified %d missing fact(s) that would strengthen the analysis\n\n",
            missingFacts.size()
        ));
        
        narrative.append("Preliminary Assessment:\n");
        narrative.append(String.format("The claim appears %s based on available evidence. ", 
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
        
        // Recommend collecting missing facts
        List<CaseFact> missingFacts = context.getRelevantFacts().stream()
            .filter(f -> f.getDescription().startsWith("[NEEDED FACT]"))
            .toList();
        
        if (!missingFacts.isEmpty()) {
            recommendations.append("Priority actions to strengthen the case:\n");
            missingFacts.stream()
                .limit(3)
                .forEach(fact -> recommendations.append("- Locate: ")
                    .append(fact.getDescription().replace("[NEEDED FACT] ", ""))
                    .append("\n"));
        }
        
        // Provide strength-specific recommendations
        switch (strengthLevel) {
            case VERY_STRONG, STRONG -> recommendations.append("\nThe position is strong. Consider proceeding with confidence.");
            case MODERATE -> recommendations.append("\nThe position is defensible but mixed. Additional evidence would improve prospects.");
            case WEAK, VERY_WEAK -> recommendations.append("\nThe position is challenged by available evidence. Additional support is needed before proceeding.");
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
        
        answer.append("APPLICABLE LEGAL STANDARDS\n");
        answer.append("---\n");
        answer.append(context.getLegalStandardSummary()).append("\n");
        
        answer.append("APPLICATION SUMMARY\n");
        answer.append("---\n");
        
        // Favorable facts
        List<CaseFact> favorableFacts = context.getRelevantFacts().stream()
            .filter(f -> !f.getDescription().startsWith("[NEEDED FACT]") && f.isFavorable())
            .toList();
        if (!favorableFacts.isEmpty()) {
            answer.append("Supporting Facts:\n");
            favorableFacts.stream()
                .limit(3)
                .forEach(f -> answer.append("- ").append(f.getDescription()).append("\n"));
        }
        
        // Unfavorable facts
        List<CaseFact> unfavorableFacts = context.getRelevantFacts().stream()
            .filter(f -> !f.getDescription().startsWith("[NEEDED FACT]") && !f.isFavorable())
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
        List<CaseFact> missingFacts = context.getRelevantFacts().stream()
            .filter(f -> f.getDescription().startsWith("[NEEDED FACT]"))
            .toList();
        if (missingFacts.isEmpty()) {
            answer.append("All critical facts appear to be documented.\n\n");
        } else {
            for (CaseFact fact : missingFacts) {
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
     * Generate opposing argument based on case facts.
     * 
     * @param context Case analysis context
     * @param strengthLevel Strength assessment
     * @return Counterclaim narrative
     */
    private String counterclaim(CaseAnalysisContext context, CaseAnalysisResult.StrengthLevel strengthLevel) {
        List<CaseFact> unfavorableFacts = context.getRelevantFacts().stream()
            .filter(f -> !f.isFavorable() && !f.getDescription().startsWith("[NEEDED FACT]"))
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
