package com.agent.service;

import com.agent.model.EvidenceChunk;
import com.agent.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service for retrieving relevant evidence chunks from the database.
 * Uses pgvector similarity search.
 */
@Service
public class RetrievalService {
    private static final Logger logger = LoggerFactory.getLogger(RetrievalService.class);
    
    private final ChunkRepository chunkRepository;
    private final OpenAiClient openAiClient;

    public RetrievalService(ChunkRepository chunkRepository, OpenAiClient openAiClient) {
        this.chunkRepository = chunkRepository;
        this.openAiClient = openAiClient;
    }

    /**
     * Retrieve top-K most relevant evidence chunks for a given question.
     * 
     * @param question The user's question
     * @param topK Number of chunks to retrieve
     * @return List of evidence chunks sorted by relevance
     */
    public List<EvidenceChunk> retrieveEvidence(String question, int topK) {
        logger.info("Retrieving {} evidence chunks for question: {}", topK, question);

        // Step 1: Generate embedding for the question
        List<Double> questionEmbedding = openAiClient.generateEmbedding(question);
        logger.debug("Question embedding generated, dimension: {}", questionEmbedding.size());

        // Step 2: Convert to vector string representation and search
        String embeddingVector = convertToVectorString(questionEmbedding);
        List<EvidenceChunk> chunks = chunkRepository.findTopKSimilarChunks(embeddingVector, topK);
        logger.info("Retrieved {} evidence chunks", chunks.size());

        // Step 3: Enrich with proper citation format
        return chunks.stream()
            .map(chunk -> new EvidenceChunk(
                chunk.chunkId(),
                chunk.docId(),
                chunk.pageNo(),
                chunk.pageStart(),
                chunk.pageEnd(),
                chunk.text(),
                chunk.similarity(),
                formatCitation(chunk.docId(), chunk.chunkId(), chunk.pageStart(), chunk.pageEnd())
            ))
            .toList();
    }

    /**
     * Convert a list of doubles to pgvector string format: "[d1,d2,d3,...]"
     */
    private String convertToVectorString(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Format citation token for evidence chunk.
     */
    private String formatCitation(Long docId, Long chunkId, Integer pageStart, Integer pageEnd) {
        return String.format("[CIT doc=%d chunk=%d p=%d-%d]", docId, chunkId, pageStart, pageEnd);
    }
}

