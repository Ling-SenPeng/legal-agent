# Retrieval Pipeline Design Guide

## Overview

The RAG retrieval pipeline is designed for **legal document retrieval** where:
1. **Precision matters** - False positives are costly
2. **Keyword matching is reliable** - Legal documents use consistent terminology
3. **Vector search adds semantic coverage** - Catches paraphrasing and synonyms
4. **Events often span chunks** - Temporal sequences are split across boundaries

## Why Not Use Raw User Queries Directly?

Raw user queries often contain:
- **Instruction words**: "List all", "Show me", "Extract" - these confuse search indexes
- **Vague phrasing**: "mentioned in the document" - adds noise
- **Mixed concerns**: Multiple unrelated questions in one query
- **Pronoun references**: "it", "they" without context

The `RetrievalPlanner` normalizes queries into:
- **Cleaned keyword queries** - Instruction words removed, exact phrases bolded
- **Focused vector query** - Entities expanded, synonyms included
- **Intent metadata** - Timeline? Summary? Count? Events?

This allows:
- Keyword search to use precise literal matching
- Vector search to use concise semantic signals
- Downstream LLM to understand expected output format

## Why Keyword Matching is Heavily Trusted

Legal documents exhibit:
- **Consistent terminology**: "TRO" always means "temporary restraining order"
- **Explicit references**: Case names, dates, party names are stated verbatim
- **Sparse distribution**: Key concepts appear in limited locations (not diffuse)

Keyword search:
- Returns exact phrase matches (highest precision)
- Scales linearly with document size
- Indexes are deterministic and auditable

Vector embeddings:
- Miss exact phrase boundaries
- Can match semantically similar but legally distinct concepts
- Slower for exact fact retrieval

**Therefore**: Weight keyword scores higher (1.5x) than vector scores (1.0x) in the final ranking.

## Why Event/Timeline Queries Benefit from Neighbor Expansion

Legal narratives often structure events as:
```
Chunk N:   "January 15: Plaintiff filed petition..."
Chunk N+1: "Judge ordered TRO to be served..."
Chunk N+2: "Hearing scheduled for February 1..."
```

Single chunk retrieval might catch "Judge ordered TRO" but miss the date context.

**Neighbor expansion**:
- After selecting top chunks, include adjacent chunks (previous/next)
- Creates richer context for timeline reconstruction
- Deduplicates to avoid redundancy
- Only applies to `extract_events` and `timeline` intents

## Future Extension: LLM Planner Fallback

Current fallback chain (for zero-hit recovery):
1. Try planned keyword queries
2. Fall back to cleaned query
3. Fall back to original query

**Future**: Replace step 2-3 with LLM-based query rephrasing:
```
If keyword_hits == 0:
  rephrased = llm.rewrite_for_search(originalQuery)
  keyword_hits = search(rephrased)
```

This would be plugged into `searchByKeywordQueries()` in RetrievalService.

## Scoring Formula

Final merged score balances keyword and vector contributions:

```
if keyword_score exists and vector_score exists:
  finalScore = (keyword_score * 1.5) + (vector_score * 1.0) + exactPhraseBoost
else if keyword_score exists:
  finalScore = keyword_score + exactPhraseBoost
else if vector_score exists:
  finalScore = vector_score
```

**Rationale**:
- Keyword match: 1.5x weight (literal match is strong signal in legal docs)
- Vector match: 1.0x weight (semantic similarity, less reliable alone)
- Exact phrase boost: +0.3 if chunk text literally contains an entity phrase
- Boost is modest to avoid over-weighting; tuned via `EXACT_PHRASE_BOOST` constant

## Logging and Observability

Three logging levels:

**INFO (request-level summary)**:
- Original query, detected intent
- Total keyword hits, vector hits, merged hits
- Fallbacks triggered
- Low confidence signal
- Top 3-5 result scores

**DEBUG (detailed execution)**:
- All planned keyword queries executed
- Individual result scores
- Exact phrase matches
- Neighbor chunks added
- Full RetrievalDebugSnapshot

**WARN** (when retrieval struggles):
- Zero keyword hits
- Zero vector hits
- Low-confidence detection triggered
- Significant fallback usage

## Configuration & Tuning

Named constants in `RetrievalService`:
- `KEYWORD_SCORE_WEIGHT = 1.5` - How much keyword score is trusted
- `VECTOR_SCORE_WEIGHT = 1.0` - How much vector score is trusted
- `EXACT_PHRASE_BOOST = 0.3` - Bonus for exact phrase matches
- `LOW_CONFIDENCE_VECTOR_THRESHOLD = 0.4` - Vector score below this triggers low-confidence signal

To tune retrieval quality:
1. Run a test batch of queries
2. Analyze retrieved scores in debug logs
3. Adjust weights
4. Re-test to see impact

## Testing Strategy

Key test cases:
1. **Acronym expansion** - "TRO" expands and retrieves "temporary restraining order"
2. **Multi-intent handling** - "extract_events" vs "summarize" choose different intents
3. **Keyword + vector merge** - Both lanes contribute fairly
4. **Fallback paths** - Zero hits trigger fallback without failure
5. **Neighbor expansion** - Timeline queries include adjacent chunks
6. **Exact phrase boost** - Literal phrase matches score higher

See test file for implementations.
