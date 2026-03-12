package com.agent.service;

import com.agent.model.TaskRoutingResult;

/**
 * Interface for task routing engines.
 * 
 * Determines the optimal processing mode for a given query by analyzing
 * query content, intent, and characteristics.
 */
public interface TaskRouter {
    /**
     * Route a query to the appropriate task mode.
     * 
     * @param query The user's query or instruction
     * @return TaskRoutingResult containing mode, confidence, and reasoning
     */
    TaskRoutingResult route(String query);
}
