package com.agent.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Single embedding data point in response.
 */
public record EmbeddingData(
    @JsonProperty("object")
    String object,
    
    @JsonProperty("embedding")
    List<Double> embedding,
    
    @JsonProperty("index")
    Integer index
) {
}
