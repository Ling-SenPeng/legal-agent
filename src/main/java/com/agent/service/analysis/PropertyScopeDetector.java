package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether a query explicitly identifies a community property.
 * 
 * Analyzes both the query text and available evidence to determine:
 * - If query specifies a property (city, address, loan number, etc.)
 * - How many candidate properties exist in the evidence
 * - Whether property scope is UNAMBIGUOUS or AMBIGUOUS
 * 
 * This prevents cross-property fact mixing when analyzing reimbursement claims.
 */
@Service
public class PropertyScopeDetector {
    private static final Logger logger = LoggerFactory.getLogger(PropertyScopeDetector.class);
    
    // Common California city names for property identification
    private static final Set<String> CALIFORNIA_CITIES = Set.of(
        "Newark", "Cupertino", "San Jose", "San Francisco", "Oakland", 
        "Berkeley", "Daly City", "Fremont", "Hayward", "Sunnyvale",
        "Mountain View", "Palo Alto", "San Mateo", "Vallejo", "Concord",
        "Walnut Creek", "Pleasanton", "Livermore", "Tracy", "Modesto",
        "Stockton", "Sacramento", "Folsom", "Elk Grove", "Vacaville",
        "Davis", "Fairfield", "Dixon", "Galt", "Lodi"
    );
    
