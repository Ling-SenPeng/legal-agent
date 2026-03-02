-- ============================================================================
-- Legal Agent Database Schema - PostgreSQL 15+ with pgvector
-- ============================================================================
-- This SQL script initializes the database for the evidence-grounded agent MVP.
-- Includes pgvector extension for semantic similarity search.
-- ============================================================================

-- Enable pgvector extension (requires: CREATE EXTENSION IF NOT EXISTS vector)
CREATE EXTENSION IF NOT EXISTS vector;

-- Drop tables if they exist (in reverse order due to foreign keys)
DROP TABLE IF EXISTS pdf_chunks;
DROP TABLE IF EXISTS pdf_documents;

-- ============================================================================
-- PDF Documents Table
-- ============================================================================
-- Stores metadata about uploaded PDF documents.
-- status: NEW | PROCESSING | DONE | FAILED
-- sha256: File content hash for deduplication
CREATE TABLE IF NOT EXISTS pdf_documents (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    sha256 CHAR(64) UNIQUE,                 -- File content hash for deduplication
    file_size BIGINT NOT NULL,
    
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',  -- NEW | PROCESSING | DONE | FAILED
    error_msg TEXT,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- Index for status-based queries (useful for polling processing pipeline)
CREATE INDEX IF NOT EXISTS idx_pdf_documents_status ON pdf_documents(status);

-- ============================================================================
-- PDF Chunks Table
-- ============================================================================
-- Stores text chunks extracted from PDF documents with embeddings.
-- Each chunk represents a portion of text (typically one page or section).
-- Embeddings are 1536-dimensional vectors from OpenAI's text-embedding-3-large model.
CREATE TABLE IF NOT EXISTS pdf_chunks (
    id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
    
    page_no INT NOT NULL,                      -- Page number for citations
    chunk_index INT NOT NULL DEFAULT 0,         -- Multiple chunks per page (MVP: all 0)
    text TEXT NOT NULL,                         -- Actual chunk text content
    
    embedding vector(1536),                    -- OpenAI 1536-dimensional vector
    ts tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(text,''))) STORED,  -- Full-text search
    meta JSONB NOT NULL DEFAULT '{}'::jsonb,   -- Reserved: char_count, extractor, language
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (doc_id, page_no, chunk_index)
);

-- Composite index for document + page queries
CREATE INDEX IF NOT EXISTS idx_pdf_chunks_doc_page ON pdf_chunks(doc_id, page_no);

-- Full-text search index for keyword matching (hybrid search)
CREATE INDEX IF NOT EXISTS idx_pdf_chunks_ts ON pdf_chunks USING GIN (ts);

-- pgvector IVFFlat index for semantic similarity search
-- Uses cosine distance metric (standard for embeddings)
-- lists = 100: number of clusters (adjust based on dataset size)
CREATE INDEX IF NOT EXISTS idx_pdf_chunks_embedding
ON pdf_chunks USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- ============================================================================
-- Sample Data for Testing
-- ============================================================================
-- Insert sample document and chunks for testing
-- (Comment out or remove in production)

-- INSERT INTO pdf_documents (file_name, file_path, file_size, status, processed_at)
-- VALUES ('contract.pdf', '/docs/contract.pdf', 50000, 'DONE', CURRENT_TIMESTAMP)
-- RETURNING id;
-- 
-- INSERT INTO pdf_chunks (doc_id, page_no, chunk_index, text, embedding)
-- VALUES (
--     1,
--     1,
--     0,
--     'John Smith agreed to pay $10,000 on January 15, 2023, as per the contract terms.',
--     '[0.1, 0.2, 0.3, ...]'::vector
-- );

-- ============================================================================
-- Verification Queries
-- ============================================================================
-- Run these to verify schema setup:

-- Check pgvector is installed:
-- SELECT * FROM pg_extension;

-- Check tables:
-- SELECT tablename FROM pg_tables WHERE schemaname='public';

-- Check indexes:
-- SELECT indexname FROM pg_indexes WHERE tablename='pdf_chunks';

-- Count documents:
-- SELECT COUNT(*) FROM pdf_documents;

-- Count chunks:
-- SELECT COUNT(*) FROM pdf_chunks;

-- Verify vector dimension:
-- SELECT id, array_length(embedding, 1) as dimension FROM pdf_chunks LIMIT 1;
