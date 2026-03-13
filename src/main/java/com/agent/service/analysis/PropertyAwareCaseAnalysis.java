package com.agent.service.analysis;

import com.agent.model.EvidenceChunk;
import com.agent.model.analysis.CaseFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles property-aware fact filtering during case analysis.
 * 
 * Prevents cross-property contamination by:
 * 1. Determining query property scope (AMBIGUOUS vs UNAMBIGUOUS)
 * 2. Attributing facts to specific properties
 * 3. Filtering facts based on property scope when analyzing
 * 4. Either splitting analysis by property or warning about ambiguity
 */
@Service
public class PropertyAwareCaseAnalysis {
    private static final Logger logger = LoggerFactory.getLogger(PropertyAwareCaseAnalysis.class);
    
    private final PropertyScopeDetector scopeDetector;
    private final PropertyExtractionService propertyExtractor;
    
    public PropertyAwareCaseAnalysis(
        PropertyScopeDetector scopeDetector,
        PropertyExtractionService propertyExtractor
    ) {
        this.scopeDetector = scopeDetector;
        this.propertyExtractor = propertyExtractor;
    }
    
    /**
     * Detect property scope and attribute facts to properties.
     * 
     * Returns enriched facts with property information, plus the detected scope.
     * 
     * @param query The user's query
     * @param evidenceChunks Retrieved evidence
     * @param caseFacts Extracted case facts
     * @return Result containing scope, attributed facts, and groupings
     */
    public PropertyAwareAnalysisResult analyzePropertyScope(
        String query,
        List<EvidenceChunk> evidenceChunks,
        List<CaseFact> caseFacts
    ) {
        // Step 1: Detect property scope from query and evidence
        PropertyScopeDetector.PropertyScopeResult scopeResult = 
            scopeDetector.detectPropertyScope(query, evidenceChunks);
        
        logger.info("[PROPERTY_FACT_GROUPING] {}", scopeResult);
        
        // Step 2: Attribute facts to properties
        Map<Long, PropertyExtractionService.PropertyInfo> chunkPropertyMap = 
            mapChunksToProperties(evidenceChunks);
        
        List<PropertyAttributedFact> attributedFacts = 
            attributeFactsToProperties(caseFacts, chunkPropertyMap);
        
        // Log grouping summary
        String groupingSummary = PropertyFactGrouper.summarizeFactsByProperty(attributedFacts);
        logger.info("[PROPERTY_FACT_GROUPING] {}", groupingSummary);
        
        // Step 3: Group facts by property
        Map<String, List<PropertyAttributedFact>> factsGroupedByProperty =
            PropertyFactGrouper.groupFactsByPropertyDisplay(attributedFacts);
        
        // Step 4: Determine whether to filter facts
        List<PropertyAttributedFact> usableFacts;
        if (scopeResult.scope == PropertyScopeDetector.PropertyScope.AMBIGUOUS) {
            logger.warn("[AMBIGUOUS_PROPERTY_QUERY] Query does not specify property, {} candidates found: {}",
                scopeResult.candidateProperties.size(), scopeResult.candidateProperties);
            usableFacts = attributedFacts;  // Keep all facts, will be analyzed per-property
        } else if (scopeResult.identifiedProperty != null) {
            // Filter to only facts from identified property
            usableFacts = filterFactsForProperty(attributedFacts, scopeResult.identifiedProperty);
            logger.info("[CASE_ANALYSIS_FILTERED_BY_PROPERTY] Keeping %d facts for property: %s",
                usableFacts.size(), scopeResult.identifiedProperty);
        } else {
            // No property identified and no candidates found
            usableFacts = attributedFacts;
        }
        
        return new PropertyAwareAnalysisResult(
            scopeResult,
            attributedFacts,
            factsGroupedByProperty,
            usableFacts
        );
    }
    
    /**
     * Map evidence chunks to their property information.
     * 
     * @param evidenceChunks Chunks to map
     * @return Map of chunkId → PropertyInfo
     */
    private Map<Long, PropertyExtractionService.PropertyInfo> mapChunksToProperties(
        List<EvidenceChunk> evidenceChunks
    ) {
        Map<Long, PropertyExtractionService.PropertyInfo> map = new HashMap<>();
        
        for (EvidenceChunk chunk : evidenceChunks) {
            PropertyExtractionService.PropertyInfo info = propertyExtractor.extractPropertyInfo(chunk);
            map.put(chunk.chunkId(), info);
        }
        
        return map;
    }
    
