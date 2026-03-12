package com.agent.service;

import com.agent.config.OpenAiProperties;
import com.agent.model.openai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Duration;
import java.util.List;

/**
 * Service for calling OpenAI APIs (embeddings and chat completions).
 */
@Service
public class OpenAiClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiClient.class);
    
    private final WebClient webClient;
    private final OpenAiProperties openAiProperties;

    public OpenAiClient(WebClient webClient, OpenAiProperties openAiProperties) {
        this.webClient = webClient;
        this.openAiProperties = openAiProperties;
    }

    /**
     * Generate embedding for a given text using OpenAI Embeddings API.
     */
    public List<Double> generateEmbedding(String text) {
        logger.debug("Generating embedding for text of length: {}", text.length());
        
        EmbeddingRequest request = new EmbeddingRequest(
            text,
            openAiProperties.getEmbeddingModel()
        );

        try {
            EmbeddingResponse response = webClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .timeout(Duration.ofMillis(openAiProperties.getTimeoutMs()))
                .block();

            if (response != null && !response.data().isEmpty()) {
                List<Double> embedding = response.data().get(0).embedding();
                logger.debug("Successfully generated embedding with dimension: {}", embedding.size());
                return embedding;
            } else {
                throw new RuntimeException("Empty embedding response from OpenAI");
            }
        } catch (WebClientResponseException e) {
            logger.error("OpenAI API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error generating embedding", e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Call OpenAI Chat Completions API with a system and user message.
     */
    public String chatCompletion(String systemMessage, String userMessage) {
        logger.debug("Calling chat completion with system message length: {}, user message length: {}",
            systemMessage.length(), userMessage.length());

        ChatMessage sysMsg = new ChatMessage("system", systemMessage);
        ChatMessage userMsg = new ChatMessage("user", userMessage);

        ChatCompletionRequest request = new ChatCompletionRequest(
            openAiProperties.getModel(),
            List.of(sysMsg, userMsg),
            0.2, // Low temperature for deterministic responses
            2000, // max tokens
            1.0
        );

        try {
            ChatCompletionResponse response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .timeout(Duration.ofMillis(openAiProperties.getTimeoutMs()))
                .block();

            if (response != null && !response.choices().isEmpty()) {
                String content = response.choices().get(0).message().content();
                logger.debug("Chat completion response length: {}", content.length());
                return content;
            } else {
                throw new RuntimeException("Empty chat completion response from OpenAI");
            }
        } catch (WebClientResponseException e) {
            logger.error("OpenAI API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to call chat completion: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error calling chat completion", e);
            throw new RuntimeException("Failed to call chat completion: " + e.getMessage(), e);
        }
    }
}
