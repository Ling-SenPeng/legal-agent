package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.CaseFact;
import com.agent.model.analysis.LegalIssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based case fact extractor from evidence chunks.
 * 
 * Identifies factual statements in evidence and converts them to CaseFact objects,
 * with filtering based on relevant issue types.
 */
@Service
public class RuleBasedCaseFactExtractor implements CaseFactExtractor {
    private static final Logger logger = LoggerFactory.getLogger(RuleBasedCaseFactExtractor.class);
    
    // Patterns for detecting factual statements
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "\\b(\\d{1,2}/\\d{1,2}/\\d{4}|january|february|march|april|may|june|july|august|september|october|november|december)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PAYMENT_PATTERN = Pattern.compile(
        "\\$\\d+(?:,\\d{3})*(?:\\.\\d{2})?|payment of|paid|mortgage",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
        "property|home|house|residence|estate|title|deed|down payment",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern OCCUPANCY_PATTERN = Pattern.compile(
        "occupied|resided|lived|occupant|occupancy|residence|household",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract case facts for a specific issue type from evidence chunks.
     * 
     * @param chunks Evidence chunks to analyze
     * @param relevantIssue The issue type to filter facts for
     * @return List of relevant case facts
     */
    @Override
    public List<CaseFact> extractFacts(List<EvidenceChunk> chunks, LegalIssueType relevantIssue) {
        if (chunks == null || chunks.isEmpty()) {
            logger.warn("No chunks provided for fact extraction");
            return List.of();
        }

        List<CaseFact> facts = new ArrayList<>();
        
        for (EvidenceChunk chunk : chunks) {
            facts.addAll(extractFactsFromChunk(chunk, relevantIssue));
        }

        logger.info("Extracted {} facts for issue type {}", facts.size(), relevantIssue);
        return facts;
    }

    /**
     * Extract all facts from evidence chunks with issue inference.
     * 
     * @param chunks Evidence chunks to analyze
     * @return List of all extracted facts
     */
    @Override
    public List<CaseFact> extractAllFacts(List<EvidenceChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<CaseFact> allFacts = new ArrayList<>();
        
        for (EvidenceChunk chunk : chunks) {
            // Extract with null (no filtering by issue)
            allFacts.addAll(extractFactsFromChunk(chunk, null));
        }

        logger.info("Extracted {} total facts from {} chunks", allFacts.size(), chunks.size());
        return allFacts;
    }

    /**
     * Extract facts from a single evidence chunk.
     * 
     * @param chunk The evidence chunk to analyze
     * @param relevantIssue Optional issue filter
     * @return List of facts extracted from the chunk
     */
    private List<CaseFact> extractFactsFromChunk(EvidenceChunk chunk, LegalIssueType relevantIssue) {
        List<CaseFact> facts = new ArrayList<>();
        String text = chunk.text();
        
        if (text == null || text.trim().isEmpty()) {
            return facts;
        }

        // Extract sentences containing factual information
        String[] sentences = text.split("\\.|\\?|!");
        
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            
            if (trimmed.isEmpty() || trimmed.length() < 10) {
                continue;
            }

            // Check if sentence contains relevant patterns
            if (containsFactualContent(trimmed)) {
                double confidence = calculateConfidence(trimmed);
                
                CaseFact fact = new CaseFact(
                    trimmed,
                    true,  // Assume favorable initially (can be refined)
                    formatSourceReference(chunk),
                    relevantIssue != null ? relevantIssue : inferIssueFromFact(trimmed)
                );
                
                facts.add(fact);
            }
        }

        return facts;
    }

    /**
     * Check if a sentence contains factual content worth extracting.
     * 
     * @param sentence The sentence to evaluate
     * @return true if sentence contains factual information
     */
    private boolean containsFactualContent(String sentence) {
        String lower = sentence.toLowerCase();
        
        // Look for dates, amounts, names, locations
        return DATE_PATTERN.matcher(lower).find() ||
               PAYMENT_PATTERN.matcher(lower).find() ||
               PROPERTY_PATTERN.matcher(lower).find() ||
               OCCUPANCY_PATTERN.matcher(lower).find() ||
               containsPronounOrAgent(lower);
    }

    /**
     * Check if sentence contains agents (subjects engaging in actions).
     * 
     * @param lower Lowercase sentence
     * @return true if contains subject/agent
     */
    private boolean containsPronounOrAgent(String lower) {
        return lower.contains(" i ") || lower.contains(" we ") ||
               lower.contains(" he ") || lower.contains(" she ") ||
               lower.contains(" they ") || lower.contains(" plaintiff ") ||
               lower.contains(" defendant ") || lower.contains(" respondent ");
    }

    /**
     * Calculate confidence score for extracted fact.
     * 
     * @param sentence The fact sentence
     * @return Confidence score [0.0-1.0]
     */
    private double calculateConfidence(String sentence) {
        double confidence = 0.5;
        
        // Increase confidence for specific details
        if (DATE_PATTERN.matcher(sentence).find()) confidence += 0.2;
        if (PAYMENT_PATTERN.matcher(sentence).find()) confidence += 0.2;
        if (sentence.length() > 100) confidence += 0.1;
        
        return Math.min(0.95, confidence);
    }

    /**
     * Infer relevant issue type from fact content.
     * 
     * @param fact The fact text
     * @return Inferred LegalIssueType
     */
    private LegalIssueType inferIssueFromFact(String fact) {
        String lower = fact.toLowerCase();
        
        if (lower.contains("mortgage") || lower.contains("payment") || lower.contains("reimbursement")) {
            return LegalIssueType.REIMBURSEMENT;
        } else if (lower.contains("support") || lower.contains("maintenance") || lower.contains("alimony")) {
            return LegalIssueType.SUPPORT;
        } else if (lower.contains("property") || lower.contains("characteriz")) {
            return LegalIssueType.PROPERTY_CHARACTERIZATION;
        } else if (lower.contains("traced") || lower.contains("source") || lower.contains("down payment")) {
            return LegalIssueType.TRACING;
        } else if (lower.contains("occupied") || lower.contains("occupancy") || lower.contains("resided")) {
            return LegalIssueType.EXCLUSIVE_USE;
        } else if (lower.contains("custody") || lower.contains("visitation") || lower.contains("child")) {
            return LegalIssueType.CUSTODY;
        } else if (lower.contains("order") || lower.contains("restrain") || lower.contains("dvro")) {
            return LegalIssueType.RESTRAINING_ORDER;
        }
        
        return LegalIssueType.OTHER;
    }

    /**
     * Format source reference from evidence chunk.
     * 
     * @param chunk The evidence chunk
     * @return Formatted reference string
     */
    private String formatSourceReference(EvidenceChunk chunk) {
        if (chunk.citations() != null && !chunk.citations().isEmpty()) {
            return chunk.citations();
        }
        
        return String.format("[doc=%d chunk=%d p=%d]", 
            chunk.docId(), 
            chunk.chunkId(), 
            chunk.pageNo());
    }
}
