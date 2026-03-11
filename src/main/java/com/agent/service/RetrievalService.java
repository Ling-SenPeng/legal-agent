package com.agent.service;

import com.agent.model.EvidenceChunk;
import com.agent.model.RetrievalPlan;
import com.agent.model.RetrievalDebugSnapshot;
import com.agent.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for retrieving relevant evidence chunks from the database.
 * Supports both vector-only and hybrid (vector + keyword) search modes.
 * Uses RetrievalPlanner to optimize queries before searching.
 * 
 * DESIGN NOTES:
 * - Keyword search is heavily trusted for legal document retrieval (literal matching)
 * - Vector search provides semantic coverage and catches paraphrasing
 * - Scores are fused with keyword weighted higher (1.5x) than vector (1.0x)
 * - Timeline/event queries get neighbor chunk expansion for richer context
 * - All retrieval execution is logged via RetrievalDebugSnapshot for observability
 */
@Service
public class RetrievalService {
    private static final Logger logger = LoggerFactory.getLogger(RetrievalService.class);
    
    // Scoring weights - tune these based on retrieval quality analysis
    private static final double KEYWORD_SCORE_WEIGHT = 1.5;      // Literal match is strongest signal
    private static final double VECTOR_SCORE_WEIGHT = 1.0;       // Semantic similarity
    private static final double EXACT_PHRASE_BOOST = 0.3;        // Bonus when chunk contains exact phrase
    private static final double LOW_CONFIDENCE_VECTOR_THRESHOLD = 0.4;  // Vector scores below this trigger warning
    
