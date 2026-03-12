# Legal Evidence-Grounded Agent MVP

A production-quality evidence-grounded agent for legal documents built with Spring Boot 3.x. This MVP implements a **multi-mode architecture** with specialized handlers for different analysis tasks, plus a complete retrieval-augmented generation (RAG) pipeline.

## Overview

### Multi-Mode Architecture (V1)

The agent uses a **Task Mode Router** pattern to dispatch queries to specialized handlers:

- **CASE_ANALYSIS Mode** (V1 ✅): Complete legal analysis pipeline
  - Evidence retrieval → Context building → Strength assessment → Answer formatting
  - 6-section structured reports (Issue Summary, Legal Standards, Application, Counterarguments, Missing Evidence, Tentative Conclusion)
  - Confidence & strength metrics
  
- **DRAFTING Mode** (Planned): Focus on answer composition
- **VERIFICATION Mode** (Planned): Citation verification
- **QUICK_ANSWER Mode** (Default): Fast fact retrieval

### Classical RAG Pipeline (Base Layer)

Standard 4-step RAG for general queries:

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
│   │   │   │   ├── ModeExecutionResult.java    (Handler return type)
│   │   │   │   ├── TaskMode.java               (Mode enumeration)
│   │   │   │   ├── VerificationReport.java
│   │   │   │   ├── analysis/                   (Case analysis models)
│   │   │   │   │   ├── CaseAnalysisContext.java
│   │   │   │   │   ├── CaseAnalysisResult.java
│   │   │   │   │   ├── CaseIssue.java
│   │   │   │   │   ├── CaseFact.java
│   │   │   │   │   └── LegalIssueType.java
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
│   │   │       ├── VerificationService.java    (Citation enforcement)
│   │   │       ├── orchestration/
│   │   │       │   ├── TaskModeOrchestrator.java     (Router)
│   │   │       │   ├── TaskModeHandler.java          (Handler interface)
│   │   │       │   └── handler/
│   │   │       │       └── CaseAnalysisModeHandler.java (V1 impl)
│   │   │       └── analysis/
│   │           ├── CaseAnalysisContextBuilder.java  (Interface)
│   │           ├── RuleBasedCaseAnalysisContextBuilder.java
│   │           ├── CaseIssueExtractor.java         (Interface)
│   │           ├── RuleBasedCaseIssueExtractor.java
│   │           ├── CaseFactExtractor.java          (Interface)
│   │           ├── RuleBasedCaseFactExtractor.java
│   │           ├── CaseAnalysisQueryCleaner.java   (Noise removal)
│   │           ├── IssueRetrievalStrategy.java     (Interface)
│   │           └── CaseAnalysisRetrievalQueryBuilder.java (Query builder)
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-test.yml
│   └── test/
│       └── java/com/agent/
│           ├── AgentIntegrationTest.java
│           └── service/
│               ├── handler/
│               │   └── CaseAnalysisModeHandlerTest.java (7 tests)
│               └── analysis/
│                   ├── CaseAnalysisFactExtractionTest.java
│                   ├── RuleBasedCaseAnalysisContextBuilderTest.java
│                   ├── RuleBasedCaseIssueExtracterTest.java                   ├── CaseAnalysisQueryCleanerTest.java
                   ├── CaseAnalysisRetrievalQueryBuilderTest.java
                   ├── CaseAnalysisQueryPreprocessingIntegrationTest.java│                   └── ...
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
export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"
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

### Task Mode Query (Multi-Mode Architecture)

**POST** `/agent/query`

**Auto-Mode Detection** (TaskModeOrchestrator):
The agent automatically detects query intent and routes to the appropriate handler.

**Example 1: CASE_ANALYSIS Mode** (Legal analysis query)
```json
{
    "question": "What is the strength of my reimbursement claim? I paid $40,000 in post-separation mortgage payments.",
    "topK": 10
}
```

Response:
```json
{
    "answer": "=== CASE ANALYSIS REPORT ===\n\nISSUE SUMMARY\nYou have raised a reimbursement claim for post-separation mortgage payments totaling $40,000...\n\nAPPLICABLE LEGAL STANDARDS\nRequest for reimbursement evaluated under Epstein factors...\n\nAPPLICATION SUMMARY\nBased on evidence provided...\n\nCOUNTERARGUMENTS\nOpposing party may argue...\n\nMISSING EVIDENCE\nThe following critical facts are not found in current evidence:\n- Documentation of property title status\n- Timeline of occupancy by other spouse\n\nTENTATIVE CONCLUSION\nPreliminary analysis suggests STRONG legal position (78% confidence)...\n\nPRELIMINARY ANALYSIS ONLY - Not legal advice.",
    "taskMode": "CASE_ANALYSIS",
    "metadata": "Mode: CASE_ANALYSIS | Issues: 1 | Facts: 5 | Strength: STRONG | Confidence: 78.50%",
    "evidence": [
        {
            "chunkId": 42,
            "docId": 3,
            "similarity": 0.91,
            "text": "Paid $40,000 in post-separation mortgage...",
            "citations": "[CIT doc=3 chunk=42 p=5-6]"
        }
    ],
    "processingTimeMs": 892
}
```

**Example 2: DEFAULT Mode** (Quick fact retrieval)
```json
{
    "question": "What was the payment amount?",
    "topK": 8
}
```

Response:
```json
{
    "answer": "According to the agreement, John Smith agreed to pay $10,000 on January 15, 2023. [CIT doc=1 chunk=5 p=1-1].",
    "taskMode": "QUICK_ANSWER",
    "evidence": [
        {
            "chunkId": 5,
            "docId": 1,
            "similarity": 0.95,
            "text": "John Smith agreed to pay $10,000..."
        }
    ],
    "verification": {
        "passed": true,
        "missingCitationLines": []
    }
}
```

