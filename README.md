# Legal Evidence-Grounded Agent MVP

A production-quality evidence-grounded agent for legal documents built with Spring Boot 3.x. This MVP implements a complete retrieval-augmented generation (RAG) pipeline with strict citation enforcement for legal document analysis.

## Overview

The agent orchestrates a 4-step pipeline to answer questions about legal documents with citations:

1. **Retrieval**: Find top-K most similar document chunks using pgvector semantic search
2. **Drafting**: Generate an answer using OpenAI with strict citation requirements
3. **Verification**: Check that all factual claims have proper citations
4. **Repair** (optional): Fix missing citations via LLM or downgrade to "Needs evidence"

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.1.0
- **Build**: Maven 3.6+
- **Database**: PostgreSQL 15+ with pgvector extension
- **HTTP Client**: Spring WebClient
- **JSON**: Jackson
- **Testing**: JUnit 5 + Testcontainers
- **LLM**: OpenAI API (GPT-4 or similar)

## Project Structure

```
legal-agent/
├── src/
│   ├── main/
│   │   ├── java/com/agent/
│   │   │   ├── AgentApplication.java           (Main app)
│   │   │   ├── AgentController.java            (REST endpoints)
│   │   │   ├── config/
│   │   │   │   ├── OpenAiProperties.java
│   │   │   │   ├── AgentProperties.java
│   │   │   │   └── WebClientConfig.java
│   │   │   ├── model/
│   │   │   │   ├── AgentQueryRequest.java
│   │   │   │   ├── AgentQueryResponse.java
│   │   │   │   ├── EvidenceChunk.java
│   │   │   │   ├── VerificationReport.java
│   │   │   │   ├── entity/
│   │   │   │   │   ├── PdfDocument.java
│   │   │   │   │   └── PdfChunk.java
│   │   │   │   └── openai/
│   │   │   │       ├── EmbeddingRequest.java
│   │   │   │       └── ChatCompletionRequest.java
│   │   │   ├── repository/
│   │   │   │   └── ChunkRepository.java         (pgvector queries)
│   │   │   └── service/
│   │   │       ├── AgentService.java           (Main orchestration)
│   │   │       ├── OpenAiClient.java           (OpenAI API integration)
│   │   │       ├── RetrievalService.java       (Vector search)
│   │   │       ├── DraftingService.java        (LLM call)
│   │   │       └── VerificationService.java    (Citation enforcement)
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-test.yml
│   └── test/
│       └── java/com/agent/
│           └── AgentIntegrationTest.java
├── pom.xml
└── README.md
```

## Prerequisites