    // Patterns for property identification
    private static final Pattern CITY_PATTERN = Pattern.compile(
        "\\b(" + String.join("|", CALIFORNIA_CITIES) + ")\\b", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern LOAN_PATTERN = Pattern.compile(
        "\\b(?:loan|lender|account)[\\s#:]*['\"]?([0-9]{8,})['\"]?\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
        "\\d+\\s+[A-Za-z]+\\s+(?:Street|Street|Ave|Dr|Rd|Ln|Ct|Blvd|Way|Way|Cir|Circle|Place|Pl|Court|Drive)",
        Pattern.CASE_INSENSITIVE
    );
    
    public enum PropertyScope {
        UNAMBIGUOUS,  // Query clearly identifies one property
        AMBIGUOUS     // Multiple properties in evidence, query doesn't specify which
    }
    
    /**
     * Result of property scope detection.
     */
    public static class PropertyScopeResult {
        public final PropertyScope scope;
        public final String identifiedProperty;  // null if AMBIGUOUS
        public final List<String> candidateProperties;
        public final List<String> canonicalProperties;  // Normalized duplicates removed
        public final String detectionReason;
        public final boolean querySpecifiedProperty;  // true if query explicitly mentions a property
        
        public PropertyScopeResult(PropertyScope scope, String identifiedProperty, 
                                   List<String> candidateProperties, String reason) {
            this(scope, identifiedProperty, candidateProperties, reason, false);
        }
        
        public PropertyScopeResult(PropertyScope scope, String identifiedProperty, 
                                   List<String> candidateProperties, String reason,
                                   boolean querySpecifiedProperty) {
            this.scope = scope;
            this.identifiedProperty = identifiedProperty;
            this.candidateProperties = new ArrayList<>(candidateProperties);
            this.canonicalProperties = com.agent.service.analysis.PropertyAmbiguityHandler
                .canonicalizeProperties(candidateProperties);
            this.detectionReason = reason;
            this.querySpecifiedProperty = querySpecifiedProperty;
        }
        
        @Override
        public String toString() {
            if (scope == PropertyScope.UNAMBIGUOUS) {
                return String.format("UNAMBIGUOUS: %s (%s)", identifiedProperty, detectionReason);
            } else {
                return String.format("AMBIGUOUS: candidates=%s (%s)", candidateProperties, detectionReason);
            }
        }
    }
    
    /**
     * Detect property scope from query and evidence.
     * 
     * @param query The user's query
     * @param evidenceChunks Retrieved evidence chunks
     * @return PropertyScopeResult indicating scope and identified properties
     */
    public PropertyScopeResult detectPropertyScope(String query, List<EvidenceChunk> evidenceChunks) {
        logger.debug("[PROPERTY_SCOPE_DETECTOR] Analyzing query: {}", query);
        
        // Step 1: Look for explicit property identifiers in query
        String queryLower = query.toLowerCase();
        String queryCity = extractCityFromText(query);
        String queryLoanNumber = extractLoanNumberFromText(query);
        
        // Step 2: Extract all candidate properties from evidence
        Set<String> candidateProperties = extractCandidateProperties(evidenceChunks);
        logger.debug("[PROPERTY_SCOPE_DETECTED] Found {} candidate properties in evidence: {}",
            candidateProperties.size(), candidateProperties);
        
        // Step 3: Determine scope
        if (queryCity != null) {
            logger.debug("[PROPERTY_SCOPE_DETECTED] Query specifies city: {}", queryCity);
            logger.info("[PROPERTY_SCOPE_DETECTED] scope=UNAMBIGUOUS property={} reason=city_in_query", queryCity);
            return new PropertyScopeResult(
                PropertyScope.UNAMBIGUOUS,
                queryCity,
                new ArrayList<>(candidateProperties),
                "City '" + queryCity + "' found in query",
                true  // Query specified property
            );
        }
        
        if (queryLoanNumber != null) {
            logger.debug("[PROPERTY_SCOPE_DETECTED] Query specifies loan number: {}", queryLoanNumber);
            logger.info("[PROPERTY_SCOPE_DETECTED] scope=UNAMBIGUOUS loan={} reason=loan_in_query", queryLoanNumber);
            return new PropertyScopeResult(
                PropertyScope.UNAMBIGUOUS,
                "Loan#" + queryLoanNumber,
                new ArrayList<>(candidateProperties),
                "Loan number '" + queryLoanNumber + "' found in query",
                true  // Query specified property
            );
        }
        
        // If multiple candidate properties exist and query doesn't specify which one
        if (candidateProperties.size() > 1) {
            logger.warn("[PROPERTY_SCOPE_DETECTED] scope=AMBIGUOUS candidates={} reason=multiple_properties_no_query_identifier",
                candidateProperties);
            return new PropertyScopeResult(
                PropertyScope.AMBIGUOUS,
                null,
                new ArrayList<>(candidateProperties),
                "Multiple properties (" + candidateProperties.size() + ") found but query does not specify which",
                false  // Query did not specify property
            );
        }
        
        // Single candidate property: unambiguous
        if (candidateProperties.size() == 1) {
            String singleProperty = candidateProperties.iterator().next();
            logger.info("[PROPERTY_SCOPE_DETECTED] scope=UNAMBIGUOUS property={} reason=single_property",
                singleProperty);
            return new PropertyScopeResult(
                PropertyScope.UNAMBIGUOUS,
                singleProperty,
                new ArrayList<>(candidateProperties),
                "Only one property found in evidence",
                false  // Query didn't specify (didn't need to)
            );
        }
        
        // No properties detected
        logger.info("[PROPERTY_SCOPE_DETECTED] scope=UNAMBIGUOUS property=null reason=no_properties_detected");
        return new PropertyScopeResult(
            PropertyScope.UNAMBIGUOUS,
            null,
            new ArrayList<>(),
            "No community properties detected in evidence",
            false  // No properties to specify
        );
    }
    
    /**
     * Extract all candidate properties from evidence chunks.
     * Looks for city names in property addresses within evidence text.
     */
    private Set<String> extractCandidateProperties(List<EvidenceChunk> evidenceChunks) {
        Set<String> properties = new LinkedHashSet<>();
        
        for (EvidenceChunk chunk : evidenceChunks) {
            String text = chunk.text();
            if (text != null) {
                String city = extractCityFromText(text);
                if (city != null) {
                    properties.add(city);
                }
            }
            
            // Also check filename for property hints
            String filename = chunk.filename();
            if (filename != null) {
                String filenameCity = extractCityFromText(filename);
                if (filenameCity != null) {
                    properties.add(filenameCity);
                }
            }
        }
        
        return properties;
    }
    
    /**
     * Extract city name from text.
     * Looks for California city names in the text.
     */
    private String extractCityFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        Matcher matcher = CITY_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Extract loan number from text.
     */
    private String extractLoanNumberFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        Matcher matcher = LOAN_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
