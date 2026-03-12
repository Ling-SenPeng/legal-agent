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
        
        // Log structured plan details
        logger.info("[RETRIEVAL_PLAN] originalQuery=\"{}\" intent={} outputFormat={} entities={} keywordQueries={} entities_count={}",
            question,
            plan.getIntent(),
            plan.getOutputFormat(),
            plan.getEntities(),
            plan.getKeywordQueries().size(),
            plan.getEntities().size());
        
        logger.debug("[RETRIEVAL_PLAN_DETAIL] cleanedQuery=\"{}\" vectorQuery=\"{}\" answerInstruction=\"{}\"",
            plan.getCleanedQuery(),
            plan.getVectorQuery(),
            plan.getAnswerInstruction());

        if (hybridEnabled) {
            return hybridSearch(plan, topK);
        } else {
            return vectorOnlySearch(plan, topK);
        }
    }

    /**
     * Retrieve evidence using a pre-planned query without running the generic retrieval planner.
     * Designed for callers (like CASE_ANALYSIS) that have already optimized their queries
     * and don't need the generic planner re-optimizing them.
     * 
     * Uses the provided query directly as both vector and keyword query,
     * constructing a minimal RetrievalPlan with no additional planning.
     * 
     * @param preplannedQuery The already-optimized query (not subject to planning)
     * @param topK Number of chunks to retrieve
     * @return List of evidence chunks sorted by relevance
     */
    public List<EvidenceChunk> retrieveEvidenceWithoutPlanning(String preplannedQuery, int topK) {
        logger.info("RetrievalService: Retrieving {} evidence chunks (pre-planned query, skipping planning phase)", topK);
        logger.debug("Pre-planned query: '{}'", preplannedQuery);
        
        // Create a minimal retrieval plan without running the planner
        // This preserves the pre-optimized query as-is for both vector and keyword search
        RetrievalPlan plan = new RetrievalPlan(
            preplannedQuery,                          // originalQuery
            preplannedQuery,                          // cleanedQuery (same - already cleaned by caller)
            "PRE-PLANNED",                            // intent marker
            List.of(),                                // entities (not needed for pre-planned)
            List.of(preplannedQuery),                 // keywordQueries (use query as single keyword query)
            preplannedQuery,                          // vectorQuery (use query directly)
            "",                                       // answerInstruction (not used in CASE_ANALYSIS)
            ""                                        // outputFormat (not used in CASE_ANALYSIS)
        );
        
        logger.debug("[RETRIEVAL_PLAN_PREPLANNED] query=\"{}\" (skipped generic planner)", preplannedQuery);

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
        logger.info("  TopK Vector: {}, TopK Keyword: {}", topKVector, topKKeyword);

        // Step 1: Generate embedding for vector search using planned vector query
        logger.info("Step 1: Vector Search");
        
        // Use vectorQuery from plan, fallback to original query if blank
        String vectorQuery = plan.getVectorQuery();
        if (vectorQuery == null || vectorQuery.trim().isEmpty()) {
            logger.warn("[VECTOR_FALLBACK] VectorQuery is blank, falling back to original query: {}", plan.getOriginalQuery());
            vectorQuery = plan.getOriginalQuery();
        }
        logger.info("  Vector query: {}", vectorQuery);
        
        List<Double> questionEmbedding = openAiClient.generateEmbedding(vectorQuery);
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

        // Step 5: Compute final scores with exact phrase boost
        logger.info("Step 4: Final Score Computation");
        List<EvidenceChunk> fusedChunks = computeFinalScores(new ArrayList<>(mergedMap.values()), plan.getEntities());
        logger.debug("  Scoring: keyword={} + vector={} + phrase_boost={}",
                KEYWORD_SCORE_WEIGHT, VECTOR_SCORE_WEIGHT, EXACT_PHRASE_BOOST);

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
        NeighborExpansionResult expansionResult = expandNeighborChunksForTimeline(finalResults, plan);
        List<EvidenceChunk> expandedResults = expansionResult.expandedChunks;
        
        // Create and log structured debug snapshot
        RetrievalDebugSnapshot snapshot = createDebugSnapshot(plan, keywordResults.size(),
                vectorResults.size(), mergedMap.size(), finalResults);
        snapshot.setNeighborExpansionUsed(expansionResult.wasExpanded);
        snapshot.setVectorFallbackUsed(vectorResults.isEmpty() && plan.isFallbackUsed());
        logDebugSnapshot(snapshot);
        
        // Final summary
        logger.info("=== RETRIEVAL SUMMARY ===");
        logger.info("  Intent: {}", plan.getIntent());
        logger.info("  Keyword hits: {} (fallback: {})", keywordResults.size(), plan.isFallbackUsed());
        logger.info("  Vector hits: {}", vectorResults.size());
        logger.info("  Merged chunks: {}", mergedMap.size());
        logger.info("  Final ranked results: {} / {}", finalResults.size(), topK);
        logger.info("  Expanded with neighbors: {} -> {} chunks", finalResults.size(), expandedResults.size());
        logger.info("  Output format: {}", plan.getOutputFormat());
        logger.info("=== END HYBRID SEARCH ===");
        return expandedResults;
    }

    /**
     * Vector-only search (fallback when hybrid disabled).
     */
    private List<EvidenceChunk> vectorOnlySearch(RetrievalPlan plan, int topK) {
        logger.debug("Using vector-only search with planned vector query");

        // Step 1: Generate embedding for the planned vector query
        logger.debug("Generating embedding for vector query: {}", plan.getVectorQuery());
        List<Double> questionEmbedding = openAiClient.generateEmbedding(plan.getVectorQuery());
        logger.debug("Embedding generated, dimension: {}", questionEmbedding.size());

        // Step 2: Convert to vector string representation and search
        String embeddingVector = convertToVectorString(questionEmbedding);
        List<EvidenceChunk> chunks = chunkRepository.findTopKSimilarChunks(embeddingVector, topK);
        logger.info("Retrieved {} evidence chunks via vector-only search", chunks.size());

        // Step 3: Create debug snapshot
        RetrievalDebugSnapshot snapshot = createDebugSnapshot(plan, 0, chunks.size(), chunks.size(), chunks);
        snapshot.setVectorFallbackUsed(false);
        logDebugSnapshot(snapshot);

        // Step 4: Enrich with proper citation format
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
            logger.warn("[KEYWORD_FALLBACK] Planned queries returned 0 hits, trying cleaned query: \"{}\"", cleanedQuery);
            plan.setFallbackUsed(true);
            
            logger.debug("Keyword Search - Fallback Attempt 1: Cleaned query");
            List<EvidenceChunk> fallbackResults = chunkRepository.searchByKeyword(cleanedQuery, topK);
            logger.debug("Cleaned query returned {} hits", fallbackResults.size());
            totalAttempts++;
            
            for (EvidenceChunk chunk : fallbackResults) {
                combinedResults.put(chunk.chunkId(), chunk);
            }
        }

        // Step 3: Fallback to original query if still no results
        if (combinedResults.isEmpty() && originalQuery != null && !originalQuery.trim().isEmpty()) {
            logger.warn("[KEYWORD_FALLBACK] Cleaned query returned 0 hits, trying original query: \"{}\"", originalQuery);
            plan.setFallbackUsed(true);
            
            logger.debug("Keyword Search - Fallback Attempt 2: Original query");
            List<EvidenceChunk> fallbackResults = chunkRepository.searchByKeyword(originalQuery, topK);
            logger.debug("Original query returned {} hits", fallbackResults.size());
            totalAttempts++;
            
            for (EvidenceChunk chunk : fallbackResults) {
                combinedResults.put(chunk.chunkId(), chunk);
            }
        }

        // Return up to topK combined unique results
        List<EvidenceChunk> finalResults = combinedResults.values().stream()
                .limit(topK)
                .toList();
        
        // Log structured keyword search summary
        logger.debug("[KEYWORD_SEARCH_RESULT] queries_attempted={} unique_chunks={} fallback_used={} avg_score={}",
            totalAttempts,
            finalResults.size(),
            plan.isFallbackUsed(),
            finalResults.stream()
                .mapToDouble(ch -> ch.keywordScore() != null ? ch.keywordScore() : 0)
                .average()
                .orElse(0.0));

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
     * Compute final hybrid score using weighted formula with exact phrase boost.
     * 
     * Score Formula:
     *   finalScore = (keyword_score × 1.5) + (vector_score × 1.0) + exactPhraseBoost
     * 
     * Rationale:
     * - Keyword search is heavily trusted for legal document retrieval (literal matching)
     * - Vector search provides semantic coverage (catch paraphrasing, synonyms)
     * - Exact phrase boost (+0.3) when chunk contains expanded entity phrases
     * 
     * Exact phrase matching:
     * Check if chunk text (case-insensitive) contains any of the expanded entity phrases.
     * Expanded entities include original terms plus synonyms (e.g., "TRO" also matches "temporary restraining order").
     * 
     * Note: Scores are NOT normalized to [0,1] - raw weighted sum allows fine-grained ranking.
     * 
     * @param chunks The merged chunks from keyword and vector search
     * @param entities List of extracted entities (original + expanded synonyms)
     * @return Chunks with computed finalScore
     */
    private List<EvidenceChunk> computeFinalScores(List<EvidenceChunk> chunks, List<String> entities) {
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

                // Exact phrase boost: check if chunk text contains any expanded entity phrase
                double boostContrib = 0.0;
                if (entities != null && !entities.isEmpty() && chunk.text() != null) {
                    String chunkTextLower = chunk.text().toLowerCase();
                    boolean hasExactPhrase = entities.stream()
                        .anyMatch(entity -> chunkTextLower.contains(entity.toLowerCase()));
                    if (hasExactPhrase) {
                        boostContrib = EXACT_PHRASE_BOOST;
                        logger.debug("Exact phrase boost applied to chunk {}: text contains entity phrase", chunk.chunkId());
                    }
                }

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
     * Result from neighbor chunk expansion with metadata.
     */
    private static class NeighborExpansionResult {
        final List<EvidenceChunk> expandedChunks;
        final boolean wasExpanded;
        final int originalCount;
        final int neighborsAdded;

        NeighborExpansionResult(List<EvidenceChunk> expandedChunks, boolean wasExpanded,
                               int originalCount, int neighborsAdded) {
            this.expandedChunks = expandedChunks;
            this.wasExpanded = wasExpanded;
            this.originalCount = originalCount;
            this.neighborsAdded = neighborsAdded;
        }
    }

    /**
     * Expand result set with neighbor chunks for timeline/event queries.
     * 
     * Activation conditions (checked in priority order):
     * 1. NEIGHBOR_EXPANSION_ENABLED constant is true
     * 2. RetrievalPlan intent is "extract_events" OR outputFormat is "timeline"
     * 
     * Deduplication:
     * - Maintain original top results first (preserves ranking)
     * - Add neighbors only if not already in result set
     * - Uses LinkedHashMap to preserve insertion order
     * 
     * @param topResults The top-K ranked chunks from hybrid search
     * @param plan The retrieval plan (contains intent and outputFormat)
     * @return NeighborExpansionResult with expanded chunks and metadata
     */
    private NeighborExpansionResult expandNeighborChunksForTimeline(List<EvidenceChunk> topResults,
                                                                   RetrievalPlan plan) {
        // Check if expansion is enabled and applicable
        if (!NEIGHBOR_EXPANSION_ENABLED || topResults.isEmpty()) {
            return new NeighborExpansionResult(topResults, false, topResults.size(), 0);
        }

        // Only expand for event/timeline queries (check both intent and outputFormat)
        String intent = plan.getIntent();
        String outputFormat = plan.getOutputFormat();
        boolean isEventQuery = "extract_events".equals(intent) || "timeline".equals(outputFormat);
        
        if (!isEventQuery) {
            logger.debug("Neighbor expansion not triggered: intent={}, format={}", intent, outputFormat);
            return new NeighborExpansionResult(topResults, false, topResults.size(), 0);
        }

        logger.info("[NEIGHBOR_EXPANSION] Activated for {} (intent={}, format={}, delta={})",
            "timeline/event query", intent, outputFormat, NEIGHBOR_CHUNK_DELTA);

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

        // Build expanded result set with deduplication
        Map<Long, EvidenceChunk> expandedMap = new LinkedHashMap<>();

        // Step 1: Add original top results first (maintain ranking)
        for (EvidenceChunk result : topResults) {
            expandedMap.put(result.chunkId(), result);
        }

        // Step 2: Add neighbor chunks if not already present
        int neighborsAdded = 0;
        for (Long chunkId : chunkIdsToFetch) {
            if (!expandedMap.containsKey(chunkId) && !chunkId.equals(-1L)) {
                // Try to fetch this chunk (may not exist in DB)
                try {
                    List<EvidenceChunk> neighbors = chunkRepository.findByChunkId(chunkId);
                    if (!neighbors.isEmpty()) {
                        expandedMap.put(chunkId, neighbors.get(0));
                        neighborsAdded++;
                        logger.debug("  Added neighbor chunk: {}", chunkId);
                    }
                } catch (Exception e) {
                    logger.debug("  Neighbor chunk not found: {}", chunkId);
                }
            }
        }

        List<EvidenceChunk> expanded = new ArrayList<>(expandedMap.values());
        logger.info("[NEIGHBOR_EXPANSION_RESULT] original={} neighbors_added={} total={}",
                topResults.size(), neighborsAdded, expanded.size());

        return new NeighborExpansionResult(expanded, neighborsAdded > 0, topResults.size(), neighborsAdded);
    }

    /**
     * Create and populate a RetrievalDebugSnapshot for structured logging.
     * Captures all retrieval metadata and execution metrics.
     * 
     * @param plan The retrieval plan
     * @param keywordHits Number of keyword search results
     * @param vectorHits Number of vector search results
     * @param mergedHits Number of merged unique chunks
     * @param finalResults The top-K ranked results
     * @return Populated snapshot ready for logging
     */
    private RetrievalDebugSnapshot createDebugSnapshot(RetrievalPlan plan, int keywordHits,
                                                      int vectorHits, int mergedHits,
                                                      List<EvidenceChunk> finalResults) {
        RetrievalDebugSnapshot snapshot = new RetrievalDebugSnapshot(
            plan.getOriginalQuery(),
            plan.getCleanedQuery(),
            plan.getIntent(),
            plan.getOutputFormat(),
            plan.getEntities(),
            plan.getKeywordQueries(),
            plan.getVectorQuery()
        );

        // Execution metrics
        snapshot.setKeywordHits(keywordHits);
        snapshot.setVectorHits(vectorHits);
        snapshot.setMergedHits(mergedHits);
        snapshot.setKeywordFallbackUsed(plan.isFallbackUsed());

        // Detect low confidence (weak vector scores)
        boolean lowConfidence = finalResults.stream()
            .anyMatch(ch -> ch.vectorScore() != null && ch.vectorScore() < LOW_CONFIDENCE_VECTOR_THRESHOLD);
        snapshot.setLowConfidenceDetected(lowConfidence);

        // Top results preview
        List<RetrievalDebugSnapshot.ResultPreview> previews = finalResults.stream()
            .limit(3)  // Top 3 for preview
            .map(ch -> new RetrievalDebugSnapshot.ResultPreview(
                ch.chunkId(),
                ch.docId(),
                ch.pageNo(),
                ch.keywordScore(),
                ch.vectorScore(),
                ch.finalScore(),
                truncateText(ch.text(), 100),  // 100 char preview
                null,  // matchedByKeywordQuery - could be enhanced
                false  // exactPhraseMatched - could be enhanced
            ))
            .toList();
        snapshot.setTopResults(previews);

        return snapshot;
    }

    /**
     * Log the retrieval debug snapshot in structured format.
     * INFO level: concise summary
     * DEBUG level: detailed chunk previews
     */
    private void logDebugSnapshot(RetrievalDebugSnapshot snapshot) {
        // INFO level: Structured summary
        logger.info("[RETRIEVAL SNAPSHOT] intent={} entities={} keyword_hits={} vector_hits={} merged_hits={} low_conf={} fallback={}",
            snapshot.getIntent(),
            snapshot.getEntities().size(),
            snapshot.getKeywordHits(),
            snapshot.getVectorHits(),
            snapshot.getMergedHits(),
            snapshot.isLowConfidenceDetected(),
            snapshot.isKeywordFallbackUsed());

        // DEBUG level: Detailed chunk information
        if (logger.isDebugEnabled() && !snapshot.getTopResults().isEmpty()) {
            logger.debug("[RETRIEVAL_CHUNKS] Top merged chunks:");
            for (int i = 0; i < snapshot.getTopResults().size(); i++) {
                RetrievalDebugSnapshot.ResultPreview preview = snapshot.getTopResults().get(i);
                logger.debug("  [{}] chunk_id={} page={} key_score={} vec_score={} final_score={} text=\"{}\"",
                    (i + 1),
                    preview.chunkId,
                    preview.pageNo,
                    formatScore(preview.keywordScore),
                    formatScore(preview.vectorScore),
                    formatScore(preview.finalScore),
                    preview.textPreview);
            }
        }
    }

    /**
     * Format a double score for logging (null-safe, 4 decimals)
     */
    private String formatScore(Double score) {
        return score == null ? "null" : String.format("%.4f", score);
    }

    /**
     * Truncate text to max length with ellipsis
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
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

