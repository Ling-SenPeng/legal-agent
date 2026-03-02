package com.agent.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for OpenAI Embedding API response.
 */
public record EmbeddingResponse(
    @JsonProperty("object")
    String object,
    
    @JsonProperty("data")
    List<EmbeddingData> data,
    
    @JsonProperty("model")
    String model,
    
    @JsonProperty("usage")
    Usage usage
) {
}
