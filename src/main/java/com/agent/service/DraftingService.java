package com.agent.service;

import com.agent.model.EvidenceChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for drafting an answer based on evidence using OpenAI.
 * Enforces citation requirements in the prompt.
 */
@Service
public class DraftingService {
    private static final Logger logger = LoggerFactory.getLogger(DraftingService.class);
    
    private final OpenAiClient openAiClient;

    public DraftingService(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    /**
     * Draft an answer based on the provided evidence.
     * Enforces strict citation requirements in the prompt.
     * 
     * @param question The original question
     * @param evidenceChunks The retrieved evidence chunks
     * @return The drafted answer with citations
     */
    public String draftAnswer(String question, List<EvidenceChunk> evidenceChunks) {
        return draftAnswer(question, evidenceChunks, null);
    }

    /**
     * Draft an answer based on the provided evidence with optional answer instruction.
     * Enforces strict citation requirements in the prompt.
     * 
     * @param question The original question
     * @param evidenceChunks The retrieved evidence chunks
     * @param answerInstruction Optional instruction on how to format the answer (from RetrievalPlan)
     * @return The drafted answer with citations
     */
    public String draftAnswer(String question, List<EvidenceChunk> evidenceChunks, String answerInstruction) {
        logger.info("Drafting answer for question: {}", question);
        if (answerInstruction != null) {
            logger.info("Using answer instruction: {}", answerInstruction);
        }
        logger.debug("Using {} evidence chunks", evidenceChunks.size());

        String systemMessage = buildSystemPrompt();
        String userMessage = buildUserMessage(question, evidenceChunks, answerInstruction);

        String draftedAnswer = openAiClient.chatCompletion(systemMessage, userMessage);
        logger.debug("Draft answer generated, length: {}", draftedAnswer.length());

        return draftedAnswer;
    }

    /**
     * Build the system prompt with strict instructions for citation.
     */
    private String buildSystemPrompt() {
        return """
            You are a legal document analysis assistant. Your task is to answer questions about legal documents
            based ONLY on the provided evidence chunks. 
            
            CRITICAL RULES:
            1. Every factual claim must be backed by evidence from the provided chunks.
            2. Every factual claim must end with a citation token in the format: [CIT doc=<doc_id> chunk=<chunk_id> p=<page_start>-<page_end>]
            3. Factual claims include: specific dates, names, amounts, events, actions, agreements, payments, etc.
            4. If you cannot find evidence for a claim, either:
               a) Omit the claim entirely, OR
               b) Explicitly mark it as "Needs evidence: <claim>"
            5. Structure your answer with:
               - Key findings (bullets with citations)
               - Supporting context (if helpful)
               - Gaps / Needs evidence (if applicable)
            
            Do NOT hallucinate or invent information not in the evidence.
            """;
    }

    /**
     * Build the user message with the question and evidence.
     */
    private String buildUserMessage(String question, List<EvidenceChunk> evidenceChunks) {
        return buildUserMessage(question, evidenceChunks, null);
    }

    /**
     * Build the user message with the question, evidence, and optional answer instruction.
     */
    private String buildUserMessage(String question, List<EvidenceChunk> evidenceChunks, String answerInstruction) {
        StringBuilder sb = new StringBuilder();
        
        // Add answer instruction if provided
        if (answerInstruction != null && !answerInstruction.isEmpty()) {
            sb.append("ANSWER INSTRUCTION: ").append(answerInstruction).append("\n\n");
        }
        
        sb.append("QUESTION: ").append(question).append("\n\n");
        
        sb.append("EVIDENCE CHUNKS:\n");
        for (int i = 0; i < evidenceChunks.size(); i++) {
            EvidenceChunk chunk = evidenceChunks.get(i);
            sb.append(String.format("\n[%d] Citation: %s (similarity: %.2f)\n", 
                i + 1, chunk.citations(), chunk.similarity()));
            sb.append("Text: ").append(chunk.text()).append("\n");
        }
        
        sb.append("\n\nProvide your answer below, ensuring every factual claim includes a citation token [CIT ...]\n");
        sb.append("ANSWER:\n");
        
        return sb.toString();
    }
}
