package com.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient used in HTTP calls to OpenAI.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://api.openai.com/v1")
            .build();
    }
}
