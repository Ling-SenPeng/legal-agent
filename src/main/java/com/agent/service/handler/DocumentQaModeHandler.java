package com.agent.service.handler;

import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.EvidenceChunk;
import com.agent.model.RetrievalPlan;
import com.agent.model.VerificationReport;
import com.agent.config.AgentProperties;
import com.agent.service.TaskModeHandler;
import com.agent.service.RetrievalService;
import com.agent.service.DraftingService;
import com.agent.service.VerificationService;
import com.agent.service.OpenAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Handler for DOCUMENT_QA mode.
 * 
 * Preserves existing retrieval/drafting/verification flow.
 * Optimized for extracting specific information from documents.
 */
@Component
public class DocumentQaModeHandler implements TaskModeHandler {
    private static final Logger logger = LoggerFactory.getLogger(DocumentQaModeHandler.class);
    
    private final RetrievalService retrievalService;
    private final DraftingService draftingService;
    private final VerificationService verificationService;
    private final OpenAiClient openAiClient;
    private final AgentProperties agentProperties;

    public DocumentQaModeHandler(
        RetrievalService retrievalService,
        DraftingService draftingService,
        VerificationService verificationService,
        OpenAiClient openAiClient,
        AgentProperties agentProperties
    ) {
        this.retrievalService = retrievalService;
        this.draftingService = draftingService;
        this.verificationService = verificationService;
        this.openAiClient = openAiClient;
        this.agentProperties = agentProperties;
    }

    @Override
    public TaskMode getMode() {
        return TaskMode.DOCUMENT_QA;
    }

    /**
     * Execute DOCUMENT_QA query using full retrieval/drafting/verification pipeline.
     * 
     * @param query The user's question
     * @param topK Number of evidence chunks to retrieve
     * @return ModeExecutionResult with answer and evidence metadata
     */
    @Override
    public ModeExecutionResult execute(String query, int topK) {
        logger.info("[DOCUMENT_QA] Processing: {}", query);
        
        try {
            // Step 1: Retrieve evidence
            List<EvidenceChunk> evidenceChunks = retrievalService.retrieveEvidence(query, topK);
            
            if (evidenceChunks.isEmpty()) {
                return new ModeExecutionResult(
                    TaskMode.DOCUMENT_QA,
                    "No relevant evidence found in the document collection."
                );
            }

            // Step 2: Get retrieval plan for answer instruction
            RetrievalPlan plan = retrievalService.getRetrievalPlan(query);
            
            // Step 3: Draft answer
            String answer = draftingService.draftAnswer(
                query,
                evidenceChunks,
                plan.getAnswerInstruction()
            );

            // Step 4: Verify citations
            VerificationReport verification = verificationService.verify(answer);
            String finalAnswer = answer;

            // Step 5: Repair if needed
            if (!verification.passed() && agentProperties.getVerification().isRepairEnabled()) {
                finalAnswer = verificationService.repairAnswer(
                    answer,
                    query,
                    verification.missingCitationLines(),
                    openAiClient
                );
                verification = verificationService.verify(finalAnswer);
            }

            // Prepare metadata
            String metadata = String.format(
                "Retrieved %d evidence chunks | Plan: %s | Verification: %s",
                evidenceChunks.size(),
                plan.getIntent(),
                verification.passed() ? "passed" : "has missing citations"
            );

            return new ModeExecutionResult(TaskMode.DOCUMENT_QA, finalAnswer, metadata);

        } catch (Exception e) {
            logger.error("[DOCUMENT_QA] Error executing query", e);
            return new ModeExecutionResult(TaskMode.DOCUMENT_QA, "Error: " + e.getMessage());
        }
    }
}
