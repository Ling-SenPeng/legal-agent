package com.agent.service;

import com.agent.config.AgentProperties;
import com.agent.model.AgentQueryRequest;
import com.agent.model.AgentQueryResponse;
import com.agent.model.EvidenceChunk;
import com.agent.model.EvidenceDTO;
import com.agent.model.ModeExecutionResult;
import com.agent.model.RetrievalPlan;
import com.agent.model.TaskMode;
import com.agent.model.VerificationReport;
import com.agent.service.mapper.EvidenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Main orchestration service for the evidence-grounded agent.
 * 
 * Refactored to use TaskModeOrchestrator for routing and mode-specific handling.
 * Pipeline: Query -> Router -> TaskModeOrchestrator -> TaskModeHandler -> Response
 * 
 * Preserves existing DOCUMENT_QA behavior and hybrid retrieval flow.
 */
@Service
public class AgentService {
    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);
    
    private final TaskModeOrchestrator orchestrator;
    private final RetrievalService retrievalService;
    private final DraftingService draftingService;
    private final VerificationService verificationService;
    private final OpenAiClient openAiClient;
    private final AgentProperties agentProperties;
    private final EvidenceMapper evidenceMapper;

    public AgentService(
        TaskModeOrchestrator orchestrator,
        RetrievalService retrievalService,
        DraftingService draftingService,
        VerificationService verificationService,
        OpenAiClient openAiClient,
        AgentProperties agentProperties,
        EvidenceMapper evidenceMapper
    ) {
        this.orchestrator = orchestrator;
        this.retrievalService = retrievalService;
        this.draftingService = draftingService;
        this.verificationService = verificationService;
        this.openAiClient = openAiClient;
        this.agentProperties = agentProperties;
        this.evidenceMapper = evidenceMapper;
    }

    /**
     * Execute the agent pipeline via task mode orchestration.
     * 
     * Routes queries to appropriate handler based on detected task mode.
     * For DOCUMENT_QA mode, preserves full retrieval/drafting/verification flow.
     * 
     * @param request The agent query request
     * @return The complete agent response with answer, evidence, and verification
     */
    public AgentQueryResponse processQuery(AgentQueryRequest request) {
        long startTime = System.currentTimeMillis();
        logger.info("=== AGENT PIPELINE START ===");
        logger.info("Processing query: {}", request.question());

        try {
            int topK = request.topK() != null ? request.topK() : agentProperties.getDefaultTopK();
            
            // Step 1: Route query through TaskModeOrchestrator
            logger.info("Step 1: Task Mode Routing & Handler Execution");
            ModeExecutionResult modeResult = orchestrator.execute(request.question(), topK);
            
            // Check execution success
            if (!modeResult.isSuccess()) {
                logger.error("Handler execution failed: {}", modeResult.getErrorMessage());
                logger.info("=== AGENT PIPELINE END (HANDLER ERROR) ===");
                return new AgentQueryResponse(
                    "Error: " + modeResult.getErrorMessage(),
                    List.of(),
                    new VerificationReport(false, List.of(), modeResult.getErrorMessage()),
                    System.currentTimeMillis() - startTime
                );
            }

            String finalAnswer = modeResult.getAnswer();
            
            // Step 2: For DOCUMENT_QA mode, retrieve evidence for response details
            // (Preserves existing behavior of including evidence chunks and verification in response)
            List<EvidenceDTO> evidenceDTOs = List.of();
            VerificationReport verification = new VerificationReport(true, List.of(), "Success");
            
            if (modeResult.getMode() == TaskMode.DOCUMENT_QA) {
                logger.info("Step 2: Retrieving Evidence for DOCUMENT_QA Response");
                
                // Retrieve evidence chunks and map to DTOs
                List<EvidenceChunk> evidenceChunks = retrievalService.retrieveEvidence(request.question(), topK);
                logger.info("  Retrieved {} evidence chunks", evidenceChunks.size());
                
                // Map chunks to DTOs for response (top 5)
                evidenceDTOs = evidenceMapper.mapToDto(evidenceChunks, 5);
                logger.info("  Mapped to {} evidence DTOs for response", evidenceDTOs.size());
                
                // Verify final answer
                verification = verificationService.verify(finalAnswer);
                logger.info("  Verification passed: {}", verification.passed());
                logger.info("  Missing citations: {}", verification.missingCitationLines().size());
            } else {
                logger.info("Step 2: Skipped Evidence Retrieval (non-DOCUMENT_QA mode)");
            }

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("=== AGENT PIPELINE END (SUCCESS) ===");
            logger.info("Total processing time: {} ms", processingTime);

            return new AgentQueryResponse(
                finalAnswer,
                evidenceDTOs,
                verification,
                processingTime
            );

        } catch (Exception e) {
            logger.error("Error processing query", e);
            long processingTime = System.currentTimeMillis() - startTime;
            return new AgentQueryResponse(
                "Error processing query: " + e.getMessage(),
                List.of(),
                new VerificationReport(false, List.of(), "Error: " + e.getMessage()),
                processingTime
            );
        }
    }
}
