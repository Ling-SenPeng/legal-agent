package com.agent.service;

import com.agent.config.AgentProperties;
import com.agent.model.AgentQueryRequest;
import com.agent.model.AgentQueryResponse;
import com.agent.model.EvidenceChunk;
import com.agent.model.RetrievalPlan;
import com.agent.model.VerificationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Main orchestration service for the evidence-grounded agent.
 * Coordinates retrieval, drafting, and verification steps.
 * Uses RetrievalPlanner to optimize queries and pass instructions to drafting stage.
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
        logger.info("=== AGENT PIPELINE START ===");
        logger.info("Processing query: {}", request.question());

        try {
            // Step 1: Create retrieval plan and retrieve evidence
            logger.info("Step 1: Retrieval Planning & Evidence Retrieval");
            int topK = request.topK() != null ? request.topK() : agentProperties.getDefaultTopK();
            
            // Get the retrieval plan
            RetrievalPlan plan = retrievalService.getRetrievalPlan(request.question());
            logger.info("  Retrieval Plan Generated:");
            logger.info("    Intent: {}", plan.getIntent());
            logger.info("    Keyword queries: {}", plan.getKeywordQueries());
            logger.info("    Vector query: {}", plan.getVectorQuery());
            
            // Retrieve evidence
            List<EvidenceChunk> evidenceChunks = retrievalService.retrieveEvidence(request.question(), topK);
            logger.info("  Retrieved {} evidence chunks", evidenceChunks.size());
            
            if (evidenceChunks.isEmpty()) {
                logger.warn("No evidence found for question");
                logger.info("=== AGENT PIPELINE END (NO EVIDENCE) ===");
                return new AgentQueryResponse(
                    "Unable to answer: No relevant evidence found in the knowledge base.",
                    List.of(),
                    new VerificationReport(false, List.of(), "No evidence available"),
                    System.currentTimeMillis() - startTime
                );
            }

            // Step 2: Drafting with answer instruction from retrieval plan
            logger.info("Step 2: Answer Drafting");
            logger.info("  Using answer instruction: {}", plan.getAnswerInstruction());
            String draftedAnswer = draftingService.draftAnswer(
                request.question(), 
                evidenceChunks, 
                plan.getAnswerInstruction()
            );
            logger.info("  Draft answer length: {}", draftedAnswer.length());

            // Step 3: Verification
            logger.info("Step 3: Citation Verification");
            VerificationReport verification = verificationService.verify(draftedAnswer);
            logger.info("  Verification passed: {}", verification.passed());
            logger.info("  Missing citations: {}", verification.missingCitationLines().size());
            String finalAnswer = draftedAnswer;

            // Step 4: Repair (if enabled and verification failed)
            if (!verification.passed() && agentProperties.getVerification().isRepairEnabled()) {
                logger.info("Step 4: Answer Repair");
                String repairedAnswer = verificationService.repairAnswer(
                    draftedAnswer,
                    request.question(),
                    verification.missingCitationLines(),
                    openAiClient
                );
                logger.info("  Repaired answer length: {}", repairedAnswer.length());
                finalAnswer = repairedAnswer;
                
                // Re-verify after repair
                verification = verificationService.verify(repairedAnswer);
                logger.info("  After repair verification: passed={}", verification.passed());
                logger.info("  Remaining missing citations: {}", verification.missingCitationLines().size());
            } else {
                logger.info("Step 4: Repair Skipped (verification passed or repair disabled)");
            }

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("=== AGENT PIPELINE END (SUCCESS) ===");
            logger.info("Total processing time: {} ms", processingTime);

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
