-- ============================================================================
-- Legal Agent Database Schema - PostgreSQL 15+ with pgvector
-- ============================================================================
-- This SQL script initializes the database for the evidence-grounded agent MVP.
-- Includes pgvector extension for semantic similarity search.
-- ============================================================================

-- Enable pgvector extension (requires: CREATE EXTENSION IF NOT EXISTS vector)
CREATE EXTENSION IF NOT EXISTS vector;

-- Drop tables if they exist (in reverse order due to foreign keys)
DROP TABLE IF EXISTS payment_records;
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
-- Payment Records Table
-- ============================================================================
-- Stores structured payment records extracted from mortgage/loan documents.
-- This is the primary evidence source for payment-related legal queries.
-- Replaces text-based extraction from PDF chunks.
CREATE TABLE IF NOT EXISTS payment_records (
    id BIGSERIAL PRIMARY KEY,
    pdf_document_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
    
    -- Statement information
    statement_index INT,                       -- Which statement in document (0, 1, 2, ...)
    statement_period_start DATE,               -- Period covered by statement
    statement_period_end DATE,
    
    -- Key payment date
    payment_date DATE NOT NULL,                -- When payment was due/made
    
    -- Payment classification
    category VARCHAR(50) NOT NULL,             -- mortgage | escrow | tax | insurance | principal | interest
    
    -- Amount breakdown (all in BigDecimal)
    total_amount NUMERIC(16, 2),               -- Total payment amount
    principal_amount NUMERIC(16, 2),           -- Principal portion
    interest_amount NUMERIC(16, 2),            -- Interest portion
    escrow_amount NUMERIC(16, 2),              -- Escrow/impound portion
    tax_amount NUMERIC(16, 2),                 -- Property tax portion
    insurance_amount NUMERIC(16, 2),           -- Insurance portion
    
    -- Party information
    payer_name VARCHAR(255),                   -- Who made the payment
    payee_name VARCHAR(255),                   -- Who received the payment
    loan_number VARCHAR(100),                  -- Account/loan number
    
    -- Property information
    property_address VARCHAR(500),             -- Full address
    property_city VARCHAR(100),                -- City name
    property_state VARCHAR(50),                -- State abbreviation
    property_zip VARCHAR(20),                  -- ZIP code
    
    -- Record details
    description TEXT,                          -- Human-readable description
    source_page INT,                           -- Page number in PDF where extracted
    source_snippet TEXT,                       -- Original text from document
    confidence NUMERIC(4, 3),                  -- Extraction confidence (0.0-1.0)
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ensure we don't have duplicate records
    UNIQUE (pdf_document_id, statement_index, payment_date, category, total_amount)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_payment_records_pdf_doc ON payment_records(pdf_document_id);
CREATE INDEX IF NOT EXISTS idx_payment_records_property_address ON payment_records(property_address);
CREATE INDEX IF NOT EXISTS idx_payment_records_property_city ON payment_records(property_city);
CREATE INDEX IF NOT EXISTS idx_payment_records_category ON payment_records(category);
CREATE INDEX IF NOT EXISTS idx_payment_records_payment_date ON payment_records(payment_date);
CREATE INDEX IF NOT EXISTS idx_payment_records_category_date ON payment_records(category, payment_date);

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
