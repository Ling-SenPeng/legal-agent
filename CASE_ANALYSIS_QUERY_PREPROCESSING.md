# CASE_ANALYSIS Query Preprocessing Architecture

## Overview

The CASE_ANALYSIS mode now includes a sophisticated query preprocessing pipeline that improves retrieval quality by removing analysis framing noise while maintaining routing signals and fact content.

## The Problem

User queries in CASE_ANALYSIS mode typically contain analysis framing language mixed with fact-bearing content:

```
"Based on these facts, do I have a strong reimbursement claim for 
post-separation mortgage payments on the Newark house?"
```

This framing language:
- ✅ **Helpful for routing**: "Based on these facts" signals CASE_ANALYSIS intent
- ❌ **Harmful for retrieval**: "do I have" and "are these facts" reduce vector search recall/precision

## Solution: Three-Stage Pipeline

### Stage 1: Query Cleaning (CaseAnalysisQueryCleaner)

**Purpose**: Strip analysis framing phrases before retrieval while keeping routing signals intact.

**Implementation**: Rule-based phrase removal with 30+ common analysis framing patterns:

```java
Input:  "Based on these facts, do I have a strong reimbursement claim..."
Output: "reimbursement claim post-separation mortgage payments newark house"
```

**Key Features**:
- Deterministic (no ML or randomness)
- Handles 30+ common analysis phrases:
  - "based on these facts"
  - "do I have"
  - "analyze my case"
  - "how strong is my"
  - "evaluate my claim"
  - ...and more

- Case-insensitive matching
- Whitespace normalization (removes extra spaces)
- Content validation (ensures ≥2 words remain)

**Preserved**: All subject matter - contracts, dates, financial figures, legal concepts

### Stage 2: Issue Detection (CaseIssueExtractor)

**Purpose**: Identify legal issues from the cleaned query to guide multi-query generation.

**Input**: Cleaned query (free of framing noise)

**Output**: List of detected CaseIssue objects with confidence scores

```
Query: "reimbursement post-separation mortgage payments"
Issues: [REIMBURSEMENT (confidence: 0.85)]
```

### Stage 3: Multi-Query Construction (CaseAnalysisRetrievalQueryBuilder)

**Purpose**: Generate 1-5 optimized retrieval query variants for comprehensive fact retrieval.

**Strategy**: Combine cleaned query with issue-specific keywords to reach different document sections.

**Example**:
```
Cleaned Query: "reimbursement post-separation mortgage payments newark"
Issues: [REIMBURSEMENT, PROPERTY_CHARACTERIZATION]

Generated Retrieval Queries:
  1. "reimbursement post-separation mortgage payments newark"        [core query]
  2. "reimbursement reimburse payment expense mortgage benefit"      [REIMBURSEMENT keywords]
  3. "reimbursement expense"                                           [core + keyword]
  4. "reimbursement post-separation mortgage"                         [sub-terms]
  5. "property characterization community separate"                  [PROPERTY_CHARACTERIZATION issue]
```

**Issue-Keyword Mapping**:
- **REIMBURSEMENT**: payment, expense, mortgage, benefit, reimburse
- **SUPPORT**: alimony, spousal, child support, income, guideline
- **PROPERTY_CHARACTERIZATION**: property, community, separate, acquisition, title
- **CUSTODY**: children, parenting, visitation, schedule, arrangement
- **TRACING**: trace, separate property, commingled, source, funds
- **EXCLUSIVE_USE**: family home, occupancy, residence, possession
- **RESTRAINING_ORDER**: protective order, abuse, harassment, injunction
- **OTHER**: legal, issue, claim, dispute

## Retrieval & Merging

### Execute Multiple Queries

Each generated query is executed against the retrieval service:

```java
for (String query : retrievalQueries) {
    List<EvidenceChunk> results = retrievalService.retrieveEvidence(query, topK);
    // Accumulate results
}
```

### Deduplication Strategy

Chunks that appear in multiple query results are deduplicated:

```java
// Keep chunk with highest similarity score
if (chunkMap.containsKey(chunkId)) {
    EvidenceChunk existing = chunkMap.get(chunkId);
    if (chunk.similarity() > existing.similarity()) {
        chunkMap.put(chunkId, chunk);  // Update with better score
    }
}
```

### Result Quality Improvement

- **Recall ⬆️**: Multiple queries catch more relevant facts (precision/recall trade-off)
- **Relevance ⬇️**: Topic-specific queries rank most relevant facts higher
- **Overhead ✅**: Max 5 queries keeps retrieval time acceptable

## Integration with CaseAnalysisModeHandler

The preprocessing pipeline is integrated into the CASE_ANALYSIS handler:

```python
Original Query
    ↓
Strip Analysis Noise (CaseAnalysisQueryCleaner)
    ↓
Cleaned Query → Extract Issues (CaseIssueExtractor)
    ↓
Build Retrieval Queries (CaseAnalysisRetrievalQueryBuilder)
    ↓
Execute Multiple Retrieval Queries
    ↓
Deduplicate & Merge Chunks
    ↓
Build Context (CaseAnalysisContextBuilder) with merged evidence
    ↓
Generate Analysis Result & Format Answer
    ↓
Return ModeExecutionResult
```

## Benefits

### For Users
- More relevant evidence chunks retrieved
- Better fact matching for analysis
- Improved confidence in assessments

### For the System
- Same retrieval latency (still top-k from each query)
- Deterministic, debuggable processing
- No external dependencies (rule-based, not LLM)
- Easy to tune (phrase list, issue keywords)

## Testing Strategy

### Unit Tests
- **CaseAnalysisQueryCleaner**: 10 tests covering phrase removal, whitespace, edge cases
- **CaseAnalysisRetrievalQueryBuilder**: 12 tests covering query generation, deduplication, multi-issue handling
- **Handler Integration**: Updated to handle new preprocessing pipeline

### Integration Tests
- **Query Preprocessing Pipeline**: 8 comprehensive tests covering:
  - Real-world query preprocessing (REIMBURSEMENT, CUSTODY, PROPERTY)
  - Complex multi-issue scenarios
  - Over-cleaning edge cases
  - Query deduplication
  - Legal term preservation

## Example: Real-World Query Rewrite

**Original User Query:**
```
"Based on these facts, could I have a strong reimbursement claim for 
post-separation mortgage payments? Also concerned about custody and 
the property being in my name."
```

**After Preprocessing:**
1. **Cleaned Query**: 
   ```
   "reimbursement mortgage payments custody property name"
   ```

2. **Detected Issues**: REIMBURSEMENT, CUSTODY, PROPERTY_CHARACTERIZATION

3. **Retrieval Queries** (5 total):
   ```
   1. "reimbursement mortgage payments custody property name"
   2. "reimbursement reimburse payment expense mortgage benefit"
   3. "custody parenting children schedule care"
   4. "property characterization community separate separate property"
   5. "reimbursement expense payment"
   ```

4. **Evidence Retrieved**: Chunks matching all three issue areas

5. **Analysis**: More comprehensive assessment using merged evidence from multiple query angles

## Configuration & Tuning

### To adjust analysis noise phrases:
Edit `ANALYSIS_NOISE_PHRASES` in `CaseAnalysisQueryCleaner.java`

### To adjust issue keywords:
Edit `ISSUE_KEYWORDS` map in `CaseAnalysisRetrievalQueryBuilder.java`

### To change max queries:
Modify `maxQueries = 5` in `CaseAnalysisRetrievalQueryBuilder.buildQueries()`

### To change content threshold:
Adjust word count check in `CaseAnalysisQueryCleaner.hasSignificantContent()`
