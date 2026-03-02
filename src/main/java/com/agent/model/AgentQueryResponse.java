package com.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO from agent query endpoint.
 */
public record AgentQueryResponse(
    @JsonProperty("answer")
    String answer,
    
    @JsonProperty("evidence")
    List<EvidenceChunk> evidence,
    
    @JsonProperty("verification")
    VerificationReport verification,
    
    @JsonProperty("processingTimeMs")
    long processingTimeMs
) {
}
