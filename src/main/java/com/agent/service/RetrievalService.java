package com.agent.service;

import com.agent.model.EvidenceChunk;
import com.agent.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Service for retrieving relevant evidence chunks from the database.
 * Supports both vector-only and hybrid (vector + keyword) search modes.
 */
@Service
public class RetrievalService {
    private static final Logger logger = LoggerFactory.getLogger(RetrievalService.class);
    
    private final ChunkRepository chunkRepository;
    private final OpenAiClient openAiClient;
    
    @Value("${hybrid.enabled:true}")
    private boolean hybridEnabled;
    
    @Value("${hybrid.top-k-vector:10}")
    private int topKVector;
    
    @Value("${hybrid.top-k-keyword:10}")
    private int topKKeyword;
    
    @Value("${hybrid.alpha:0.6}")
    private double hybridAlpha;

    public RetrievalService(ChunkRepository chunkRepository, OpenAiClient openAiClient) {
        this.chunkRepository = chunkRepository;
        this.openAiClient = openAiClient;
    }

    /**
     * Retrieve top-K most relevant evidence chunks for a given question.
     * Uses hybrid search (vector + keyword) by default if configured.
     * 
     * @param question The user's question
     * @param topK Number of chunks to retrieve
     * @return List of evidence chunks sorted by relevance
     */
    public List<EvidenceChunk> retrieveEvidence(String question, int topK) {
        logger.info("Retrieving {} evidence chunks for question: {}", topK, question);

        if (hybridEnabled) {
            return hybridSearch(question, topK);
        } else {
            return vectorOnlySearch(question, topK);
        }
    }

    /**
     * Hybrid search: Merge vector and keyword search results with score fusion.
     * 
     * Algorithm:
     * 1. Retrieve topKVector results from vector search
     * 2. Retrieve topKKeyword results from keyword search
     * 3. Merge by chunk_id (dedupe)
     * 4. Normalize scores to [0,1] within each candidate list
     * 5. Compute finalScore = alpha * vectorNorm + (1-alpha) * keywordNorm
     * 6. Sort by finalScore desc and return top-K
     * 
     * @param question The user's question
     * @param topK Number of chunks to return
     * @return List of merged and reranked evidence chunks
     */
    private List<EvidenceChunk> hybridSearch(String question, int topK) {
        logger.debug("Using hybrid search: topKVector={}, topKKeyword={}, alpha={}", 
                    topKVector, topKKeyword, hybridAlpha);

        // Step 1: Generate embedding for vector search
        List<Double> questionEmbedding = openAiClient.generateEmbedding(question);
        String embeddingVector = convertToVectorString(questionEmbedding);

        // Step 2: Execute vector search
        logger.debug("Executing vector search");
        List<EvidenceChunk> vectorResults = chunkRepository.searchByVector(embeddingVector, topKVector);
        logger.debug("Vector search returned {} results", vectorResults.size());

        // Step 3: Execute keyword search
        logger.debug("Executing keyword search");
        List<EvidenceChunk> keywordResults = chunkRepository.searchByKeyword(question, topKKeyword);
        logger.debug("Keyword search returned {} results", keywordResults.size());

        // Step 4: Merge results by chunk_id
        Map<Long, EvidenceChunk> mergedMap = mergeResults(vectorResults, keywordResults);
        logger.debug("After merge: {} unique chunks", mergedMap.size());

        // Step 5: Normalize and fuse scores
        List<EvidenceChunk> fusedChunks = normalizeAndFuseScores(new ArrayList<>(mergedMap.values()));

        // Step 6: Sort by finalScore and return top-K with citations
        return fusedChunks.stream()
            .sorted((a, b) -> Double.compare(b.finalScore(), a.finalScore()))
            .limit(topK)
            .map(chunk -> new EvidenceChunk(
                chunk.chunkId(),
                chunk.docId(),
                chunk.pageNo(),
                chunk.pageStart(),
                chunk.pageEnd(),
                chunk.text(),
                chunk.finalScore(),  // Use finalScore as similarity
                formatCitation(chunk.docId(), chunk.chunkId(), chunk.pageNo()),
                chunk.vectorScore(),
                chunk.keywordScore(),
                chunk.finalScore()
            ))
            .toList();
    }