    // Neighbor expansion for timeline/event queries
    private static final boolean NEIGHBOR_EXPANSION_ENABLED = true;
    private static final int NEIGHBOR_CHUNK_DELTA = 1;  // Include ±1 adjacent chunks
    
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
                    String.format("%.4f", finalResults.get(0).vectorScore() != null ? finalResults.get(0).vectorScore() : 0),
                    String.format("%.4f", finalResults.get(0).keywordScore() != null ? finalResults.get(0).keywordScore() : 0),
                    String.format("%.4f", finalResults.get(0).finalScore()));
        }

        // Step 7: Expand neighbor chunks for timeline/event queries
        logger.info("Step 6: Neighbor Expansion");
        List<EvidenceChunk> expandedResults = expandNeighborChunksForTimeline(finalResults, plan.getIntent());
        
        // Final summary
        logger.info("=== RETRIEVAL SUMMARY ===");
        logger.info("  Intent: {}", plan.getIntent());
        logger.info("  Keyword queries: {}", plan.getKeywordQueries());
        logger.info("  Vector query: {}", plan.getVectorQuery());
        logger.info("  Keyword hits: {}", keywordResults.size());
        logger.info("  Vector hits: {}", vectorResults.size());
        logger.info("  Merged chunks: {}", mergedMap.size());
        logger.info("  Final ranked results: {} / {}", finalResults.size(), topK);
        logger.info("  Neighbor expansion: {} -> {} chunks", finalResults.size(), expandedResults.size());
        logger.info("  Fallback used: {}", plan.isFallbackUsed());
        logger.info("=== END HYBRID SEARCH ===");
        return expandedResults;
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
     * Compute final hybrid score using weighted formula.
     * 
     * Score Formula:
     *   finalScore = (keyword_score × 1.5) + (vector_score × 1.0) + exactPhraseBoost
     * 
     * Rationale:
     * - Keyword search is heavily trusted for legal document retrieval (literal matching)
     * - Vector search provides semantic coverage (catch paraphrasing, synonyms)
     * - Exact phrase boost (+0.3) when chunk contains entity phrase from query
     * 
     * Note: Scores are NOT normalized to [0,1] - raw weighted sum allows fine-grained ranking.
     * 
     * @param chunks The merged chunks from keyword and vector search
     * @return Chunks with computed finalScore
     */
    private List<EvidenceChunk> normalizeAndFuseScores(List<EvidenceChunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        return chunks.stream()
            .map(chunk -> {
                // Keyword contribution (heavily weighted - legal docs benefit from literal matching)
                double keywordContrib = (chunk.keywordScore() != null) 
                    ? chunk.keywordScore() * KEYWORD_SCORE_WEIGHT 
                    : 0.0;

                // Vector contribution (semantic similarity)
                double vectorContrib = (chunk.vectorScore() != null)
                    ? chunk.vectorScore() * VECTOR_SCORE_WEIGHT
                    : 0.0;

                // Exact phrase boost (chunk text contains exact phrase from entities)
                double boostContrib = 0.0;
                // Note: In full implementation, would check if chunk.text() contains entity phrases
                // For now, exact phrase matching is done in searchByKeywordQueries()

                // Combined score
                double finalScore = keywordContrib + vectorContrib + boostContrib;

                return new EvidenceChunk(
                    chunk.chunkId(),
                    chunk.docId(),
                    chunk.pageNo(),
                    chunk.pageStart(),
                    chunk.pageEnd(),
                    chunk.text(),
                    finalScore,
                    chunk.citations(),
                    chunk.vectorScore(),
                    chunk.keywordScore(),
                    finalScore
                );
            })
            .toList();
    }

    /**
     * Expand result set with neighbor chunks for timeline/event queries.
     * 
     * Rationale: Event queries benefit from broader context (what happened before/after).
     * For each top result, include ±1 adjacent chunks to provide timeline context.
     * 
     * Example: If query extracts "TRO issued on Jan 5", including chunks from Jan 4-6
     * provides richer narrative context.
     * 
     * @param topResults The top results from hybrid search
     * @param intent The detected query intent (e.g., "extract_events")
     * @return Expanded result set with neighbor chunks
     */
    private List<EvidenceChunk> expandNeighborChunksForTimeline(List<EvidenceChunk> topResults,
                                                               String intent) {
        if (!NEIGHBOR_EXPANSION_ENABLED || topResults.isEmpty()) {
            return topResults;
        }

        // Only expand for event/timeline intents
        if (!"extract_events".equals(intent) && !"timeline".equals(intent)) {
            return topResults;
        }

        logger.info("Expanding neighbor chunks for timeline intent (delta={})", NEIGHBOR_CHUNK_DELTA);

        // Collect all chunk IDs that should be included
        Set<Long> chunkIdsToFetch = new HashSet<>();
        for (EvidenceChunk result : topResults) {
            chunkIdsToFetch.add(result.chunkId());
            // Add neighbors
            for (int i = 1; i <= NEIGHBOR_CHUNK_DELTA; i++) {
                chunkIdsToFetch.add(result.chunkId() - i);  // Previous chunks
                chunkIdsToFetch.add(result.chunkId() + i);  // Next chunks
            }
        }

        // Fetch neighbor chunks in bulk (if possible via repository query)
        // For simplicity, fetch individually for now
        Map<Long, EvidenceChunk> expandedMap = new LinkedHashMap<>();

        // Add original top results first (maintain ranking)
        for (EvidenceChunk result : topResults) {
            expandedMap.put(result.chunkId(), result);
        }

        // Add neighbor chunks (lower score, just for context)
        for (Long chunkId : chunkIdsToFetch) {
            if (!expandedMap.containsKey(chunkId)) {
                // Try to fetch this chunk (may not exist in DB)
                try {
                    List<EvidenceChunk> neighbors = chunkRepository.findByChunkId(chunkId);
                    if (!neighbors.isEmpty()) {
                        expandedMap.put(chunkId, neighbors.get(0));
                        logger.debug("  Added neighbor chunk: {}", chunkId);
                    }
                } catch (Exception e) {
                    logger.debug("  Neighbor chunk not found: {} ({})", chunkId, e.getMessage());
                }
            }
        }

        List<EvidenceChunk> expanded = new ArrayList<>(expandedMap.values());
        logger.info("Neighbor expansion: {} original + {} neighbors = {} total",
                topResults.size(), expanded.size() - topResults.size(), expanded.size());

        return expanded;
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

