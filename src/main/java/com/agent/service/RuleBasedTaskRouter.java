package com.agent.service;

import com.agent.model.TaskMode;
import com.agent.model.TaskRoutingResult;
import org.springframework.stereotype.Service;

/**
 * Rule-based task router using linguistic heuristics.
 * 
 * Analyzes query content to detect intent and route to appropriate mode:
 * - DRAFTING: imperative/directive verbs (draft, write, create, prepare)
 * - CASE_ANALYSIS: evaluative/predictive language (strength, likely, claim, risk, standard)
 *   OR family law domain keywords (reimbursement, tracing, exclusive use, etc.)
 * - LEGAL_RESEARCH: research/discovery verbs (find, search, identify, locate, cases)
 * - DOCUMENT_QA: query/extraction verbs (what, explain, show, list, summarize)
 * - DEFAULT: DOCUMENT_QA for ambiguous queries
 * 
 * Routing priority (checked in order):
 * 1. DRAFTING (1+ match)
 * 2. CASE_ANALYSIS (1+ match) - includes family law domain keywords
 * 3. LEGAL_RESEARCH (1+ match)
 * 4. DOCUMENT_QA (1+ match)
 * 5. DEFAULT to DOCUMENT_QA (low confidence)
 */
@Service
public class RuleBasedTaskRouter implements TaskRouter {
    
    // Keyword patterns for each mode
    // NOTE: DRAFTING keywords focus on action verbs, not nouns like "document"
    // This avoids false positives when users refer to "the document" generically
    private static final String[] DRAFTING_KEYWORDS = {
        "draft", "write", "create", "prepare", "compose", "generate"
    };
    
    // CASE_ANALYSIS keywords include both evaluative language and family law domain terms
    // Domain keywords (family law): reimbursement, post separation, tracing, exclusive use, etc.
    // These alone are strong indicators of legal analysis queries
    private static final String[] CASE_ANALYSIS_KEYWORDS = {
        // Evaluative/analytical language
        "strength", "likely", "strong", "claim", "valid", "risk", "standard",
        "do i have", "analysis", "evaluate", "assess", "determine", "conclude",
        "based on", "given", "these facts", "position",
        
        // Family law domain keywords - legal analysis topics
        "reimbursement", "post separation", "mortgage reimbursement",
        "tracing", "separate property", "separate property contribution",
        "exclusive use", "occupancy offset", "property characterization",
        "transmutation", "fiduciary duty", "asset disclosure",
        "community property", "community property dispute", "property division",
        "property division dispute"
    };
    
    private static final String[] LEGAL_RESEARCH_KEYWORDS = {
        "find", "search", "identify", "locate", "cases", "precedent", "authority",
        "holding", "rule", "statute", "regulation", "case law", "jurisdiction"
    };
    
    private static final String[] DOCUMENT_QA_KEYWORDS = {
        "what", "explain", "show", "list", "summarize", "describe",
        "say", "states", "mentions", "contains", "reference"
    };

    /**
     * Route query to appropriate task mode using heuristic keyword matching.
     * 
     * @param query The user's query
     * @return TaskRoutingResult with detected mode and confidence
     */
    @Override
    public TaskRoutingResult route(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new TaskRoutingResult(
                TaskMode.DOCUMENT_QA,
                0.5,
                "Empty query - defaulting to DOCUMENT_QA"
            );
        }
        
        String lowerQuery = query.toLowerCase();
        
        // Check each mode in priority order
        // DRAFTING has highest priority (most specific)
        MatchResult draftingMatch = matchKeywords(lowerQuery, DRAFTING_KEYWORDS);
        if (draftingMatch.count >= 1) {
            return new TaskRoutingResult(
                TaskMode.DRAFTING,
                Math.min(0.95, 0.7 + draftingMatch.count * 0.1),
                "Detected drafting/creation intent (matched: " + draftingMatch.keywords + ")"
            );
        }
        
        // CASE_ANALYSIS - evaluative/analytical language or family law domain keywords
        // Lower threshold to 1+ because family law keywords alone are strong indicators
        MatchResult analysisMatch = matchKeywords(lowerQuery, CASE_ANALYSIS_KEYWORDS);
        if (analysisMatch.count >= 1) {
            return new TaskRoutingResult(
                TaskMode.CASE_ANALYSIS,
                Math.min(0.95, 0.7 + analysisMatch.count * 0.1),
                "Detected legal analysis intent (matched: " + analysisMatch.keywords + ")"
            );
        }
        
        // LEGAL_RESEARCH - research/precedent search language
        MatchResult researchMatch = matchKeywords(lowerQuery, LEGAL_RESEARCH_KEYWORDS);
        if (researchMatch.count >= 1) {
            return new TaskRoutingResult(
                TaskMode.LEGAL_RESEARCH,
                Math.min(0.95, 0.7 + researchMatch.count * 0.1),
                "Detected legal research intent (matched: " + researchMatch.keywords + ")"
            );
        }
        
        // DOCUMENT_QA - direct extraction/explanation questions
        MatchResult qaMatch = matchKeywords(lowerQuery, DOCUMENT_QA_KEYWORDS);
        if (qaMatch.count >= 1) {
            return new TaskRoutingResult(
                TaskMode.DOCUMENT_QA,
                Math.min(0.95, 0.7 + qaMatch.count * 0.1),
                "Detected document QA intent (matched: " + qaMatch.keywords + ")"
            );
        }
        
        // Default to DOCUMENT_QA with low confidence
        return new TaskRoutingResult(
            TaskMode.DOCUMENT_QA,
            0.5,
            "No clear intent indicators - defaulting to DOCUMENT_QA"
        );
    }
    
    /**
     * Match query against keyword list and return count and matched keywords.
     * 
     * @param query Lowercased query string
     * @param keywords Keywords to match
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
                matchedKeywords.append("\"" + keyword + "\"");
            }
        }
        
        return new MatchResult(count, matchedKeywords.toString());
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
