package com.agent.service;

import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.TaskRoutingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates task mode routing and handler execution.
 * 
 * Pipeline:
 * 1. Route query using TaskRouter to detect task mode
 * 2. Select corresponding TaskModeHandler
 * 3. Execute handler with query and topK
 * 4. Log routing decision
 * 5. Return execution result
 */
@Service
public class TaskModeOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(TaskModeOrchestrator.class);
    
    private final TaskRouter router;
    private final Map<TaskMode, TaskModeHandler> handlerRegistry;

    public TaskModeOrchestrator(
        TaskRouter router,
        List<TaskModeHandler> handlers
    ) {
        this.router = router;
        // Build handler registry: TaskMode -> Handler
        this.handlerRegistry = handlers.stream()
            .collect(Collectors.toMap(TaskModeHandler::getMode, Function.identity()));
        
        logger.info("TaskModeOrchestrator initialized with {} mode handlers: {}",
            handlerRegistry.size(),
            handlerRegistry.keySet());
    }

    /**
     * Execute query through routing and mode-specific handler.
     * 
     * @param query The user's question or request
     * @param topK Number of evidence chunks/results to retrieve
     * @return ModeExecutionResult from the selected handler
     */
    public ModeExecutionResult execute(String query, int topK) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Route query to detect task mode
            TaskRoutingResult routingResult = router.route(query);
            TaskMode selectedMode = routingResult.getMode();
            double confidence = routingResult.getConfidence();
            String reasoning = routingResult.getReasoning();
            
            // Log routing decision
            logger.info("=== TASK ROUTING DECISION ===");
            logger.info("  Query: \"{}\"", query);
            logger.info("  Selected Mode: {}", selectedMode);
            logger.info("  Confidence: {}", String.format("%.2f", confidence));
            logger.info("  Reasoning: {}", reasoning);
            
            // Step 2: Get handler for selected mode
            TaskModeHandler handler = handlerRegistry.get(selectedMode);
            
            if (handler == null) {
                logger.error("No handler registered for mode: {}", selectedMode);
                return new ModeExecutionResult(
                    selectedMode,
                    "Error: No handler available for mode " + selectedMode
                );
            }
            
            logger.info("  Handler: {}", handler.getClass().getSimpleName());
            
            // Step 3: Execute handler
            logger.info("=== HANDLER EXECUTION START ===");
            ModeExecutionResult result = handler.execute(query, topK);
            long executionTime = System.currentTimeMillis() - startTime;
            
            logger.info("=== HANDLER EXECUTION END ===");
            logger.info("  Success: {}", result.isSuccess());
            logger.info("  Execution Time: {} ms", executionTime);
            if (result.getMetadata() != null) {
                logger.info("  Metadata: {}", result.getMetadata());
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error in TaskModeOrchestrator.execute", e);
            return new ModeExecutionResult(
                TaskMode.DOCUMENT_QA,
                "Orchestrator error: " + e.getMessage()
            );
        }
    }

    /**
     * Get the handler for a specific task mode.
     * Useful for dependency injection or manual handler selection.
     * 
     * @param mode The TaskMode to get handler for
     * @return The TaskModeHandler, or null if none registered
     */
    public TaskModeHandler getHandler(TaskMode mode) {
        return handlerRegistry.get(mode);
    }

    /**
     * Get map of all registered handlers.
     * 
     * @return Map of TaskMode to handler
     */
    public Map<TaskMode, TaskModeHandler> getHandlerRegistry() {
        return handlerRegistry;
    }
}
