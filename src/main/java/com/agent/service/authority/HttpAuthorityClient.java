package com.agent.service.authority;

import com.agent.config.AuthorityServiceProperties;
import com.agent.model.analysis.authority.RetrievedAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HTTP-based implementation of AuthorityClient.
 * 
 * Communicates with the authority-ingest service via REST API.
 * Handles empty responses, deduplication, and result merging.
 */
@Service
public class HttpAuthorityClient implements AuthorityClient {
    private static final Logger logger = LoggerFactory.getLogger(HttpAuthorityClient.class);
    
    private final WebClient webClient;
    private final AuthorityServiceProperties properties;

    public HttpAuthorityClient(WebClient.Builder webClientBuilder, AuthorityServiceProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    @Override
    public List<RetrievedAuthority> searchAuthorities(String query, int topK) {
        logger.debug("Searching authorities with query: '{}' (topK={})", query, topK);
        
        if (query == null || query.isEmpty()) {
            logger.warn("Empty query provided to searchAuthorities");
            return Collections.emptyList();
        }
        
        try {
            String uri = UriComponentsBuilder.fromPath("/authorities/search")
                .queryParam("q", query)
                .queryParam("topK", topK)
                .toUriString();
            
            List<RetrievedAuthority> results = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<RetrievedAuthority>>() {})
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .onErrorResume(e -> {
                    logger.warn("Error searching authorities: {}", e.getMessage());
                    return Mono.just(Collections.emptyList());
                })
                .block();
            
            if (results == null) {
                logger.debug("Null response from searchAuthorities, returning empty list");
                return Collections.emptyList();
            }
            
            // Normalize relevance scores to 0.0-1.0 range
            results = normalizeRelevanceScores(results);
            
            logger.info("Found {} authorities for query: '{}'", results.size(), query);
            return results;
        } catch (Exception e) {
            logger.error("Error searching authorities", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<RetrievedAuthority> findAuthoritiesByTopic(String topic) {
        logger.debug("Finding authorities by topic: '{}'", topic);
        
        if (topic == null || topic.isEmpty()) {
            logger.warn("Empty topic provided to findAuthoritiesByTopic");
            return Collections.emptyList();
        }
        
        try {
            List<RetrievedAuthority> results = webClient.get()
                .uri("/authorities/topic/{topic}", topic)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<RetrievedAuthority>>() {})
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .onErrorResume(e -> {
                    logger.warn("Error finding authorities by topic '{}': {}", topic, e.getMessage());
                    return Mono.just(Collections.emptyList());
                })
                .block();
            
            if (results == null) {
                logger.debug("Null response from findAuthoritiesByTopic, returning empty list");
                return Collections.emptyList();
            }
            
            // Normalize relevance scores to 0.0-1.0 range
            results = normalizeRelevanceScores(results);
            
            logger.info("Found {} authorities for topic: '{}'", results.size(), topic);
            return results;
        } catch (Exception e) {
            logger.error("Error finding authorities by topic: {}", topic, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<RetrievedAuthority> findRelevantAuthorities(String query, List<String> topics) {
        logger.debug("Finding relevant authorities for query: '{}' and topics: {}", query, topics);
        
        if ((query == null || query.isEmpty()) && (topics == null || topics.isEmpty())) {
            logger.warn("Both query and topics are empty");
            return Collections.emptyList();
        }
        
        // Collect results from all sources
        Set<RetrievedAuthority> deduplicated = new LinkedHashSet<>();
        
        // Search by query if provided
        if (query != null && !query.isEmpty()) {
            List<RetrievedAuthority> searchResults = searchAuthorities(query, 10);
            deduplicated.addAll(searchResults);
        }
        
        // Search by topics if provided
        if (topics != null && !topics.isEmpty()) {
            for (String topic : topics) {
                List<RetrievedAuthority> topicResults = findAuthoritiesByTopic(topic);
                deduplicated.addAll(topicResults);
            }
        }
        
        List<RetrievedAuthority> merged = new ArrayList<>(deduplicated);
        
        // Sort by relevance score descending
        merged.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        
        logger.info("Merged and deduplicated to {} unique authorities", merged.size());
        return merged;
    }
    
    /**
     * Normalize relevance scores to be within 0.0-1.0 range.
     * If any score is outside this range, normalize all scores using min-max scaling.
     * 
     * @param authorities List of authorities to normalize
     * @return List with normalized relevance scores
     */
    private List<RetrievedAuthority> normalizeRelevanceScores(List<RetrievedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return authorities;
        }
        
        // Check if any score is outside 0.0-1.0 range
        boolean needsNormalization = authorities.stream()
            .anyMatch(a -> a.getRelevanceScore() < 0.0 || a.getRelevanceScore() > 1.0);
        
        if (!needsNormalization) {
            return authorities;
        }
        
        // Find min and max scores
        double minScore = authorities.stream()
            .mapToDouble(RetrievedAuthority::getRelevanceScore)
            .min()
            .orElse(0.0);
        
        double maxScore = authorities.stream()
            .mapToDouble(RetrievedAuthority::getRelevanceScore)
            .max()
            .orElse(1.0);
        
        logger.debug("Normalizing relevance scores from range [{}, {}] to [0.0, 1.0]", minScore, maxScore);
        
        // If all scores are the same, set them to 1.0
        if (maxScore == minScore) {
            return authorities.stream()
                .map(auth -> new RetrievedAuthority(
                    auth.getAuthorityId(),
                    auth.getTitle(),
                    auth.getCitation(),
                    auth.getAuthorityType(),
                    auth.getRuleSummary(),
                    1.0
                ))
                .collect(Collectors.toList());
        }
        
        // Min-max normalization: (x - min) / (max - min)
        final double range = maxScore - minScore;
        return authorities.stream()
            .map(auth -> {
                double normalized = (auth.getRelevanceScore() - minScore) / range;
                return new RetrievedAuthority(
                    auth.getAuthorityId(),
                    auth.getTitle(),
                    auth.getCitation(),
                    auth.getAuthorityType(),
                    auth.getRuleSummary(),
                    normalized
                );
            })
            .collect(Collectors.toList());
    }
}