    /**
     * Attribute facts to properties based on their source chunks.
     * 
     * @param caseFacts Facts to attribute
     * @param chunkPropertyMap Map of chunk → property info
     * @return Facts with property attribution
     */
    private List<PropertyAttributedFact> attributeFactsToProperties(
        List<CaseFact> caseFacts,
        Map<Long, PropertyExtractionService.PropertyInfo> chunkPropertyMap
    ) {
        List<PropertyAttributedFact> attributed = new ArrayList<>();
        
        for (CaseFact fact : caseFacts) {
            // Extract chunk ID from source reference if possible
            // Format: "Chunk 123" or similar
            Long chunkId = extractChunkIdFromSourceReference(fact.getSourceReference());
            
            PropertyExtractionService.PropertyInfo propInfo = null;
            if (chunkId != null) {
                propInfo = chunkPropertyMap.get(chunkId);
            }
            
            PropertyAttributedFact attributed_fact;
            if (propInfo != null) {
                attributed_fact = new PropertyAttributedFact(
                    fact,
                    propInfo.city,
                    propInfo.address,
                    propInfo.loanNumber,
                    propInfo.sourceFilename
                );
            } else {
                // No property info available
                attributed_fact = new PropertyAttributedFact(fact);
            }
            
            attributed.add(attributed_fact);
        }
        
        return attributed;
    }
    
    /**
     * Filter facts to only those from a specific property.
     * 
     * @param facts Facts to filter
     * @param propertyIdentifier Property to match (city name or loan number)
     * @return Filtered facts
     */
    private List<PropertyAttributedFact> filterFactsForProperty(
        List<PropertyAttributedFact> facts,
        String propertyIdentifier
    ) {
        String normalized = propertyIdentifier.toLowerCase();
        
        return facts.stream()
            .filter(f -> {
                String factProperty = f.getPropertyKey();
                return normalized.contains(factProperty) || factProperty.contains(normalized);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Extract chunk ID from source reference string.
     * Source references are typically formatted as "Chunk 123" or similar.
     */
    private Long extractChunkIdFromSourceReference(String sourceRef) {
        if (sourceRef == null || sourceRef.isEmpty()) {
            return null;
        }
        
        try {
            // Try to extract number from "Chunk 123" format
            if (sourceRef.contains("Chunk ") || sourceRef.contains("chunk ")) {
                String[] parts = sourceRef.split("\\s+");
                if (parts.length >= 2) {
                    return Long.parseLong(parts[parts.length - 1]);
                }
            }
        } catch (NumberFormatException e) {
            // Ignore - couldn't parse
        }
        
        return null;
    }
    
    /**
     * Result of property-aware analysis.
     */
    public static class PropertyAwareAnalysisResult {
        public final PropertyScopeDetector.PropertyScopeResult scope;
        public final List<PropertyAttributedFact> allAttributedFacts;
        public final Map<String, List<PropertyAttributedFact>> factsGroupedByProperty;
        public final List<PropertyAttributedFact> factsToUseForAnalysis;
        
        public PropertyAwareAnalysisResult(
            PropertyScopeDetector.PropertyScopeResult scope,
            List<PropertyAttributedFact> allAttributedFacts,
            Map<String, List<PropertyAttributedFact>> factsGroupedByProperty,
            List<PropertyAttributedFact> factsToUseForAnalysis
        ) {
            this.scope = scope;
            this.allAttributedFacts = allAttributedFacts;
            this.factsGroupedByProperty = factsGroupedByProperty;
            this.factsToUseForAnalysis = factsToUseForAnalysis;
        }
        
        /**
         * Check if analysis should be split by property.
         */
        public boolean shouldSplitByProperty() {
            return scope.scope == PropertyScopeDetector.PropertyScope.AMBIGUOUS &&
                   factsGroupedByProperty.size() > 1;
        }
        
        /**
         * Get properties available in evidence.
         */
        public Set<String> getAvailableProperties() {
            return factsGroupedByProperty.keySet();
        }
        
        /**
         * Format ambiguity warning for output.
         */
        public String formatAmbiguityWarning() {
            if (scope.scope == PropertyScopeDetector.PropertyScope.AMBIGUOUS) {
                return String.format(
                    "⚠️  AMBIGUOUS PROPERTY: Your query does not specify which community property is at issue. " +
                    "Evidence contains %d properties: %s. " +
                    "Please specify the property (e.g., '%s') for more accurate analysis.",
                    scope.candidateProperties.size(),
                    String.join(", ", scope.candidateProperties),
                    scope.candidateProperties.isEmpty() ? "property name" : scope.candidateProperties.get(0)
                );
            }
            return null;
        }
    }
}