1. **PostgreSQL 15+** with pgvector extension
2. **Java 17+**
3. **Maven 3.6+**
4. **OpenAI API Key** (sign up at https://platform.openai.com)

## Setup & Configuration

### 1. Database Setup

```bash
# Connect to PostgreSQL
psql -U postgres -d postgres

# Create database
CREATE DATABASE legal_agent;

# Switch to legal_agent database
\c legal_agent

# Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;

# Create schema (SQL shown below)
```

### 2. Environment Variables

Create a `.env` file or export the following:

```bash
export JDBC_URL="jdbc:postgresql://localhost:5432/legal_agent"
export JDBC_USER="postgres"
export JDBC_PASSWORD="postgres"

export OPENAI_API_KEY="sk-..."
export OPENAI_MODEL="gpt-4-mini"
export OPENAI_EMBEDDING_MODEL="text-embedding-3-large"
```

### 3. Build & Run

```bash
# Build
mvn clean package

# Run
mvn spring-boot:run

# Or run the JAR
java -jar target/legal-agent-1.0.0.jar
```

The application will start on `http://localhost:8080`

## Database Schema

```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- PDF documents table
CREATE TABLE pdf_documents (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    sha256 CHAR(64) UNIQUE,
    file_size BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    error_msg TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

CREATE INDEX idx_pdf_documents_status ON pdf_documents(status);

-- PDF chunks table with embeddings
CREATE TABLE pdf_chunks (
    id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
    page_no INT NOT NULL,
    chunk_index INT NOT NULL DEFAULT 0,
    text TEXT NOT NULL,
    embedding vector(1536),  -- OpenAI 1536-dim embeddings
    meta JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (doc_id, page_no, chunk_index)
);

CREATE INDEX idx_pdf_chunks_doc_page ON pdf_chunks(doc_id, page_no);

-- pgvector IVFFlat index for cosine similarity
CREATE INDEX idx_pdf_chunks_embedding
ON pdf_chunks USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

## API Endpoints

### Query Agent

**POST** `/agent/query`

Request:
```json
{
    "question": "What was the payment amount?",
    "topK": 8,
    "filters": null
}
```

Response:
```json
{
    "answer": "According to the agreement, John Smith agreed to pay $10,000 on January 15, 2023. [CIT doc=1 chunk=5 p=1-1]. Payment was split 50% upfront and 50% upon completion. [CIT doc=1 chunk=6 p=2-2].",
    "evidence": [
        {
            "chunkId": 5,
            "docId": 1,
            "pageNo": 1,
            "pageStart": 1,
            "pageEnd": 1,
            "text": "John Smith agreed to pay $10,000 on January 15, 2023, as per the contract terms.",
            "similarity": 0.95,
            "citations": "[CIT doc=1 chunk=5 p=1-1]"
        }
    ],
    "verification": {
        "passed": true,
        "missingCitationLines": [],
        "notes": "All factual claims have proper citations."
    },
    "processingTimeMs": 1234
}
```

### Health Check

**GET** `/agent/health`

Response: `"Agent is running"`

## End-to-End Usage Examples

### Example 1: Simple Contract Question

Query the agent to find specific payment terms:

```bash
curl -X POST http://localhost:8080/agent/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "How much did John Smith agree to pay?",
    "topK": 5,
    "filters": null
  }'
```

Expected response:
```json
{
  "answer": "John Smith agreed to pay $10,000 on January 15, 2023. [CIT doc=1 chunk=0 p=1-1]",
  "evidence": [
    {
      "chunkId": 0,
      "docId": 1,
      "pageNo": 1,
      "text": "John Smith agreed to pay $10,000 on January 15, 2023, as per the contract terms.",
      "similarity": 0.96,
      "citations": "[CIT doc=1 chunk=0 p=1-1]"
    }
  ],
  "verification": {
    "passed": true,
    "missingCitationLines": [],
    "notes": "All factual claims have proper citations."
  },
  "processingTimeMs": 245
}
```

### Example 2: Multi-Part Question with Evidence

Ask a complex question requiring multiple evidence pieces:

```bash
curl -X POST http://localhost:8080/agent/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are the complete payment terms and when does completion occur?",
    "topK": 8,
    "filters": null
  }'
```

Expected response:
```json
{
  "answer": "The contract specifies that payment is split into two parts. [CIT doc=1 chunk=1 p=2-2] Payment terms are: 50% upfront and 50% upon completion. [CIT doc=1 chunk=1 p=2-2] Both parties signed the agreement on February 1, 2023. [CIT doc=1 chunk=2 p=3-3]",
  "evidence": [
    {
      "chunkId": 1,
      "docId": 1,
      "pageNo": 2,
      "text": "Payment terms: 50% upfront, 50% upon completion. The buyer received the full documentation.",
      "similarity": 0.94,
      "citations": "[CIT doc=1 chunk=1 p=2-2]"
    },
    {
      "chunkId": 2,
      "docId": 1,
      "pageNo": 3,
      "text": "Both parties signed the agreement on February 1, 2023. Witness: Jane Doe.",
      "similarity": 0.91,
      "citations": "[CIT doc=1 chunk=2 p=3-3]"
    }
  ],
  "verification": {
    "passed": true,
    "missingCitationLines": [],
    "notes": "All factual claims properly cited with document references."
  },
  "processingTimeMs": 387
}
```

### Example 3: Named Entity Recognition

Query for specific people or dates mentioned in documents:

```bash
curl -X POST http://localhost:8080/agent/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Who are the witnesses and signers of this agreement?",
    "topK": 5,
    "filters": null
  }'
