package com.agent.service;

import com.agent.config.AgentProperties;
import com.agent.model.AgentQueryRequest;
import com.agent.model.AgentQueryResponse;
import com.agent.model.EvidenceChunk;
import com.agent.model.VerificationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Main orchestration service for the evidence-grounded agent.
 * Coordinates retrieval, drafting, and verification steps.
 */
@Service
public class AgentService {
    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);
    
    private final RetrievalService retrievalService;
    private final DraftingService draftingService;
    private final VerificationService verificationService;
    private final OpenAiClient openAiClient;
    private final AgentProperties agentProperties;

    public AgentService(
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

    /**
     * Execute the full agent pipeline: retrieve, draft, verify, and optionally repair.
     * 
     * @param request The agent query request
     * @return The complete agent response with answer, evidence, and verification
     */
    public AgentQueryResponse processQuery(AgentQueryRequest request) {
        long startTime = System.currentTimeMillis();
        logger.info("Processing query: {}", request.question());

        try {
            // Step 1: Retrieval
            logger.info("Step 1: Retrieving evidence");
            int topK = request.topK() != null ? request.topK() : agentProperties.getDefaultTopK();
            List<EvidenceChunk> evidenceChunks = retrievalService.retrieveEvidence(request.question(), topK);
            
            if (evidenceChunks.isEmpty()) {
                logger.warn("No evidence found for question");
                return new AgentQueryResponse(
                    "Unable to answer: No relevant evidence found in the knowledge base.",
                    List.of(),
                    new VerificationReport(false, List.of(), "No evidence available"),
                    System.currentTimeMillis() - startTime
                );
            }

            // Step 2: Drafting
            logger.info("Step 2: Drafting answer with {} evidence chunks", evidenceChunks.size());
            String draftedAnswer = draftingService.draftAnswer(request.question(), evidenceChunks);

            // Step 3: Verification
            logger.info("Step 3: Verifying citations");
            VerificationReport verification = verificationService.verify(draftedAnswer);
            String finalAnswer = draftedAnswer;

            // Step 4: Repair (if enabled and verification failed)
            if (!verification.passed() && agentProperties.getVerification().isRepairEnabled()) {
                logger.info("Step 4: Repairing answer due to missing citations");
                String repairedAnswer = verificationService.repairAnswer(
                    draftedAnswer,
                    request.question(),
                    verification.missingCitationLines(),
                    openAiClient
                );
                finalAnswer = repairedAnswer;
                
                // Re-verify after repair
                verification = verificationService.verify(repairedAnswer);
                logger.info("After repair verification: passed={}", verification.passed());
            }

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Query processed successfully in {} ms", processingTime);

            return new AgentQueryResponse(
                finalAnswer,
                evidenceChunks,
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
