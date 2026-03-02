package com.agent.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for OpenAI Embedding API request.
 */
public record EmbeddingRequest(
    @JsonProperty("input")
    String input,
    
    @JsonProperty("model")
    String model
) {
}

