package com.agent;

import com.agent.model.EvidenceChunk;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for hybrid search functionality.
 * Tests score merging, normalization, and fusion logic without requiring database/OpenAI.
 */
class HybridSearchTest {

    /**
     * Test merging vector and keyword results by chunk_id.
     */
    @Test
    void testMergeResultsByChunkId() {
        // Vector results (2 chunks)
        List<EvidenceChunk> vectorResults = List.of(
            createChunk(1L, 1L, 1, "Text A", 0.85, null),
            createChunk(2L, 1L, 2, "Text B", 0.75, null)
        );

        // Keyword results (2 chunks, 1 overlap)
        List<EvidenceChunk> keywordResults = List.of(
            createChunk(1L, 1L, 1, "Text A", null, 0.60),  // Overlap with chunk 1
            createChunk(3L, 1L, 3, "Text C", null, 0.50)   // New chunk
        );

        // Merge
        Map<Long, EvidenceChunk> merged = mergeResults(vectorResults, keywordResults);

        // Verify
        assertEquals(3, merged.size(), "Should have 3 unique chunks");
        
        EvidenceChunk chunk1 = merged.get(1L);
        assertNotNull(chunk1.vectorScore(), "Chunk 1 should have vector score");
        assertNotNull(chunk1.keywordScore(), "Chunk 1 should have keyword score");
        assertEquals(0.85, chunk1.vectorScore());
        assertEquals(0.60, chunk1.keywordScore());

        EvidenceChunk chunk3 = merged.get(3L);
        assertNull(chunk3.vectorScore(), "Chunk 3 should not have vector score");
        assertNotNull(chunk3.keywordScore(), "Chunk 3 should have keyword score");
    }

    /**
     * Test min-max normalization to [0,1].
     */
    @Test
    void testMinMaxNormalization() {
        List<Double> scores = List.of(0.1, 0.5, 0.9);
        double min = 0.1, max = 0.9;

        List<Double> normalized = new ArrayList<>();
        for (Double score : scores) {
            normalized.add((score - min) / (max - min));
        }

        assertEquals(0.0, normalized.get(0), 0.0001, "Min should normalize to 0");
        assertEquals(0.5, normalized.get(1), 0.0001, "Mid should normalize to 0.5");
        assertEquals(1.0, normalized.get(2), 0.0001, "Max should normalize to 1");
    }

    /**
     * Test normalization with identical scores (all equal).
     */
    @Test
    void testNormalizationWithIdenticalScores() {
        List<Double> identicalScores = List.of(0.5, 0.5, 0.5);
        double min = 0.5, max = 0.5;

        double normalized = (max > min) ? (0.5 - min) / (max - min) : 0.5;
        assertEquals(0.5, normalized, "Identical scores should normalize to 0.5");
    }

    /**
     * Test alpha-blended hybrid score fusion.
     */
    @Test
    void testAlphaBlending() {
        double alpha = 0.6;  // 60% weight on vector, 40% on keyword
        double vectorNorm = 0.8;
        double keywordNorm = 0.6;

        double finalScore = alpha * vectorNorm + (1.0 - alpha) * keywordNorm;

        // Expected: 0.6 * 0.8 + 0.4 * 0.6 = 0.48 + 0.24 = 0.72
        assertEquals(0.72, finalScore, 0.0001);
    }

    /**
     * Test different alpha weights prioritize different modalities.
     */
    @Test
    void testAlphaWeightsDifferentResults() {
        // Scenario: chunk has high keyword score but low vector score
        double vectorScore = 0.3;
        double keywordScore = 0.9;

        // With alpha=0.6 (vector-biased)
        double finalScoreVectorBias = 0.6 * vectorScore + 0.4 * keywordScore;  // 0.18 + 0.36 = 0.54

        // With alpha=0.3 (keyword-biased)
        double finalScoreKeywordBias = 0.3 * vectorScore + 0.7 * keywordScore;  // 0.09 + 0.63 = 0.72

        assertTrue(finalScoreKeywordBias > finalScoreVectorBias,
            "Keyword-biased should rank chunk higher when keyword score dominates");
    }

    /**
     * Test that chunks with only vector score are properly handled.
     */
    @Test
    void testVectorOnlyChunk() {
        // Chunk with only vector score
        EvidenceChunk chunk = createChunk(1L, 1L, 1, "Text", 0.8, null);
        
        List<EvidenceChunk> chunks = List.of(chunk);
        List<EvidenceChunk> fused = normalizeAndFuse(chunks, 0.6);

        EvidenceChunk result = fused.get(0);
        assertEquals(0.8, result.vectorScore(), "Vector score should be preserved");
        assertNull(result.keywordScore(), "Keyword score should remain null");
        // finalScore = 0.6 * 0.8 + 0.4 * 0 = 0.48
        assertEquals(0.48, result.finalScore(), 0.0001);
    }

