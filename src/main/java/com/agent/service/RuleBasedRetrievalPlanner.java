package com.agent.service;

import com.agent.model.RetrievalPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule-based implementation of RetrievalPlanner.
 * Converts user queries into optimized retrieval plans using deterministic rules.
 */
@Service
public class RuleBasedRetrievalPlanner implements RetrievalPlanner {
    private static final Logger logger = LoggerFactory.getLogger(RuleBasedRetrievalPlanner.class);

    // Instruction words to remove from queries
    private static final Set<String> INSTRUCTION_WORDS = Set.of(
            "list", "list all", "show me", "show", "summarize", "extract", "find", 
            "mentioned in the document", "mentioned", "in the document", "all", "the"
    );

    // Legal acronym expansion mapping
    private static final Map<String, List<String>> LEGAL_ACRONYMS = new LinkedHashMap<>();
    static {
        LEGAL_ACRONYMS.put("TRO", List.of("TRO", "temporary restraining order", "restraining order"));
        LEGAL_ACRONYMS.put("RFO", List.of("RFO", "request for order"));
        LEGAL_ACRONYMS.put("DV", List.of("DV", "domestic violence"));
        LEGAL_ACRONYMS.put("DVRO", List.of("DVRO", "domestic violence restraining order"));
        LEGAL_ACRONYMS.put("OCR", List.of("OCR", "order to show cause"));
        LEGAL_ACRONYMS.put("UCCJEA", List.of("UCCJEA", "Uniform Child Custody Jurisdiction and Enforcement Act"));
        LEGAL_ACRONYMS.put("FCPA", List.of("FCPA", "Family Court Act"));
    }

    @Override
    public RetrievalPlan plan(String userQuery) {
        logger.debug("DEBUG: Creating retrieval plan for query: {}", userQuery);
        
        String originalQuery = userQuery;
        
        // Step 1: Remove instruction words
        String cleanedQuery = removeInstructionWords(userQuery);
        logger.debug("DEBUG: After removing instruction words: {}", cleanedQuery);
        
        // Step 2: Detect intent
        String intent = detectIntent(originalQuery);
        logger.debug("DEBUG: Detected intent: {}", intent);
        
        // Step 3: Extract entities and expand acronyms
        List<String> entities = extractAndExpandEntities(cleanedQuery);
        logger.debug("DEBUG: Extracted entities: {}", entities);
        
        // Step 4: Build keyword queries (quoted phrases)
        List<String> keywordQueries = buildKeywordQueries(entities);
        logger.debug("DEBUG: Keyword queries: {}", keywordQueries);
        
        // Step 5: Build vector query
        String vectorQuery = buildVectorQuery(entities);
        logger.debug("DEBUG: Vector query: {}", vectorQuery);
        
        // Step 6: Determine output format based on intent
        String outputFormat = determineOutputFormat(intent);
        logger.debug("DEBUG: Output format: {}", outputFormat);
        
        // Step 7: Create answer instruction
        String answerInstruction = createAnswerInstruction(intent, cleanedQuery);
        logger.debug("DEBUG: Answer instruction: {}", answerInstruction);
        
        RetrievalPlan plan = new RetrievalPlan(
                originalQuery,
                cleanedQuery,
                intent,
                entities,
                keywordQueries,
                vectorQuery,
                answerInstruction,
                outputFormat
        );
        
        logger.info("Generated retrieval plan - intent: {}, keyword queries: {}, vector query: {}", 
                intent, keywordQueries.size(), vectorQuery);
        
        return plan;
    }

    /**
     * Removes instruction words from the query.
     */
    private String removeInstructionWords(String query) {
        String result = query.toLowerCase();
        
        // Sort by length descending to match longer phrases first
        List<String> sortedWords = INSTRUCTION_WORDS.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());
        
        for (String word : sortedWords) {
            result = result.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b\\s*", "");
        }
        
