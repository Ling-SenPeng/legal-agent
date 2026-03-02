package com.agent.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single choice in a chat completion response.
 */
public record ChatChoice(
    @JsonProperty("index")
    Integer index,
    
    @JsonProperty("message")
    ChatMessage message,
    
    @JsonProperty("finish_reason")
    String finishReason
) {
}
