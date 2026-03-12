package com.agent.service;

import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;

/**
 * Handler for mode-specific query processing.
 * 
 * Implementations customize retrieval, reasoning, and answer generation
 * based on the task mode (DOCUMENT_QA, LEGAL_RESEARCH, etc).
 */
public interface TaskModeHandler {
    
    /**
     * Get the TaskMode this handler processes.
     * 
     * @return The TaskMode handled by this implementation
     */
    TaskMode getMode();
    
    /**
     * Process a query in this mode.
     * 
     * @param query The user's question or request
     * @param topK Number of evidence chunks to retrieve (if applicable)
     * @return ModeExecutionResult with answer and metadata
     */
    ModeExecutionResult execute(String query, int topK);
}