### Mode Keywords (Query Routing)

| Mode | Keywords |
|------|----------|
| CASE_ANALYSIS | "analyze", "strength", "claim", "legal position", "position on" |
| DRAFTING | "draft", "write", "compose", "response to" |
| VERIFICATION | "verify", "check", "validate", "citations" |
| QUICK_ANSWER | (default) |

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
  embedding-model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
  timeout-ms: 30000

agent:
  default-top-k: 8
  verification:
    enabled: true
    repair-enabled: true
    factual-keywords: paid,lived,moved,asked,agreed,owed,purchased,sold,received,gave
```

## Testing

### Test Suite

**Total: 107 tests** (all passing)

Run all tests:
```bash
mvn test
```

**Test Coverage by Component**:

| Component | Tests | File |
|-----------|-------|------|
| CaseAnalysisModeHandler (V1) | 7 | `CaseAnalysisModeHandlerTest.java` |
| RuleBasedCaseAnalysisContextBuilder | 14 | `RuleBasedCaseAnalysisContextBuilderTest.java` |
| RuleBasedCaseFactExtractor | 27 | `RuleBasedCaseFactExtractionTest.java` |
| RuleBasedCaseIssueExtractor | 25 | `RuleBasedCaseIssueExtracterTest.java` |
| CaseAnalysisQueryCleaner | 10 | `CaseAnalysisQueryCleanerTest.java` |
| CaseAnalysisRetrievalQueryBuilder | 12 | `CaseAnalysisRetrievalQueryBuilderTest.java` |
| Query Preprocessing Pipeline | 8 | `CaseAnalysisQueryPreprocessingIntegrationTest.java` |
| RuleBasedTaskRouter | 26 | `RuleBasedTaskRouterTest.java` |
| RetrievalService | 7 | `RetrievalServiceTest.java` |
| **Total** | **137** | |

**CaseAnalysisModeHandler Tests** (7 new tests):
- ✅ Handler returns correct TaskMode
- ✅ Answer includes all 6 required sections
- ✅ Metadata format verified
- ✅ Handles missing evidence gracefully
- ✅ Exception handling with error messages
- ✅ Strength assessment accuracy
- ✅ Comprehensive analysis with recommendations

### Running Tests with Testcontainers

Tests use an embedded PostgreSQL container with pgvector. No external database required:

```bash
mvn test
```

Container setup is automatic - tests start PostgreSQL, create schema, run tests, and clean up.

## Agent Flow Diagram

```
User Query
    ↓
[TaskModeOrchestrator]
  Detect query intent & route to handler
    ↓
[Mode Handler Dispatch]
├─ CASE_ANALYSIS → CaseAnalysisModeHandler
│  ├─ [Query Preprocessing Pipeline]  ← NEW
│  │  ├─ Strip analysis noise (CaseAnalysisQueryCleaner)
│  │  ├─ Extract issues (CaseIssueExtractor)
│  │  └─ Build issue-driven retrieval queries (IssueRetrievalStrategy)
│  ├─ [Issue-Driven Retrieval]
│  │  ├─ Execute multiple optimized queries
│  │  └─ Merge & deduplicate results
│  ├─ [Context Builder] - Extract issues & facts
│  ├─ [Strength Assessment] - Evaluate 5-level scale
│  └─ [Answer Formatter] - 6-section report
│
├─ DRAFTING → DraftingModeHandler (TBD)
├─ VERIFICATION → VerificationModeHandler (TBD)
└─ QUICK_ANSWER → [Original RAG Pipeline]
    ├─ [Retrieval] - pgvector search
    ├─ [Drafting] - LLM answer generation
    ├─ [Verification] - Citation check
    └─ [Repair] (if needed) - Fix citations
    ↓
Response (answer + metadata + evidence)
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

## Completed Features

### Query Preprocessing Pipeline (V1 ✅)

Implements intelligent query preprocessing to improve CASE_ANALYSIS retrieval quality:

**Components**:
- **CaseAnalysisQueryCleaner**: Strips 30+ analysis framing phrases ("based on these facts", "do I have", etc.)
- **CaseAnalysisRetrievalQueryBuilder** (implements IssueRetrievalStrategy): Generates 1-5 optimized retrieval queries per detected issue
- **Issue-to-Keyword Mapping**: 8 legal issue types with relevant keywords for each
- **Merge & Deduplication**: Combines results from multiple queries, keeping highest similarity scores

**Benefits**:
- Removes analysis noise that interferes with vector search relevance
- Generates multiple query variants for better recall
- Deterministic, rule-based implementation (no external ML dependencies)
- Comprehensive logging for debugging

**Testing**:
- 30 tests total (10 + 12 + 8 for cleaning, building, and integration)
- Real-world query rewrite examples validated
- All 137 tests passing

**Documentation**: See [CASE_ANALYSIS_QUERY_PREPROCESSING.md](CASE_ANALYSIS_QUERY_PREPROCESSING.md) for complete architecture guide.

## Next Steps (Production Hardening)

1. ✅ COMPLETED: Issue-driven retrieval with query preprocessing
2. Implement batch embedding ingestion for large document sets
3. Add hybrid search (semantic + keyword matching with BM25)
4. Implement RAG-specific metrics (answer quality, citation coverage)
5. Add support for multi-case queries
6. Integrate with vector store (e.g., LangChain, LlamaIndex)
7. Add document chunking strategies (semantic, sliding window)
8. Implement caching for frequently asked questions
9. Add observability (tracing, metrics, structured logging)
10. Set up Redis cache for embeddings
11. Implement async processing for large queries

## License

This project is open source and available under the MIT License.

## Support

For issues or questions, see the [GitHub Issues](https://github.com/Ling-SenPeng/legal-agent/issues) page.
