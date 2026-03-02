package com.agent.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single message in a chat completion request/response.
 */
public record ChatMessage(
    @JsonProperty("role")
    String role,
    
    @JsonProperty("content")
    String content
) {
}