```

Expected response includes properly cited names and roles.

### Example 4: Verification with Auto-Repair

Query that tests the citation verification and optional auto-repair:

```bash
curl -X POST http://localhost:8080/agent/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "List all monetary amounts mentioned in the document.",
    "topK": 8,
    "filters": null
  }'
```

The response will include all citations in the format `[CIT doc=1 chunk=5 p=10-11]` for each monetary amount mentioned.

### Example 5: Health Check (Pre-Query Verification)

Always check health before queries:

```bash
curl http://localhost:8080/agent/health
# Response: "Agent is running"
```

## Citation Format

All factual claims in the answer must end with a citation token:

```
[CIT doc=<doc_id> chunk=<chunk_id> p=<page_start>-<page_end>]
```

Example:
```
According to the contract, the payment was $50,000. [CIT doc=1 chunk=5 p=10-11]
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
openai:
  api-key: ${OPENAI_API_KEY}
  model: ${OPENAI_MODEL:gpt-4-mini}
  embedding-model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-large}
  timeout-ms: 30000

agent:
  default-top-k: 8
  verification:
    enabled: true
    repair-enabled: true
    factual-keywords: paid,lived,moved,asked,agreed,owed,purchased,sold,received,gave
```

## Testing

Run integration tests with Testcontainers:

```bash
mvn test
```

Tests use an embedded PostgreSQL container with pgvector. No external database required.

## Agent Flow Diagram

```
User Query
    ↓
[Retrieval Service]
  - Generate embedding for question (OpenAI)
  - pgvector similarity search (top-K chunks)
    ↓
[Drafting Service]
  - Call OpenAI with evidence + strict prompt
  - LLM generates answer with citations
    ↓
[Verification Service]
  - Parse answer into lines
  - Detect factual claims (keywords, numbers, dates)
  - Check for [CIT ...] tokens
    ↓
[Repair Service] (if verification failed)
  - LLM call to fix missing citations
  - Or downgrade to "Needs evidence"
    ↓
Response (answer + evidence + verification)
```

## Troubleshooting

### "No plugin found for prefix 'spring-boot'"

```bash
mvn clean install -U
```

### OpenAI API timeout

Increase `openai.timeout-ms` in configuration

### pgvector extension not found

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### Database connection issues

Verify credentials:
```bash
psql -h localhost -U postgres -d legal_agent
```

## Performance Considerations

- **Vector Index**: Uses IVFFlat for ~100 lists. Adjust based on dataset size.
- **Top-K**: Default 8; adjust via `defaultTopK` in configuration.
- **Embeddings**: OpenAI `text-embedding-3-large` (1536 dims) is recommended.
- **LLM Model**: Use `gpt-4-mini` or faster models for MVP; upgrade to `gpt-4-turbo` for production.

## Next Steps (Production Hardening)

1. Implement batch embedding ingestion for large document sets
2. Add hybrid search (semantic + keyword matching with BM25)
3. Implement RAG-specific metrics (answer quality, citation coverage)
4. Add support for multi-case queries
5. Integrate with vector store (e.g., LangChain, LlamaIndex)
6. Add document chunking strategies (semantic, sliding window)
7. Implement caching for frequently asked questions
8. Add observability (tracing, metrics, structured logging)
9. Set up Redis cache for embeddings
10. Implement async processing for large queries

## License

This project is open source and available under the MIT License.

## Support

For issues or questions, see the [GitHub Issues](https://github.com/Ling-SenPeng/legal-agent/issues) page.
