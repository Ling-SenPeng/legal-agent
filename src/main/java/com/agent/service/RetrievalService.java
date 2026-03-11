package com.agent.service;

import com.agent.model.EvidenceChunk;
import com.agent.model.RetrievalPlan;
import com.agent.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Service for retrieving relevant evidence chunks from the database.
 * Supports both vector-only and hybrid (vector + keyword) search modes.
 * Uses RetrievalPlanner to optimize queries before searching.
 */
@Service
public class RetrievalService {
    private static final Logger logger = LoggerFactory.getLogger(RetrievalService.class);
    
    private final ChunkRepository chunkRepository;
    private final OpenAiClient openAiClient;
    private final RetrievalPlanner retrievalPlanner;
    
    @Value("${hybrid.enabled:true}")
    private boolean hybridEnabled;
    
    @Value("${hybrid.top-k-vector:10}")
    private int topKVector;
    
    @Value("${hybrid.top-k-keyword:10}")
    private int topKKeyword;
    
    @Value("${hybrid.alpha:0.6}")
    private double hybridAlpha;

    public RetrievalService(ChunkRepository chunkRepository, OpenAiClient openAiClient,
                            RetrievalPlanner retrievalPlanner) {
        this.chunkRepository = chunkRepository;
        this.openAiClient = openAiClient;
        this.retrievalPlanner = retrievalPlanner;
    }

    /**
     * Retrieve top-K most relevant evidence chunks for a given question.
     * Uses RetrievalPlanner to optimize the query before searching.
     * Uses hybrid search (vector + keyword) by default if configured.
     * Returns the retrieval plan so it can be used by downstream stages (e.g., LLM drafting).
     * 
     * @param question The user's question
     * @param topK Number of chunks to retrieve
     * @return List of evidence chunks sorted by relevance
     */
    public List<EvidenceChunk> retrieveEvidence(String question, int topK) {
        logger.info("RetrievalService: Retrieving {} evidence chunks", topK);
        
        // Step 1: Generate retrieval plan
        RetrievalPlan plan = retrievalPlanner.plan(question);
        
        // Log full retrieval plan details
        logger.info("=== RETRIEVAL PLAN ===");
        logger.info("  Original query: {}", plan.getOriginalQuery());
        logger.info("  Cleaned query: {}", plan.getCleanedQuery());
        logger.info("  Detected intent: {}", plan.getIntent());
        logger.info("  Keyword queries: {}", plan.getKeywordQueries());
        logger.info("  Vector query: {}", plan.getVectorQuery());
        logger.info("  Output format: {}", plan.getOutputFormat());
        logger.info("  Answer instruction: {}", plan.getAnswerInstruction());

        if (hybridEnabled) {
            return hybridSearch(plan, topK);
        } else {
            return vectorOnlySearch(plan, topK);
        }
    }

    /**
     * Retrieve evidence with retrieval plan metadata.
     * Returns both the evidence chunks and the retrieval plan for downstream processing.
     * 
     * @param question The user's question
     * @param topK Number of chunks to retrieve
     * @return Object containing both evidence chunks and retrieval plan
     */
    public RetrievalPlan getRetrievalPlan(String question) {
        return retrievalPlanner.plan(question);
    }

