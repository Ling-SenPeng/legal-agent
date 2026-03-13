package com.agent.repository;

import com.agent.model.EvidenceChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for querying PDF chunks with pgvector similarity search and full-text search.
 * Supports both vector-only and hybrid search modes.
 */
@Repository
public class ChunkRepository {
    
    private final JdbcTemplate jdbcTemplate;

    public ChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Vector search: Find top-K most similar chunks using pgvector cosine distance.
     * 
     * @param embeddingVector String representation of the vector (e.g., "[0.1,0.2,...]")
     * @param topK Number of top results to return
     * @return List of most similar evidence chunks with vectorScore populated
     */
    public List<EvidenceChunk> searchByVector(String embeddingVector, int topK) {
        String query = """
            SELECT 
                c.id as chunk_id,
                c.doc_id,
                d.file_name as filename,
                c.page_no,
                c.page_no as page_start,
                c.page_no as page_end,
                c.text,
                (1.0 - (c.embedding <=> ?::vector)) as vector_score
            FROM pdf_chunks c
            JOIN pdf_documents d ON c.doc_id = d.id
            WHERE c.embedding IS NOT NULL
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?
            """;

        return jdbcTemplate.query(
            query,
            (rs, rowNum) -> new EvidenceChunk(
                rs.getLong("chunk_id"),
                rs.getLong("doc_id"),
                rs.getString("filename"),
                rs.getInt("page_no"),
                rs.getInt("page_start"),
                rs.getInt("page_end"),
                rs.getString("text"),
                rs.getDouble("vector_score"),
                "",
                rs.getDouble("vector_score"),  // vectorScore
                null,                           // keywordScore
                null                            // finalScore (computed later)
            ),
            embeddingVector,
            embeddingVector,
            topK
        );
    }

