package com.agent.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for OpenAI Chat Completion response.
 */
public record ChatCompletionResponse(
    @JsonProperty("id")
    String id,
    
    @JsonProperty("object")
    String object,
    
    @JsonProperty("created")
    Long created,
    
    @JsonProperty("model")
    String model,
    
    @JsonProperty("choices")
    List<ChatChoice> choices,
    
    @JsonProperty("usage")
    Usage usage
) {
}
