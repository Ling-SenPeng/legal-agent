package com.agent;

import com.agent.model.AgentQueryRequest;
import com.agent.model.AgentQueryResponse;
import com.agent.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the evidence-grounded agent API.
 */
@RestController
@RequestMapping("/agent")
public class AgentController {
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * POST /agent/query
     * Process a query and return an evidence-grounded answer.
     * 
     * @param request The query request containing question, topK, and optional filters
     * @return Agent response with answer, evidence, and verification results
     */
    @PostMapping("/query")
    public ResponseEntity<AgentQueryResponse> query(@RequestBody AgentQueryRequest request) {
        logger.info("Received query request for question: {}", request.question());
        
        try {
            AgentQueryResponse response = agentService.processQuery(request);
            logger.info("Query processed successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Unexpected error processing query", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /agent/health
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Agent is running");
    }
}

