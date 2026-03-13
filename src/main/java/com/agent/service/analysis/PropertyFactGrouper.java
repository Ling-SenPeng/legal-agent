package com.agent.service.analysis;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Groups PropertyAttributedFacts by property for analysis.
 * 
 * Prevents cross-property contamination in legal reasoning by keeping
 * facts about different properties separate.
 */
public class PropertyFactGrouper {
    
    /**
     * Group facts by property key.
     * 
     * @param facts Facts with property attribution
     * @return Map of propertyKey → List of facts for that property
     */
    public static Map<String, List<PropertyAttributedFact>> groupFactsByProperty(
        List<PropertyAttributedFact> facts
    ) {
        return facts.stream()
            .collect(Collectors.groupingBy(PropertyAttributedFact::getPropertyKey));
    }
    
    /**
     * Get groups organized by property city name (human-readable).
     * 
     * @param facts Facts with property attribution
     * @return Map of propertyDisplay → List of facts for that property
     */
    public static Map<String, List<PropertyAttributedFact>> groupFactsByPropertyDisplay(
        List<PropertyAttributedFact> facts
    ) {
        return facts.stream()
            .collect(Collectors.groupingBy(PropertyAttributedFact::getPropertyDisplay,
                LinkedHashMap::new,  // Preserve insertion order
                Collectors.toList()));
    }
    
    /**
     * Get unattributed facts (those without property information).
     * 
     * @param facts Facts with property attribution
     * @return Facts that lack property information
     */
    public static List<PropertyAttributedFact> getUnattributedFacts(
        List<PropertyAttributedFact> facts
    ) {
        return facts.stream()
            .filter(f -> !f.hasPropertyAttribution())
            .collect(Collectors.toList());
    }
    
    /**
     * Filter facts to only those from a specific property.
     * 
     * @param facts Facts to filter
     * @param propertyKey Property key to match (e.g., "newark", "san jose")
     * @return Facts matching the property key
     */
    public static List<PropertyAttributedFact> filterByProperty(
        List<PropertyAttributedFact> facts,
        String propertyKey
    ) {
        String normalized = propertyKey.toLowerCase();
        return facts.stream()
            .filter(f -> normalized.equals(f.getPropertyKey()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get a summary of facts grouped by property.
     * 
     * Useful for logging and debugging fact contamination issues.
     * 
     * @param facts Facts to summarize
     * @return Summary string showing fact count by property
     */
    public static String summarizeFactsByProperty(List<PropertyAttributedFact> facts) {
        Map<String, Long> summary = facts.stream()
            .collect(Collectors.groupingBy(
                PropertyAttributedFact::getPropertyDisplay,
                Collectors.counting()
            ));
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : summary.entrySet()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" facts");
        }
        return sb.toString();
    }
}