    /**
     * Hybrid search: Merge vector and keyword search results with score fusion.
     * 
     * Algorithm:
     * 1. Retrieve topKVector results from vector search using planned vector query
     * 2. Retrieve topKKeyword results from keyword search using planned keyword queries
     * 3. Merge by chunk_id (dedupe)
     * 4. Normalize scores to [0,1] within each candidate list
     * 5. Compute finalScore = alpha * vectorNorm + (1-alpha) * keywordNorm
     * 6. Sort by finalScore desc and return top-K
     * 
     * @param plan The retrieval plan with optimized queries
     * @param topK Number of chunks to return
     * @return List of merged and reranked evidence chunks
     */
    private List<EvidenceChunk> hybridSearch(RetrievalPlan plan, int topK) {
        logger.info("=== HYBRID SEARCH EXECUTION ===");
        logger.info("  Mode: Hybrid (Vector + Keyword)");
        logger.info("  TopK Vector: {}, TopK Keyword: {}, Alpha: {}", topKVector, topKKeyword, hybridAlpha);

        // Step 1: Generate embedding for vector search using planned vector query
        logger.info("Step 1: Vector Search");
        logger.info("  Vector query: {}", plan.getVectorQuery());
        List<Double> questionEmbedding = openAiClient.generateEmbedding(plan.getVectorQuery());
        logger.debug("  Embedding dimension: {}", questionEmbedding.size());
        String embeddingVector = convertToVectorString(questionEmbedding);

        // Step 2: Execute vector search
        List<EvidenceChunk> vectorResults = chunkRepository.searchByVector(embeddingVector, topKVector);
        logger.info("  Vector search results: {} hits", vectorResults.size());
        if (!vectorResults.isEmpty()) {
            logger.debug("  Top vector hit - chunk: {}, score: {}", 
                    vectorResults.get(0).chunkId(), vectorResults.get(0).vectorScore());
        }

        // Step 3: Execute keyword search using planned keyword queries
        logger.info("Step 2: Keyword Search");
        logger.info("  Keyword queries ({} total): {}", plan.getKeywordQueries().size(), plan.getKeywordQueries());
        List<EvidenceChunk> keywordResults = searchByKeywordQueries(plan, topKKeyword);
        logger.info("  Keyword search results: {} hits (after dedup)", keywordResults.size());
        if (!keywordResults.isEmpty()) {
            logger.debug("  Top keyword hit - chunk: {}, score: {}", 
                    keywordResults.get(0).chunkId(), keywordResults.get(0).keywordScore());
        }

        // Step 4: Merge results by chunk_id
        logger.info("Step 3: Merge & Deduplication");
        Map<Long, EvidenceChunk> mergedMap = mergeResults(vectorResults, keywordResults);
        logger.info("  Merged unique chunks: {} (vector: {}, keyword: {}, overlap: {})",
                mergedMap.size(), vectorResults.size(), keywordResults.size(),
                vectorResults.size() + keywordResults.size() - mergedMap.size());

        // Step 5: Normalize and fuse scores
        logger.info("Step 4: Score Normalization & Fusion");
        List<EvidenceChunk> fusedChunks = normalizeAndFuseScores(new ArrayList<>(mergedMap.values()));
        logger.debug("  Score fusion alpha: {} (vector weight) + {} (keyword weight)",
                hybridAlpha, (1.0 - hybridAlpha));

        // Step 6: Sort by finalScore and return top-K with citations
        logger.info("Step 5: Final Ranking");
        List<EvidenceChunk> finalResults = fusedChunks.stream()
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
        
        logger.info("  Final ranked results: {} chunks (limit: {})", finalResults.size(), topK);
        if (!finalResults.isEmpty()) {
            logger.info("  Top result - chunk: {}, vector: {}, keyword: {}, final: {}",
                    finalResults.get(0).chunkId(),
                    String.format("%.4f", finalResults.get(0).vectorScore()),
                    String.format("%.4f", finalResults.get(0).keywordScore()),
                    String.format("%.4f", finalResults.get(0).finalScore()));
        }
        
        // Final summary
        logger.info("=== RETRIEVAL SUMMARY ===");
        logger.info("  Intent: {}", plan.getIntent());
        logger.info("  Keyword queries: {}", plan.getKeywordQueries());
        logger.info("  Vector query: {}", plan.getVectorQuery());
        logger.info("  Keyword hits: {}", keywordResults.size());
        logger.info("  Vector hits: {}", vectorResults.size());
        logger.info("  Merged chunks: {}", mergedMap.size());
        logger.info("  Final results: {} / {}", finalResults.size(), topK);
        logger.info("  Fallback used: {}", plan.isFallbackUsed());
        logger.info("=== END HYBRID SEARCH ===");
        return finalResults;
    }

