# Development Guide - Legal Evidence-Grounded Agent

## Architecture Overview

The agent uses a **Task Mode Router Pattern** to orchestrate specialized analysis modes:

```
┌─────────────┐
│   Query     │
└──────┬──────┘
       ↓
┌──────────────────────────────────────────┐
│ TaskModeOrchestrator                     │
│ ─────────────────────────────────────── │
│ • Detect query intent/mode               │
│ • Route to mode-specific handler         │
│ • Return ModeExecutionResult             │
└──────┬───────────────────────────────────┘
       ↓
   [Mode Type]
    ├─ CASE_ANALYSIS ──→ CaseAnalysisModeHandler (V1)
    ├─ DRAFTING     ──→ DraftingModeHandler
    ├─ VERIFICATION ──→ VerificationModeHandler
    └─ [Future]    ──→ CustomHandlers
```

### CASE_ANALYSIS Mode (V1)

The CASE_ANALYSIS mode implements a complete legal analysis pipeline with 6 steps:

```
┌──────────────────────────────────────────────────────────────┐
│ CASE_ANALYSIS V1 Pipeline                                   │
├──────────────────────────────────────────────────────────────┤
│ Step 1: Evidence Retrieval    → Top-K chunks via vector search
│ Step 2: Context Building      → Extract issues & facts
│ Step 3: Strength Assessment   → 5-level scale evaluation
│ Step 4: Result Generation     → Confidence scoring
│ Step 5: Answer Formatting     → 6-section structured report
│ Step 6: Metadata Assembly     → Issues, facts, strength metrics
└──────────────────────────────────────────────────────────────┘
```

**Answer Format** (6 Required Sections):
1. **Issue Summary** - Key legal issues identified
2. **Applicable Legal Standards** - Relevant case law/statutes
3. **Application Summary** - How facts map to legal standards
4. **Counterarguments** - Opposing viewpoints/weaknesses
5. **Missing Evidence** - Critical facts not found in documents
6. **Tentative Conclusion** - Preliminary analysis (with disclaimer)

**Strength Assessment Logic** (5 Levels):
- **VERY_STRONG**: ≥75% favorable facts, <20% missing
- **STRONG**: ≥65% favorable facts, <30% missing
- **MODERATE**: 40-65% favorable facts balance
- **WEAK**: <40% favorable facts OR >50% missing
- **VERY_WEAK**: <25% favorable facts

**Confidence Calculation**:
- `60% × avg(issue_confidence) + 40% × (favorable_facts / (favorable + missing))`

**Metadata Format**:
```
Mode: CASE_ANALYSIS | Issues: 2 | Facts: 8 | Strength: STRONG | Confidence: 78.50%
```

## Task Mode Router & Handler Architecture

### TaskModeOrchestrator

**File**: `service/orchestration/TaskModeOrchestrator.java`

Central routing component that detects query intent and dispatches to mode-specific handlers:

```java
public ModeExecutionResult executeQuery(String query, int topK) {
    // 1. Detect mode from query
    TaskMode mode = detectMode(query);
    
    // 2. Get appropriate handler
    TaskModeHandler handler = handlerMap.get(mode);
    
    // 3. Execute and return result
    return handler.execute(query, topK);
}
```

**Mode Detection Strategy**:
- Pattern matching on query keywords
- Confidence scoring (0.0-1.0)
- Fallback to default mode if no confident match

**Routing Table**:
| Mode | Keywords | Handler |
|------|----------|---------|
| CASE_ANALYSIS | "analyze", "strength", "claim", "legal position" | CaseAnalysisModeHandler |
| DRAFTING | "draft", "write", "compose", "response" | DraftingModeHandler |
| VERIFICATION | "verify", "check", "validate", "citations" | VerificationModeHandler |
| QUICK_ANSWER | (default) | QuickAnswerHandler |

### TaskModeHandler Interface

All mode handlers implement `TaskModeHandler`:

```java
public interface TaskModeHandler {
    TaskMode getMode();
    ModeExecutionResult execute(String query, int topK);
}
```

**Common Return Type** - `ModeExecutionResult`:
```java
public record ModeExecutionResult(
    TaskMode mode,
    String answer,
    String metadata  // Optional structured metadata
) {
    public boolean isSuccess() { /* ... */ }
    public String getErrorMessage() { /* ... */ }
}
```

### CaseAnalysisModeHandler (V1)

