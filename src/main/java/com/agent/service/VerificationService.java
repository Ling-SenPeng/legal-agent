package com.agent.service;

import com.agent.config.AgentProperties;
import com.agent.model.VerificationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for verifying that all factual claims in the drafted answer have proper citations.
 * MVP implementation: parses lines, detects factual claims, and enforces citation presence.
 */
@Service
public class VerificationService {
    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);
    
    // Regex to detect citation tokens
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[CIT\\s+doc=\\d+\\s+chunk=\\d+\\s+p=\\d+-\\d+\\]");
    
    private final AgentProperties agentProperties;

    public VerificationService(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * Verify that all factual claims have proper citations.
     * 
     * @param draftedAnswer The answer to verify
     * @return Verification report with results
     */
    public VerificationReport verify(String draftedAnswer) {
        logger.info("Verifying citations in drafted answer");

        List<String> missingCitationLines = new ArrayList<>();
        String[] lines = draftedAnswer.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            
            // Skip empty lines and section headers
            if (trimmed.isEmpty() || trimmed.endsWith(":")) {
                continue;
            }

            // Check if this looks like a factual claim
            if (isFunctionalClaim(trimmed)) {
                // Check if it has a citation
                if (!hasCitation(trimmed)) {
                    logger.debug("Missing citation for line: {}", trimmed);
                    missingCitationLines.add(trimmed);
                }
            }
        }

        boolean passed = missingCitationLines.isEmpty();
        String notes = passed 
            ? "All factual claims have proper citations." 
            : "Found " + missingCitationLines.size() + " lines missing citations.";

        logger.info("Verification result: passed={}, missingLines={}", passed, missingCitationLines.size());

        return new VerificationReport(passed, missingCitationLines, notes);
    }

    /**
     * Repair the drafted answer by rewriting lines without citations.
     * Makes an LLM call to add citations or downgrade to "Needs evidence".
     */
    public String repairAnswer(String draftedAnswer, String question, List<String> missingCitationLines, OpenAiClient openAiClient) {
        logger.info("Attempting to repair answer with {} missing citations", missingCitationLines.size());

        String repairPrompt = buildRepairPrompt(draftedAnswer, question, missingCitationLines);
        String repaired = openAiClient.chatCompletion(
            buildRepairSystemPrompt(),
            repairPrompt
        );

        logger.debug("Repaired answer generated, length: {}", repaired.length());
        return repaired;
    }

    /**
     * Check if a line is a factual claim (contains keywords or numbers/dates).
     */
    private boolean isFunctionalClaim(String line) {
        // Skip generic phrases
        if (line.contains("Based on") || line.contains("Evidence shows") || 
            line.contains("According to") || line.contains("Needs evidence")) {
            return false;
        }

        // Look for factual indicators
        String factualKeywords = agentProperties.getVerification().getFactualKeywords();
        if (factualKeywords != null && !factualKeywords.isEmpty()) {
            for (String keyword : factualKeywords.split(",")) {
                if (line.toLowerCase().contains(keyword.trim().toLowerCase())) {
                    return true;
                }
            }
        }

        // Check for dates, numbers, or specific named entities
        return line.matches(".*\\b\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}\\b.*")  // Dates
            || line.matches(".*\\$\\d+.*")                                   // Money
            || line.matches(".*\\b[A-Z][A-Za-z]+\\s+[A-Z][a-z]+\\b.*");    // Names (capitalized)
    }

    /**
     * Check if a line contains a citation token.
     */
    private boolean hasCitation(String line) {
        Matcher matcher = CITATION_PATTERN.matcher(line);
        return matcher.find();
    }

    /**
     * Build system prompt for the repair step.
     */
    private String buildRepairSystemPrompt() {
        return """
            You are a legal document analysis assistant refining citations.
            
            Your task is to revise the given answer by ONLY modifying the lines that lack citations.
            For each line without a citation:
            1. If you can infer which evidence it came from, add the citation token.
            2. If you cannot determine the source, rewrite it as: "Needs evidence: <claim>"
            
            Do NOT modify lines that already have proper citations.
            Do NOT add new information beyond what was discussed.
            Return only the corrected answer without explanation.
            """;
    }

    /**
     * Build the repair prompt with the specific lines to fix.
     */
    private String buildRepairPrompt(String draftedAnswer, String question, List<String> missingCitationLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Original answer that needs repair:\n\n");
        sb.append(draftedAnswer).append("\n\n");
        
        sb.append("Lines that are missing citations (fix these):\n");
        for (String line : missingCitationLines) {
            sb.append("- ").append(line).append("\n");
        }
        
        sb.append("\nRevised answer with proper citations:\n");
        return sb.toString();
    }
}