    /**
     * Vector-only search (fallback when hybrid disabled).
     */
    private List<EvidenceChunk> vectorOnlySearch(String question, int topK) {
        logger.debug("Using vector-only search");

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
                formatCitation(chunk.docId(), chunk.chunkId(), chunk.pageNo()),
                null,
                null,
                null
            ))
            .toList();
    }

    /**
     * Merge vector and keyword search results by chunk_id.
     * If a chunk appears in both results, both scores are preserved.
     */
    private Map<Long, EvidenceChunk> mergeResults(List<EvidenceChunk> vectorResults,
                                                  List<EvidenceChunk> keywordResults) {
        Map<Long, EvidenceChunk> merged = new LinkedHashMap<>();

        // Add all vector results
        for (EvidenceChunk chunk : vectorResults) {
            merged.put(chunk.chunkId(), chunk);
        }

        // Merge keyword results
        for (EvidenceChunk keywordChunk : keywordResults) {
            Long chunkId = keywordChunk.chunkId();
            if (merged.containsKey(chunkId)) {
                // Chunk appears in both: merge scores
                EvidenceChunk existing = merged.get(chunkId);
                EvidenceChunk mergedChunk = new EvidenceChunk(
                    existing.chunkId(),
                    existing.docId(),
                    existing.pageNo(),
                    existing.pageStart(),
                    existing.pageEnd(),
                    existing.text(),
                    existing.similarity(),
                    existing.citations(),
                    existing.vectorScore(),
                    keywordChunk.keywordScore(),  // Update with keyword score
                    null  // finalScore computed later
                );
                merged.put(chunkId, mergedChunk);
            } else {
                // Chunk only in keyword results
                merged.put(chunkId, keywordChunk);
            }
        }

        return merged;
    }

    /**
     * Normalize scores to [0,1] and compute final hybrid score using alpha blending.
     * Uses min-max normalization within the candidate set.
     */
    private List<EvidenceChunk> normalizeAndFuseScores(List<EvidenceChunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        // Find min/max for normalization
        double vectorMin = Double.MAX_VALUE, vectorMax = Double.MIN_VALUE;
        double keywordMin = Double.MAX_VALUE, keywordMax = Double.MIN_VALUE;
        boolean hasVector = false, hasKeyword = false;

        for (EvidenceChunk chunk : chunks) {
            if (chunk.vectorScore() != null) {
                hasVector = true;
                vectorMin = Math.min(vectorMin, chunk.vectorScore());
                vectorMax = Math.max(vectorMax, chunk.vectorScore());
            }
            if (chunk.keywordScore() != null) {
                hasKeyword = true;
                keywordMin = Math.min(keywordMin, chunk.keywordScore());
                keywordMax = Math.max(keywordMax, chunk.keywordScore());
            }
        }

        // Make variables effectively final for lambda
        final double fVectorMin = vectorMin;
        final double fVectorMax = vectorMax;
        final double fKeywordMin = keywordMin;
        final double fKeywordMax = keywordMax;
        final boolean fHasVector = hasVector;
        final boolean fHasKeyword = hasKeyword;

        // Normalize and fuse
        return chunks.stream()
            .map(chunk -> {
                double vectorNorm = 0.0;
                double keywordNorm = 0.0;

                // Normalize vector score
                if (chunk.vectorScore() != null && fHasVector) {
                    vectorNorm = (fVectorMax > fVectorMin) 
                        ? (chunk.vectorScore() - fVectorMin) / (fVectorMax - fVectorMin)
                        : (chunk.vectorScore() > 0 ? 1.0 : 0.5);
                }

                // Normalize keyword score
                if (chunk.keywordScore() != null && fHasKeyword) {
                    keywordNorm = (fKeywordMax > fKeywordMin)
                        ? (chunk.keywordScore() - fKeywordMin) / (fKeywordMax - fKeywordMin)
                        : (chunk.keywordScore() > 0 ? 1.0 : 0.5);
                }

                // Alpha-weighted hybrid score
                double finalScore = hybridAlpha * vectorNorm + (1.0 - hybridAlpha) * keywordNorm;

                return new EvidenceChunk(
                    chunk.chunkId(),
                    chunk.docId(),
                    chunk.pageNo(),
                    chunk.pageStart(),
                    chunk.pageEnd(),
                    chunk.text(),
                    finalScore,
                    chunk.citations(),
                    vectorNorm,
                    keywordNorm,
                    finalScore
                );
            })
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
    private String formatCitation(Long docId, Long chunkId, Integer pageNo) {
        return String.format("[CIT doc=%d chunk=%d p=%d-%d]", docId, chunkId, pageNo, pageNo);
    }
}

