package com.agent.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for OpenAI Chat Completion request.
 */
public record ChatCompletionRequest(
    @JsonProperty("model")
    String model,
    
    @JsonProperty("messages")
    List<ChatMessage> messages,
    
    @JsonProperty("temperature")
    Double temperature,
    
    @JsonProperty("max_tokens")
    Integer maxTokens,
    
    @JsonProperty("top_p")
    Double topP
) {
}

