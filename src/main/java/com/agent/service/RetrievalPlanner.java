package com.agent.service;

import com.agent.model.RetrievalPlan;

/**
 * Service interface for generating retrieval plans.
 * Converts user queries into optimized search strategies.
 */
public interface RetrievalPlanner {
    /**
     * Generate a retrieval plan for the given user query.
     * 
     * @param userQuery The user's question or instruction
     * @return A RetrievalPlan with optimized keyword and vector queries
     */
    RetrievalPlan plan(String userQuery);
}
