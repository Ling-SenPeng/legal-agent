package com.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single chunk of evidence retrieved from the database.
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
    String citations  // e.g., "[CIT doc=1 chunk=5 p=10-11]"
) {
}
