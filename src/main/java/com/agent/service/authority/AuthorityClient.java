package com.agent.service.authority;

import com.agent.model.analysis.authority.RetrievedAuthority;
import java.util.List;

/**
 * Client interface for retrieving legal authorities from the authority-ingest service.
 * 
 * Defines methods for searching authorities and finding them by topic.
 */
public interface AuthorityClient {
    
    /**
     * Search for authorities based on a query string.
     * 
     * @param query The search query
     * @param topK Number of results to return
     * @return List of matching authorities, sorted by relevance score
     */
    List<RetrievedAuthority> searchAuthorities(String query, int topK);
    
    /**
     * Find authorities by topic.
     * 
     * @param topic The topic name (e.g., "REIMBURSEMENT", "SUPPORT")
     * @return List of authorities for the given topic
     */
    List<RetrievedAuthority> findAuthoritiesByTopic(String topic);
    
    /**
     * Find relevant authorities for a query across multiple topics.
     * 
     * Merges results from searching the query and looking up authorities
     * by topic, deduplicating by authorityId.
     * 
     * @param query The search query
     * @param topics List of topic names to include
     * @return List of merged and deduplicated authorities
     */
    List<RetrievedAuthority> findRelevantAuthorities(String query, List<String> topics);
}