    /**
     * Keyword search: Find top-K chunks matching a full-text search query.
     * Uses PostgreSQL full-text search with ts_rank_cd for ranking.
     * 
     * @param queryText The search query
     * @param topK Number of top results to return
     * @return List of text-matched evidence chunks with keywordScore populated
     */
    public List<EvidenceChunk> searchByKeyword(String queryText, int topK) {
        String query = """
            SELECT 
                c.id as chunk_id,
                c.doc_id,
                d.file_name as filename,
                c.page_no,
                c.page_no as page_start,
                c.page_no as page_end,
                c.text,
                ts_rank_cd(c.ts, websearch_to_tsquery('english', ?)) as keyword_score
            FROM pdf_chunks c
            JOIN pdf_documents d ON c.doc_id = d.id
            WHERE c.ts @@ websearch_to_tsquery('english', ?)
            ORDER BY keyword_score DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(
            query,
            (rs, rowNum) -> new EvidenceChunk(
                rs.getLong("chunk_id"),
                rs.getLong("doc_id"),
                rs.getString("filename"),
                rs.getInt("page_no"),
                rs.getInt("page_start"),
                rs.getInt("page_end"),
                rs.getString("text"),
                rs.getDouble("keyword_score"),
                "",
                null,                           // vectorScore
                rs.getDouble("keyword_score"),  // keywordScore
                null                            // finalScore (computed later)
            ),
            queryText,
            queryText,
            topK
        );
    }

    /**
     * Find top-K most similar chunks for a given embedding using pgvector cosine distance.
     * Uses the <-> operator for cosine distance (default IVFFlat metric).
     * 
     * @param embeddingVector String representation of the vector (e.g., "[0.1,0.2,...]")
     * @param topK Number of top results to return
     * @return List of most similar evidence chunks
     */
    public List<EvidenceChunk> findTopKSimilarChunks(String embeddingVector, int topK) {
        String query = """
            SELECT 
                c.id as chunk_id,
                c.doc_id,
                d.file_name as filename,
                c.page_no,
                c.page_no as page_start,
                c.page_no as page_end,
                c.text,
                (1 - (c.embedding <-> ?::vector)) as similarity
            FROM pdf_chunks c
            JOIN pdf_documents d ON c.doc_id = d.id
            WHERE c.embedding IS NOT NULL
            ORDER BY c.embedding <-> ?::vector
            LIMIT ?
            """;

        return jdbcTemplate.query(
            query,
            (rs, rowNum) -> new EvidenceChunk(
                rs.getLong("chunk_id"),
                rs.getLong("doc_id"),
                rs.getString("filename"),
                rs.getInt("page_no"),
                rs.getInt("page_start"),
                rs.getInt("page_end"),
                rs.getString("text"),
                rs.getDouble("similarity"),
                "",
                null,
                null,
                null
            ),
            embeddingVector,
            embeddingVector,
            topK
        );
    }

    /**
     * Find chunks by document ID and page number range.
     */
    public List<EvidenceChunk> findChunksByDocAndPages(Long docId, Integer pageStart, Integer pageEnd) {
        String query = """
            SELECT 
                c.id as chunk_id,
                c.doc_id,
                d.file_name as filename,
                c.page_no,
                c.page_no as page_start,
                c.page_no as page_end,
                c.text,
                0.0 as similarity
            FROM pdf_chunks c
            JOIN pdf_documents d ON c.doc_id = d.id
            WHERE c.doc_id = ? AND c.page_no >= ? AND c.page_no <= ?
            ORDER BY c.page_no, c.chunk_index
            """;

        return jdbcTemplate.query(
            query,
            (rs, rowNum) -> new EvidenceChunk(
                rs.getLong("chunk_id"),
                rs.getLong("doc_id"),
                rs.getString("filename"),
                rs.getInt("page_no"),
                rs.getInt("page_start"),
                rs.getInt("page_end"),
                rs.getString("text"),
                rs.getDouble("similarity"),
                "",
                null,
                null,
                null
            ),
            docId,
            pageStart,
            pageEnd
        );
    }

    /**
     * Find a specific chunk by chunk ID.
     * Used for neighbor chunk expansion in timeline queries.
     * 
     * @param chunkId The ID of the chunk to find
     * @return List with single chunk if found, empty list otherwise
     */
    public List<EvidenceChunk> findByChunkId(Long chunkId) {
        String query = """
            SELECT 
                c.id as chunk_id,
                c.doc_id,
                d.file_name as filename,
                c.page_no,
                c.page_no as page_start,
                c.page_no as page_end,
                c.text,
                0.0 as similarity
            FROM pdf_chunks c
            JOIN pdf_documents d ON c.doc_id = d.id
            WHERE c.id = ?
            """;

        return jdbcTemplate.query(
            query,
            (rs, rowNum) -> new EvidenceChunk(
                rs.getLong("chunk_id"),
                rs.getLong("doc_id"),
                rs.getString("filename"),
                rs.getInt("page_no"),
                rs.getInt("page_start"),
                rs.getInt("page_end"),
                rs.getString("text"),
                rs.getDouble("similarity"),
                "",
                null,
                null,
                null
            ),
            chunkId
        );
    }

    /**
     * Update embedding for a specific chunk.
     */
    public void updateChunkEmbedding(Long chunkId, String embeddingVector) {
        String query = "UPDATE pdf_chunks SET embedding = ?::vector WHERE id = ?";
        jdbcTemplate.update(query, embeddingVector, chunkId);
    }

    /**
     * Insert a new chunk (for indexing).
     */
    public Long insertChunk(Long docId, Integer pageNo, Integer chunkIndex, String text, String embeddingVector) {
        String query = """
            INSERT INTO pdf_chunks (doc_id, page_no, chunk_index, text, embedding, meta, created_at)
            VALUES (?, ?, ?, ?, ?::vector, '{}'::jsonb, CURRENT_TIMESTAMP)
            RETURNING id
            """;

        return jdbcTemplate.queryForObject(
            query,
            Long.class,
            docId,
            pageNo,
            chunkIndex,
            text,
            embeddingVector
        );
    }
}