        // Clean up extra spaces
        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }

    /**
     * Detects the intent of the query.
     */
    private String detectIntent(String query) {
        String lowerQuery = query.toLowerCase();
        
        if (lowerQuery.contains("list") || lowerQuery.contains("events") || 
            lowerQuery.contains("all ") || lowerQuery.contains("mentions")) {
            return "extract_events";
        } else if (lowerQuery.contains("summarize") || lowerQuery.contains("summary")) {
            return "summarize";
        } else if (lowerQuery.contains("compare") || lowerQuery.contains("comparison")) {
            return "compare";
        } else if (lowerQuery.contains("count") || lowerQuery.contains("how many")) {
            return "count";
        } else if (lowerQuery.contains("timeline") || lowerQuery.contains("chronological") ||
                   lowerQuery.contains("date") || lowerQuery.contains("when")) {
            return "timeline";
        }
        
        return "find_facts";  // default intent
    }

    /**
     * Extracts entities from query and expands legal acronyms.
     */
    private List<String> extractAndExpandEntities(String query) {
        List<String> entities = new ArrayList<>();
        
        // First, identify and expand acronyms
        for (Map.Entry<String, List<String>> entry : LEGAL_ACRONYMS.entrySet()) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", 
                    Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(query).find()) {
                entities.addAll(entry.getValue());
                logger.debug("DEBUG: Expanded acronym {} to {}", entry.getKey(), entry.getValue());
            }
        }
        
        // Extract capitalized words or phrases (potential entities)
        Pattern entityPattern = Pattern.compile("\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*\\b");
        Matcher matcher = entityPattern.matcher(query);
        while (matcher.find()) {
            String entity = matcher.group();
            if (!entities.contains(entity) && !isCommonWord(entity)) {
                entities.add(entity);
            }
        }
        
        // If no entities found, extract important words (nouns/multi-word phrases)
        if (entities.isEmpty()) {
            String[] words = query.split("\\s+");
            for (String word : words) {
                if (word.length() > 3 && !isStopWord(word.toLowerCase())) {
                    if (!entities.contains(word)) {
                        entities.add(word);
                    }
                }
            }
        }
        
        return entities;
    }

    /**
     * Builds keyword queries with quoted phrases.
     */
    private List<String> buildKeywordQueries(List<String> entities) {
        return entities.stream()
                .map(entity -> "\"" + entity + "\"")
                .collect(Collectors.toList());
    }

    /**
     * Builds a concise vector query from entities.
     */
    private String buildVectorQuery(List<String> entities) {
        return String.join(" ", entities);
    }

    /**
     * Determines output format based on intent.
     */
    private String determineOutputFormat(String intent) {
        return switch (intent) {
            case "extract_events" -> "timeline";
            case "summarize" -> "narrative";
            case "compare" -> "comparison_table";
            case "count" -> "statistics";
            case "timeline" -> "timeline";
            default -> "narrative";
        };
    }

    /**
     * Creates a detailed answer instruction based on intent and query.
     */
    private String createAnswerInstruction(String intent, String cleanedQuery) {
        return switch (intent) {
            case "extract_events" -> "Extract and list all events in chronological order: " + cleanedQuery;
            case "summarize" -> "Provide a concise summary: " + cleanedQuery;
            case "compare" -> "Compare and contrast: " + cleanedQuery;
            case "count" -> "Count occurrences and provide statistics: " + cleanedQuery;
            case "timeline" -> "Create a timeline of events: " + cleanedQuery;
            default -> "Find and explain relevant facts about: " + cleanedQuery;
        };
    }

    /**
     * Checks if a word is a common English word.
     */
    private boolean isCommonWord(String word) {
        Set<String> commonWords = Set.of(
                "The", "A", "An", "And", "Or", "But", "In", "On", "At", "To", "From",
                "For", "With", "By", "About", "Is", "Are", "Was", "Were"
        );
        return commonWords.contains(word);
    }

    /**
     * Checks if a word is a stop word.
     */
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "from",
                "for", "with", "by", "about", "is", "are", "was", "were", "of", "be"
        );
        return stopWords.contains(word);
    }
}
