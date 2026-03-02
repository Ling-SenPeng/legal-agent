package com.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request DTO for agent query endpoint.
 */
public record AgentQueryRequest(
    @JsonProperty("question")
    String question,
    
    @JsonProperty("topK")
    Integer topK,
    
    @JsonProperty("filters")
    Map<String, Object> filters
) {
    public AgentQueryRequest {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question cannot be blank");
        }
        if (topK == null || topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
    }
}
