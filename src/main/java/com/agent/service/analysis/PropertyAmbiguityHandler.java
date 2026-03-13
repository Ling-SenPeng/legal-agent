package com.agent.service.analysis;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility for property canonicalization and ambiguity handling.
 * 
 * Normalizes property names and generates safe ambiguity responses
 * when multiple properties are detected in evidence but not specified in query.
 */
public class PropertyAmbiguityHandler {
    
    private static final org.slf4j.Logger logger = 
        org.slf4j.LoggerFactory.getLogger(PropertyAmbiguityHandler.class);
    
    // California cities and common property location identifiers
    private static final Set<String> KNOWN_CITIES = Set.of(
        "Newark", "Cupertino", "San Jose", "San Francisco", "Oakland", 
        "Berkeley", "Daly City", "Fremont", "Hayward", "Sunnyvale",
        "Mountain View", "Palo Alto", "San Mateo", "Vallejo", "Concord",
        "Walnut Creek", "Pleasanton", "Livermore", "Tracy", "Modesto",
        "Stockton", "Sacramento", "Folsom", "Elk Grove", "Vacaville",
        "Davis", "Fairfield", "Dixon", "Galt", "Lodi"
    );
    
    /**
     * Canonicalize a list of property identifiers.
     * 
     * Normalizes case, removes duplicates, and merges variations
     * (e.g., "SAN JOSE" + "San Jose" → "San Jose")
     * 
     * @param rawProperties Raw property names from evidence
     * @return Canonical list of property names
     */
    public static List<String> canonicalizeProperties(List<String> rawProperties) {
        if (rawProperties == null || rawProperties.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Normalize and collect canonical forms
        Map<String, String> canonicalMap = new LinkedHashMap<>();
        
        for (String prop : rawProperties) {
            String canonical = normalizePropertyName(prop);
            if (canonical != null && !canonical.isEmpty()) {
                // Use first occurrence as canonical
                canonicalMap.putIfAbsent(canonical.toLowerCase(), canonical);
            }
        }
        
        List<String> result = new ArrayList<>(canonicalMap.values());
        
        if (result.size() != rawProperties.size()) {
            logger.info("[PROPERTY_CANONICALIZATION] raw={} canonical={} deduped={}",
                rawProperties, result, rawProperties.size() - result.size());
        }
        
        return result;
    }
    
    /**
     * Normalize a single property name.
     * 
     * - Trim whitespace
     * - Normalize case to "Title Case"
     * - Handle common variations
     * 
     * @param property Raw property name
     * @return Normalized property name
     */
    private static String normalizePropertyName(String property) {
        if (property == null || property.isBlank()) {
            return null;
        }
        
        String trimmed = property.trim();
        
        // Check if it's a known city name
        for (String city : KNOWN_CITIES) {
            if (trimmed.equalsIgnoreCase(city)) {
                return city;  // Return proper case version
            }
        }
        
        // Handle unknown properties - normalize to Title Case
        String[] words = trimmed.split("\\s+");
        StringBuilder normalized = new StringBuilder();
        
        for (String word : words) {
            if (normalized.length() > 0) {
                normalized.append(" ");
            }
            normalized.append(word.substring(0, 1).toUpperCase())
                     .append(word.substring(1).toLowerCase());
        }
        
        return normalized.toString();
    }
    
    /**
     * Check if a query specifies a property.
     * 
     * Looks for city names, addresses, or loan numbers in the query text.
     * 
     * @param query User's query
     * @param candidateProperties Known properties from evidence
     * @return true if query mentions at least one property
     */
    public static boolean querySpecifiesProperty(String query, List<String> candidateProperties) {
        if (query == null || query.isBlank()) {
            return false;
        }
        
        String queryLower = query.toLowerCase();
        
        // Check if any candidate property is mentioned in query
        for (String property : candidateProperties) {
            if (queryLower.contains(property.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Generate a safe ambiguity response when multiple properties are detected
     * but the query doesn't specify which one.
     * 
     * @param issues Identified legal issues
     * @param canonicalProperties Canonical property names
     * @param recommendedProperty First property to suggest to user
     * @return Safe ambiguity response text
     */
    public static String generateAmbiguityResponse(
        List<String> issues,
        List<String> canonicalProperties,
        String recommendedProperty
    ) {
        StringBuilder response = new StringBuilder();
        
        response.append("=== CASE ANALYSIS REPORT ===\n\n");
        
        // Issue summary
        response.append("ISSUE SUMMARY\n");
        response.append("---\n");
        if (issues.isEmpty()) {
            response.append("No legal issues detected in the query.\n\n");
        } else {
            for (String issue : issues) {
                response.append("- ").append(issue).append("\n");
            }
            response.append("\n");
        }
        
        // Ambiguity notice
        response.append("⚠️  PROPERTY SCOPE AMBIGUITY - ANALYSIS CANNOT PROCEED\n");
        response.append("---\n");
        
        response.append(String.format(
            "Your query does not specify which community property is at issue.\n\n" +
            "Multiple community properties detected in evidence: %s\n\n" +
            "A reimbursement or property analysis requires knowing which property's " +
            "mortgage, deed, or financial records are being analyzed. " +
            "Mixing facts from different properties would produce unreliable results.\n\n",
            String.join(", ", canonicalProperties)
        ));
        
        // Recommendation
        String recommended = recommendedProperty != null && !recommendedProperty.isBlank()
            ? recommendedProperty
            : (canonicalProperties.isEmpty() ? "property name" : canonicalProperties.get(0));
        
        response.append("NEXT STEPS\n");
        response.append("---\n");
        response.append(String.format(
            "Please specify which community property you want analyzed by including the city or address.\n\n" +
            "Example: 'post separation mortgage reimbursement for the %s property'\n" +
            "or: 'post separation mortgage reimbursement %s'\n\n" +
            "Once you specify the property, I can provide a reliable analysis of its reimbursement claim.",
            recommended, recommended
        ));
        
        logger.info("[AMBIGUITY_RESPONSE_RETURNED] properties={} recommended={}",
            canonicalProperties, recommended);
        
        return response.toString();
    }
}
