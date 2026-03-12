package com.agent.service.handler;

import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.service.TaskModeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for DRAFTING mode.
 * 
 * Generates legal documents (memos, briefs, motions, letters) grounded in evidence.
 * 
 * TODO: Implement document generation with proper formatting, citations, and persuasive structure.
 */
@Component
public class DraftingModeHandler implements TaskModeHandler {
    private static final Logger logger = LoggerFactory.getLogger(DraftingModeHandler.class);

    @Override
    public TaskMode getMode() {
        return TaskMode.DRAFTING;
    }

    /**
     * Execute DRAFTING query to generate legal documents.
     * 
     * @param query The user's drafting request
     * @param topK Number of evidence chunks to incorporate into the document
     * @return ModeExecutionResult with generated document
     */
    @Override
    public ModeExecutionResult execute(String query, int topK) {
        logger.info("[DRAFTING] Processing: {}", query);
        
        // Placeholder implementation
        String answer = String.format(
            "Document drafting for: \"%s\"\n\n" +
            "This feature is under development. " +
            "Currently supported: document question-answering mode.\n\n" +
            "Coming soon: Memo generation, brief drafting, motion composition, persuasive letter writing.",
            query
        );
        
        String metadata = "Mode: Drafting Document type: Per user request";
        
        return new ModeExecutionResult(TaskMode.DRAFTING, answer, metadata);
    }
}
