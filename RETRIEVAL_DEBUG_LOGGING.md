# Retrieval Debug Logging Guide

Complete structured logging for the retrieval pipeline. Logs are split into two levels for flexible debugging.

## Overview

The retrieval service now logs structured information at key points:
1. **Plan Phase** - Query optimization metadata
2. **Retrieval Phase** - Execution metrics and fallback tracking
3. **Result Phase** - Score details and chunk previews

## Log Levels

### INFO Level (Production Friendly)
Concise structured format for monitoring and alerting. Safe for high-volume logging.

### DEBUG Level (Development/Troubleshooting)
Detailed chunk previews and execution traces. Enable only when diagnosing retrieval issues.

---

## Log Examples

### 1. Plan Phase - INFO Level

```
[RETRIEVAL_PLAN] originalQuery="List all TRO events" intent=extract_events outputFormat=timeline 
entities=[TRO, temporary restraining order] keywordQueries=2 entities_count=2
```

**Fields:**
- `originalQuery` - User's original question
- `intent` - Detected intent (extract_events, summarize, compare, etc.)
- `outputFormat` - Expected output format (timeline, narrative, table, etc.)
- `entities` - Extracted legal entities and concepts
- `keywordQueries` - Number of planned keyword search queries
- `entities_count` - Count of extracted entities

### 2. Plan Phase - DEBUG Level

```
[RETRIEVAL_PLAN_DETAIL] cleanedQuery="TRO events" 
vectorQuery="TRO temporary restraining order restraining order" 
answerInstruction="Extract and list all events in chronological order"
```

**Fields:**
- `cleanedQuery` - Cleaned user query (instruction words removed)
- `vectorQuery` - Query optimized for embedding vector search
- `answerInstruction` - LLM instruction for answer format

---

### 3. Keyword Search - Fallback Events

When keyword queries return zero hits:

```
[KEYWORD_FALLBACK] Planned queries returned 0 hits, trying cleaned query: "TRO events"
```

If cleaned query also fails:

```
[KEYWORD_FALLBACK] Cleaned query returned 0 hits, trying original query: "List all TRO events"
```

**When to See:** 
- Very specific keyword queries (e.g., exact acronyms) find no matches
- Fallback chain activates: planned → cleaned → original

---

### 4. Keyword Search Results - DEBUG Level

```
[KEYWORD_SEARCH_RESULT] queries_attempted=3 unique_chunks=12 fallback_used=true avg_score=0.45
```

**Fields:**
- `queries_attempted` - Number of keyword search attempts
- `unique_chunks` - Unique chunks after deduplication
- `fallback_used` - Whether fallback chain was triggered
- `avg_score` - Average keyword score across results

---

### 5. Retrieval Snapshot - INFO Level (Main Summary)

```
[RETRIEVAL_SNAPSHOT] intent=extract_events entities=2 keyword_hits=12 vector_hits=8 
merged_hits=15 low_conf=false fallback=true
```

**Fields:**
- `intent` - Query intent
- `entities` - Count of entities extracted
- `keyword_hits` - Keyword search result count
- `vector_hits` - Vector search result count
- `merged_hits` - Unique chunks after merge/dedup
- `low_conf` - Low confidence flag (vector scores < 0.4)
- `fallback` - Whether fallback was used

---

### 6. Retrieval Results - DEBUG Level (Top Chunks)

```
[RETRIEVAL_CHUNKS] Top merged chunks:
  [1] chunk_id=42 page=5 key_score=0.8500 vec_score=0.6200 final_score=1.6800 
      text="The court issued a temporary restraining order on January 5, 2023..."
  [2] chunk_id=43 page=5 key_score=0.7200 vec_score=0.5800 final_score=1.4800 
      text="The TRO was granted for 14 days pending the next hearing..."
  [3] chunk_id=44 page=6 key_score=0.6500 vec_score=0.4900 final_score=1.3400 
      text="Parties agreed to extend the restraining order..."
```

**Fields per chunk:**
- `chunk_id` - Database chunk identifier
- `page` - Page number where chunk originates
- `key_score` - Keyword search score (0 = no match, higher = better)
- `vec_score` - Vector/semantic similarity score (0-1)
- `final_score` - Combined score (keyword×1.5 + vector×1.0)
- `text` - First 100 characters of chunk content

---

### 7. Vector-Only Search (When Hybrid Disabled)

```
2026-03-11 16:53:26 INFO  [RETRIEVAL_PLAN] originalQuery="Find relevant case law" ...
2026-03-11 16:53:26 INFO  Retrieved 10 evidence chunks via vector-only search
2026-03-11 16:53:26 INFO  [RETRIEVAL_SNAPSHOT] intent=find_facts entities=0 keyword_hits=0 
                          vector_hits=10 merged_hits=10 low_conf=false fallback=false
2026-03-11 16:53:26 DEBUG [RETRIEVAL_CHUNKS] Top merged chunks:
                          [1] chunk_id=100 page=2 key_score=null vec_score=0.8200 ...
```

