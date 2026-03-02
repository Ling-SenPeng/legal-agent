package com.agent.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage information.
 */
public record Usage(
    @JsonProperty("prompt_tokens")
    Integer promptTokens,
    
    @JsonProperty("total_tokens")
    Integer totalTokens
) {
}