**File**: `service/handler/CaseAnalysisModeHandler.java`

Implements complete legal analysis pipeline orchestration:

```
execute(query, topK)
  ├─ Step 1: retrievalService.retrieveEvidence(query, topK)
  │         → List<EvidenceChunk>
  │
  ├─ Step 2: contextBuilder.buildContext(query, chunks)
  │         → CaseAnalysisContext (issues, facts, legal standards)
  │
  ├─ Step 3: generateAnalysisResult(context)
  │         → CaseAnalysisResult (strength, confidence)
  │
  ├─ Step 4: formatAnalysisAnswer(query, context, result)
  │         → String (6-section report)
  │
  ├─ Step 5: Build metadata
  │         → "Mode: CASE_ANALYSIS | Issues: X | Facts: Y | ..."
  │
  └─ Step 6: Return ModeExecutionResult(CASE_ANALYSIS, answer, metadata)
```

**Key Methods**:
- `execute()` - Main orchestration entry point
- `generateAnalysisResult()` - Strength assessment & result generation
- `assessStrength()` - 5-level strength evaluation
- `formatAnalysisAnswer()` - Structured answer formatting
- `counterclaim()` - Generate opposing arguments

**Integration Points**:
- `RetrievalService` - Semantic search
- `CaseAnalysisContextBuilder` - Issue & fact extraction
- Comprehensive logging at DEBUG/INFO levels

### Other Handler Classes

**DraftingModeHandler** (TBD): Focus on answer composition
**VerificationModeHandler** (TBD): Citation verification
**QuickAnswerHandler** (TBD): Fast fact retrieval

## Classical RAG Pipeline (Base Layer)

The agent also implements a classical RAG (Retrieval-Augmented Generation) pipeline with strict citation enforcement:

```
┌─────────────┐
│   Query     │
└──────┬──────┘
       ↓
┌──────────────────────────────────────────────────────────┐
│ 1. RETRIEVAL PHASE                                       │
│ ─────────────────────────────────────────────────────── │
│ • Embed user question using OpenAI embeddings API      │
│ • Search pgvector for top-K similar chunks            │
│ • Return chunks with similarity scores                │
└──────┬───────────────────────────────────────────────────┘
       ↓
┌──────────────────────────────────────────────────────────┐
│ 2. DRAFTING PHASE                                        │
│ ─────────────────────────────────────────────────────── │
│ • Package evidence chunks in strict prompt             │
│ • Call OpenAI chat API with citation requirements     │
│ • LLM generates answer with [CIT ...] tokens         │
└──────┬───────────────────────────────────────────────────┘
       ↓
┌──────────────────────────────────────────────────────────┐
│ 3. VERIFICATION PHASE                                    │
│ ─────────────────────────────────────────────────────── │
│ • Parse answer into lines/bullets                     │
│ • Detect factual claims (keywords, numbers, dates)   │
│ • Verify presence of citation tokens                  │
│ • Collect lines missing citations                     │
└──────┬───────────────────────────────────────────────────┘
       ↓
   [Passed?]
       ├─→ YES ──→ Return answer
       │
       └─→ NO ──→ REPAIR PHASE
              ┌──────────────────────────┐
              │ 4. REPAIR PHASE          │
              │ ─────────────────────── │
              │ • LLM fixes missing cit  │
              │ • Re-verify              │
              └──────┬───────────────────┘
                     ↓
            ┌────────────────────┐
            │ Return fixed answer│
            └────────────────────┘
```

## Key Components

### Handler Integration with AgentService

**File**: `service/AgentService.java`

Main orchestration layer coordinates handlers with RAG pipeline:

```java
@Service
public class AgentService {
    private TaskModeOrchestrator orchestrator;
    
    public AgentQueryResponse processQuery(AgentQueryRequest request) {
        // Route to mode handler
        ModeExecutionResult modeResult = orchestrator.executeQuery(
            request.getQuestion(), 
            request.getTopK()
        );
        
        // Convert to agent response
        return new AgentQueryResponse(
            modeResult.getAnswer(),
            modeResult.getMetadata(),
            modeResult.getMode(),
            /* RAG metadata if applicable */
        );
    }
}
```

**Execution Flow**:
1. `AgentController` receives HTTP request
2. Delegates to `AgentService.processQuery()`
3. `AgentService` calls `TaskModeOrchestrator.executeQuery()`
4. `TaskModeOrchestrator` routes to mode-specific `TaskModeHandler`
5. Handler executes specialized logic
6. Returns `ModeExecutionResult`
7. Service wraps result in `AgentQueryResponse`
8. Controller returns JSON to client