---

## Configuration

### Enable DEBUG Logging

Add to `application.yml`:

```yaml
logging:
  level:
    com.agent.service.RetrievalService: DEBUG
```

Or via environment variable:

```bash
export LOGGING_LEVEL_COM_AGENT_SERVICE_RETRIEVALSERVICE=DEBUG
```

### Structured Log Parsing

JSON format ready (can extend to structured JSON):

```json
{
  "timestamp": "2026-03-11T16:53:26Z",
  "level": "INFO",
  "logger": "com.agent.service.RetrievalService",
  "message": "[RETRIEVAL_SNAPSHOT]",
  "fields": {
    "intent": "extract_events",
    "entities": 2,
    "keyword_hits": 12,
    "vector_hits": 8,
    "merged_hits": 15,
    "low_confidence": false,
    "fallback_used": true
  }
}
```

---

## Interpreting Low Confidence

When `low_conf=true` in the snapshot:

```
[RETRIEVAL_SNAPSHOT] ... low_conf=true ...
[RETRIEVAL_CHUNKS] Top merged chunks:
  [1] ... vec_score=0.3200 final_score=...
```

**Meaning:** Vector scores are below 0.4 threshold
- Query may be ambiguous or outside document scope
- Consider reviewing keyword search coverage
- May want to expand search with fallbacks

---

## Interpreting Fallback Flags

When `fallback=true`:

1. **Keyword fallback:** Planned queries returned 0 hits
   - Issue: Query too specific for database text
   - Solution: Clean up query terms, expand acronym synonyms

2. **Vector fallback:** Embedding returned no semantic matches
   - Issue: Query semantically misaligned with documents
   - Solution: Rephrase query or add more context

3. **Both:** Both search lanes failed
   - Issue: Query outside document scope
   - Solution: Clarify question or provide more context

---

## Tuning Based on Logs

### High Final Scores (> 2.0)
Keyword match is strong (expected for legal docs with exact terminology)
- **Action:** Keep as-is; exact terminology is valuable

### Low Final Scores (< 0.5)
Weak match on both keyword and vector
- **Action:** Fallback chain activated; check if reasonable results returned

### Imbalanced Scores
Keyword >> Vector or Vector >> Keyword
- **Action:** Adjust weights (KEYWORD_SCORE_WEIGHT, VECTOR_SCORE_WEIGHT)
- Current weights: keyword=1.5, vector=1.0

### Frequent Fallbacks
> 20% of queries trigger fallback
- **Action:** Review RetrievalPlanner intent/entity detection
- May need better acronym expansion or instruction word removal

---

## Example: Debugging a Query

**User Query:** "Show me all TRO issuances and related motions"

**Logs:**

```
INFO  [RETRIEVAL_PLAN] originalQuery="Show me all TRO issuances and related motions" 
      intent=extract_events outputFormat=timeline entities=[TRO, motions] keywordQueries=2

DEBUG [RETRIEVAL_PLAN_DETAIL] cleanedQuery="TRO issuances motions"
      vectorQuery="TRO temporary restraining order issuances motions"

INFO  Keyword search results: 5 hits (after dedup)
INFO  Vector search results: 8 hits

INFO  [RETRIEVAL_SNAPSHOT] intent=extract_events entities=2 keyword_hits=5 vector_hits=8 
      merged_hits=10 low_conf=false fallback=false

DEBUG [RETRIEVAL_CHUNKS] Top merged chunks:
  [1] chunk_id=100 page=3 key_score=0.9200 vec_score=0.7100 final_score=2.1900 
      text="In Motion for Temporary Restraining Order filed March 5, 2023..."
  [2] chunk_id=101 page=3 key_score=0.8700 vec_score=0.6800 final_score=1.9800 
      text="Court granted TRO effective immediately for motion to..."
```

**Interpretation:**
- ✓ Intent correctly detected as extract_events
- ✓ Both keyword and vector searches found matches
- ✓ Final scores are healthy (> 1.5)
- ✓ Top chunks are relevant to the query
- **Action:** Retrieval is working well; proceed with LLM drafting

---

## Metrics to Monitor

### Production Telemetry

Track these metrics to assess retrieval health:

1. **Fallback Rate:** % of queries triggering fallback
   - Target: < 10%
   - High rate indicates poor query planning

2. **Low Confidence Rate:** % of queries with low_conf=true
   - Target: < 5%
   - High rate indicates weak embeddings or misaligned corpus

3. **Merge Efficiency:** (merged_hits / (keyword_hits + vector_hits)) × 100
   - Target: 60-80% (some overlap is expected)
   - Low = few duplicates; high = large overlap

4. **Final Score Distribution:**
   - Mean final_score: should be > 1.0
   - Median: target 1.2-1.8
   - Low median suggests weak matches

---

## Next Steps

- Enable DEBUG logging in development environments
- Monitor INFO logs in production for fallback/confidence anomalies
- Tune scoring weights based on typical final_score distributions
- Periodically review top chunks to validate relevance