    /**
     * Vector-only search (fallback when hybrid disabled).
     */
    private List<EvidenceChunk> vectorOnlySearch(RetrievalPlan plan, int topK) {
        logger.debug("Using vector-only search with planned vector query");

        // Step 1: Generate embedding for the planned vector query
        logger.debug("DEBUG: Generating embedding for vector query: {}", plan.getVectorQuery());
        List<Double> questionEmbedding = openAiClient.generateEmbedding(plan.getVectorQuery());
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
     * Perform keyword search with fallback logic.
     * Tries planned keyword queries first, then falls back to cleaned query, then original query.
     * Combines results from all queries, deduplicating by chunk_id.
     * 
     * @param plan The retrieval plan containing keyword queries and fallback values
     * @param topK Number of results per query
     * @return Combined and deduplicated keyword search results
     */
    private List<EvidenceChunk> searchByKeywordQueries(RetrievalPlan plan, int topK) {
        List<String> keywordQueries = plan.getKeywordQueries();
        String cleanedQuery = plan.getCleanedQuery();
        String originalQuery = plan.getOriginalQuery();
        
        logger.debug("searchByKeywordQueries: Starting keyword search");
        logger.debug("  Planned queries: {}", keywordQueries);
        logger.debug("  Cleaned query: {}", cleanedQuery);
        logger.debug("  Original query: {}", originalQuery);
        
        if (keywordQueries.isEmpty()) {
            logger.warn("No keyword queries provided, using fallback strategy");
            keywordQueries = List.of(cleanedQuery);
        }

        Map<Long, EvidenceChunk> combinedResults = new LinkedHashMap<>();
        int totalAttempts = 0;

        // Step 1: Try planned keyword queries
        logger.info("Keyword Search - Attempt 1: Planned queries");
        for (String query : keywordQueries) {
            logger.debug("  Executing query: {}", query);
            List<EvidenceChunk> results = chunkRepository.searchByKeyword(query, topK);
            logger.debug("  Results: {} hits", results.size());
            totalAttempts++;
            
            // Add results, keeping highest scoring version of each chunk
            for (EvidenceChunk chunk : results) {
                Long chunkId = chunk.chunkId();
                if (!combinedResults.containsKey(chunkId)) {
                    combinedResults.put(chunkId, chunk);
                } else {
                    // Keep the higher scoring version
                    EvidenceChunk existing = combinedResults.get(chunkId);
                    if (chunk.keywordScore() != null && existing.keywordScore() != null) {
                        if (chunk.keywordScore() > existing.keywordScore()) {
                            combinedResults.put(chunkId, chunk);
                        }
                    }
                }
            }
        }

        // Step 2: Fallback to cleaned query if no results
        if (combinedResults.isEmpty() && cleanedQuery != null && !cleanedQuery.trim().isEmpty()) {
            logger.warn("Keyword search returned 0 results. Falling back to cleaned query: {}", cleanedQuery);
            plan.setFallbackUsed(true);
            
            logger.info("Keyword Search - Attempt 2: Cleaned query fallback");
            logger.debug("  Executing query: {}", cleanedQuery);
            List<EvidenceChunk> fallbackResults = chunkRepository.searchByKeyword(cleanedQuery, topK);
            logger.debug("  Results: {} hits", fallbackResults.size());
            totalAttempts++;
            
            for (EvidenceChunk chunk : fallbackResults) {
                combinedResults.put(chunk.chunkId(), chunk);
            }
        }

        // Step 3: Fallback to original query if still no results
        if (combinedResults.isEmpty() && originalQuery != null && !originalQuery.trim().isEmpty()) {
            logger.warn("Keyword search still returned 0 results. Falling back to original query: {}", originalQuery);
            plan.setFallbackUsed(true);
            
            logger.info("Keyword Search - Attempt 3: Original query fallback");
            logger.debug("  Executing query: {}", originalQuery);
            List<EvidenceChunk> fallbackResults = chunkRepository.searchByKeyword(originalQuery, topK);
            logger.debug("  Results: {} hits", fallbackResults.size());
            totalAttempts++;
            
            for (EvidenceChunk chunk : fallbackResults) {
                combinedResults.put(chunk.chunkId(), chunk);
            }
        }

        // Return up to topK combined unique results
        List<EvidenceChunk> finalResults = combinedResults.values().stream()
                .limit(topK)
                .toList();
        
        logger.info("Keyword search completed: {} total hits, {} fallback used: {}",
                finalResults.size(), totalAttempts, plan.isFallbackUsed());

        return finalResults;
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