### 1. RetrievalService
**File**: `service/RetrievalService.java`

Handles semantic search using pgvector:
- Takes user question
- Generates embedding via OpenAI API
- Searches PostgreSQL with cosine similarity
- Returns top-K chunks with similarity scores

**SQL Query** (in `ChunkRepository`):
```sql
SELECT c.id, c.doc_id, c.text, (1 - (c.embedding <-> ?)) as similarity
FROM pdf_chunks c
WHERE c.embedding IS NOT NULL
ORDER BY c.embedding <-> ?
LIMIT ?
```

The `<->` operator performs cosine distance calculation in pgvector.

### 2. DraftingService
**File**: `service/DraftingService.java`

Generates initial answer with OpenAI:
- Builds system prompt enforcing strict citation rules
- Includes evidence chunks in user message
- Calls GPT-4-mini (or configured model)
- Returns drafted answer

**Key Prompt Elements**:
- "Every factual claim must end with a citation token"
- "If you cannot find evidence, either omit or mark as 'Needs evidence'"
- Structured format: Key findings → Supporting context → Gaps

### 3. VerificationService
**File**: `service/VerificationService.java`

Checks citation completeness:
- Parses answer into lines
- Identifies factual claims using keywords and patterns
- Verifies each claim has `[CIT doc=X chunk=Y p=A-B]` token
- Optionally repairs via LLM call

**Factual Claim Detection**:
- Keyword matching (paid, lived, moved, agreed, owed, etc.)
- Date patterns: `1/15/2023`, `01-15-2023`, `January 15, 2023`
- Dollar amounts: `$10,000`, `$50 million`
- Named entities (capitalized words)

**Citation Token Format**:
```
[CIT doc=<doc_id> chunk=<chunk_id> p=<page_start>-<page_end>]
```

### 4. AgentService
**File**: `service/AgentService.java`

Main orchestration layer:
- Calls services in sequence
- Handles errors gracefully
- Conditionally runs repair step
- Compiles final response

## Database Design

### pgvector Setup

1. **Extension**:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

2. **Vector Type**: `vector(1536)`
   - Matches OpenAI `text-embedding-3-large` dimension
   - Immutable, efficiently stored

3. **Index Type**: `ivfflat`
   - Faster approximate search than linear scan
   - `lists = 100`: number of partitions (tune based on data size)
   - Cosine distance: `vector_cosine_ops`

4. **Query Example**:
```sql
-- Find top-5 most similar chunks
SELECT id, text, 1 - (embedding <-> '[0.1,0.2,...]'::vector) as similarity
FROM pdf_chunks
ORDER BY embedding <-> '[0.1,0.2,...]'::vector
LIMIT 5;
```

### Why pgvector vs. Other Solutions?

| Feature | pgvector | Pinecone | Weaviate |
|---------|----------|----------|----------|
| Database | Built-in | External | External |
| Cost | Free | $$ | $$ |
| SQL Support | Yes | Limited API | Limited |
| Deployment | Self-hosted | Managed | Managed |
| Learning Curve | Low (PostgreSQL) | Medium | Medium |

For MVP and self-hosted deployment, pgvector is ideal.

## OpenAI Integration

### Models Used

1. **Embeddings**: `text-embedding-3-large` (1536 dimensions)
   - Used for semantic search
   - Standard choice for RAG

2. **Chat**: `gpt-4-mini` (default, or GPT-4-Turbo)
   - Used for answer generation and repair
   - Low-cost, good quality

### API Calls

**Embedding API**:
```bash
POST https://api.openai.com/v1/embeddings
{
  "input": "What was the payment amount?",
  "model": "text-embedding-3-large"
}
```

**Chat Completions API**:
```bash
POST https://api.openai.com/v1/chat/completions
{
  "model": "gpt-4-mini",
  "messages": [
    {"role": "system", "content": "You are a legal analysis assistant..."},
    {"role": "user", "content": "Question + evidence + instructions"}
  ],
  "temperature": 0.2
}
```

### Error Handling

- Timeout: 30 seconds (configurable)
- Retry: Not implemented in MVP (can add exponential backoff)
- Fallback: Return error message to client

## Testing Strategy

