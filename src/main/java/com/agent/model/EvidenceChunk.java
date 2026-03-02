package com.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single chunk of evidence retrieved from the database.
 * Supports hybrid search with vector and keyword scoring.
 */
public record EvidenceChunk(
    @JsonProperty("chunkId")
    Long chunkId,
    
    @JsonProperty("docId")
    Long docId,
    
    @JsonProperty("pageNo")
    Integer pageNo,
    
    @JsonProperty("pageStart")
    Integer pageStart,
    
    @JsonProperty("pageEnd")
    Integer pageEnd,
    
    @JsonProperty("text")
    String text,
    
    @JsonProperty("similarity")
    Double similarity,
    
    @JsonProperty("citations")
    String citations,  // e.g., "[CIT doc=1 chunk=5 p=10-11]"
    
    @JsonProperty("vectorScore")
    Double vectorScore,  // Normalized vector similarity [0,1], null if not from vector search
    
    @JsonProperty("keywordScore")
    Double keywordScore,  // Normalized FTS ranking [0,1], null if not from keyword search
    
    @JsonProperty("finalScore")
    Double finalScore  // Hybrid fusion: alpha * vectorScore + (1-alpha) * keywordScore
) {
}