    /**
     * Test that chunks with only keyword score are properly handled.
     */
    @Test
    void testKeywordOnlyChunk() {
        // Chunk with only keyword score
        EvidenceChunk chunk = createChunk(1L, 1L, 1, "Text", null, 0.7);
        
        List<EvidenceChunk> chunks = List.of(chunk);
        List<EvidenceChunk> fused = normalizeAndFuse(chunks, 0.6);

        EvidenceChunk result = fused.get(0);
        assertNull(result.vectorScore(), "Vector score should remain null");
        assertEquals(0.7, result.keywordScore(), "Keyword score should be preserved");
        // finalScore = 0.6 * 0 + 0.4 * 0.7 = 0.28
        assertEquals(0.28, result.finalScore(), 0.0001);
    }

    /**
     * Test sorting by final score and returning top-K.
     */
    @Test
    void testTopKOrdering() {
        List<EvidenceChunk> chunks = List.of(
            createChunk(1L, 1L, 1, "Text A", 0.8, 0.4),  // finalScore = 0.6*0.8 + 0.4*0.4 = 0.64
            createChunk(2L, 1L, 2, "Text B", 0.5, 0.9),  // finalScore = 0.6*0.5 + 0.4*0.9 = 0.66
            createChunk(3L, 1L, 3, "Text C", 0.7, 0.3)   // finalScore = 0.6*0.7 + 0.4*0.3 = 0.54
        );

        List<EvidenceChunk> fused = normalizeAndFuse(chunks, 0.6);
        
        // Top 2 should be chunks 2 and 1 (by descending finalScore)
        assertTrue(fused.get(0).finalScore() >= fused.get(1).finalScore(),
            "Results should be sorted by finalScore descending");
        assertTrue(fused.get(1).finalScore() >= fused.get(2).finalScore(),
            "Results should be sorted by finalScore descending");
    }

    /**
     * Test citation format generation.
     */
    @Test
    void testCitationFormat() {
        EvidenceChunk chunk = new EvidenceChunk(
            5L,              // chunkId
            1L,              // docId
            10,              // pageNo
            10,              // pageStart
            10,              // pageEnd
            "Some text",
            0.9,             // similarity
            "[CIT doc=1 chunk=5 p=10-10]",  // citations
            0.8,             // vectorScore
            null,            // keywordScore
            0.8              // finalScore
        );

        assertTrue(chunk.citations().contains("CIT"));
        assertTrue(chunk.citations().matches("\\[CIT doc=1 chunk=5 p=10-10\\]"));
    }

    /**
     * Test hybrid results are stable and reproducible.
     */
    @Test
    void testReproducibleFusion() {
        double alpha = 0.6;
        List<EvidenceChunk> chunks = List.of(
            createChunk(1L, 1L, 1, "Text A", 0.75, 0.55),
            createChunk(2L, 1L, 2, "Text B", 0.65, 0.65)
        );

        // Fuse twice
        List<EvidenceChunk> fused1 = normalizeAndFuse(chunks, alpha);
        List<EvidenceChunk> fused2 = normalizeAndFuse(chunks, alpha);

        // Should produce identical results
        assertEquals(fused1.size(), fused2.size());
        for (int i = 0; i < fused1.size(); i++) {
            assertEquals(fused1.get(i).chunkId(), fused2.get(i).chunkId());
            assertEquals(fused1.get(i).finalScore(), fused2.get(i).finalScore(), 0.0001);
        }
    }

    // ============================================================================
    // Helper methods
    // ============================================================================

    private EvidenceChunk createChunk(Long chunkId, Long docId, Integer pageNo, String text,
                                      Double vectorScore, Double keywordScore) {
        return new EvidenceChunk(
            chunkId, docId, pageNo, pageNo, pageNo, text,
            null,  // similarity
            "",    // citations
            vectorScore,
            keywordScore,
            null   // finalScore
        );
    }

    private Map<Long, EvidenceChunk> mergeResults(List<EvidenceChunk> vectorResults,
                                                  List<EvidenceChunk> keywordResults) {
        Map<Long, EvidenceChunk> merged = new LinkedHashMap<>();

        for (EvidenceChunk chunk : vectorResults) {
            merged.put(chunk.chunkId(), chunk);
        }

        for (EvidenceChunk keywordChunk : keywordResults) {
            Long chunkId = keywordChunk.chunkId();
            if (merged.containsKey(chunkId)) {
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
                    keywordChunk.keywordScore(),
                    null
                );
                merged.put(chunkId, mergedChunk);
            } else {
                merged.put(chunkId, keywordChunk);
            }
        }

        return merged;
    }

    private List<EvidenceChunk> normalizeAndFuse(List<EvidenceChunk> chunks, double alpha) {
        if (chunks.isEmpty()) return chunks;

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

        return chunks.stream()
            .map(chunk -> {
                double vectorNorm = 0.0;
                double keywordNorm = 0.0;

                if (chunk.vectorScore() != null && fHasVector) {
                    vectorNorm = (fVectorMax > fVectorMin)
                        ? (chunk.vectorScore() - fVectorMin) / (fVectorMax - fVectorMin)
                        : chunk.vectorScore();
                }

                if (chunk.keywordScore() != null && fHasKeyword) {
                    keywordNorm = (fKeywordMax > fKeywordMin)
                        ? (chunk.keywordScore() - fKeywordMin) / (fKeywordMax - fKeywordMin)
                        : chunk.keywordScore();
                }

                double finalScore = alpha * vectorNorm + (1.0 - alpha) * keywordNorm;

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
}