### Unit Tests (TODO)
- `OpenAiClientTest`: Mock OpenAI API
- `VerificationServiceTest`: Test citation detection
- `RetrievalServiceTest`: Mock repository

### Integration Tests
**File**: `AgentIntegrationTest.java`

Uses Testcontainers for full stack testing:
1. Spin up PostgreSQL 15 container
2. Enable pgvector extension
3. Create schema
4. Insert sample data
5. Execute queries
6. Tear down container

**Key Benefits**:
- No external dependencies
- Fast (container-based)
- Reproducible
- CI/CD friendly

**Limitations**:
- Cannot mock OpenAI API (would need WireMock)
- Full integration tests require real API key OR mock WebClient

## Configuration

### application.yml Hierarchy

1. **Defaults** (in code): `OpenAiProperties`, `AgentProperties`
2. **application.yml**: Development defaults
3. **Environment variables**: Override defaults
4. **System properties**: Override everything

**Example Override**:
```bash
export OPENAI_MODEL="gpt-4-turbo"
export AGENT_DEFAULT_TOP_K=15
```

Maps to:
```yaml
openai:
  model: gpt-4-turbo

agent:
  default-top-k: 15
```

## Performance Tuning

### Vector Search

**Factors** affecting speed:
- Index lists (default 100): More lists → slower build, faster search
  - Small dataset (<10K): lists=10-50
  - Medium (10K-100K): lists=50-200
  - Large (>100K): lists=200-500

**Optimize**:
```sql
CREATE INDEX CONCURRENTLY idx_pdf_chunks_embedding
ON pdf_chunks USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 200);
```

### OpenAI Calls

**Reduce costs**:
- Use cheaper model: `gpt-3.5-turbo` (instead of GPT-4)
- Batch requests: Not applicable for MVP
- Cache embeddings: Implement Redis layer

**Example**: GPT-3.5-turbo
```yaml
openai:
  model: gpt-3.5-turbo
  embedding-model: text-embedding-3-small  # 512 dims, cheaper
```

### Network Optimization

- Use connection pooling (HikariCP, included)
- Batch pgvector inserts for indexing
- Implement caching layer for repeated queries

## Debugging

### Enable Debug Logging

```yaml
logging:
  level:
    com.agent: DEBUG
    org.springframework.jdbc: DEBUG
    org.springframework.web.reactive.function.client: DEBUG
```

### Common Issues

**Issue**: "embedding IS NOT NULL but all null"
**Solution**: Verify embeddings inserted correctly:
```sql
SELECT COUNT(*) FROM pdf_chunks WHERE embedding IS NOT NULL;
```

**Issue**: "No citations found in answer"
**Solution**: Check regex pattern in `VerificationService.CITATION_PATTERN`
- Ensure exact format: `[CIT doc=X chunk=Y p=A-B]`

**Issue**: Cosine distance returns NULL
**Solution**: Ensure embedding dimensions match (both 1536)
```sql
SELECT array_length(embedding, 1) FROM pdf_chunks LIMIT 1;
```

## Deployment Checklist

- [ ] PostgreSQL 15+ installed with pgvector
- [ ] OpenAI API key configured and tested
- [ ] All required environment variables set
- [ ] Database initialized with schema.sql
- [ ] Sample data loaded and indexed
- [ ] Tests passing `mvn test`
- [ ] `mvn clean package` builds successfully
- [ ] Health check responds: `GET /agent/health`
- [ ] Sample query works: `POST /agent/query`

## Next Steps & Roadmap

**MVP+** (Next iteration):
1. Batch embedding injestion endpoint
2. Multi-document support
3. Hybrid search (semantic + keyword with BM25)
4. Query-specific filters (date ranges, keywords)
5. Answer caching

**Production** (v2.0):
1. Async processing (Spring WebFlux)
2. Redis cache for embeddings
3. Multi-model support (Claude, LLaMA)
4. Document chunking strategies
5. RAG metrics & observability
6. API rate limiting
7. Multi-user/tenant support

## References

- [pgvector GitHub](https://github.com/pgvector/pgvector/)
- [OpenAI API Docs](https://platform.openai.com/docs/)
- [Spring Boot WebClient](https://spring.io/guides/gs/consuming-rest-restjs/)
- [Testcontainers PostgreSQL](https://www.testcontainers.org/modules/databases/postgres/)
- [RAG Best Practices](https://huggingface.co/docs/hub/index-rag)
