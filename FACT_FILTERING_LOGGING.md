# Fact Filtering & Assignment Debug Logging

## Overview

Comprehensive DEBUG-level logging has been added throughout the fact filtering pipeline in CASE_ANALYSIS mode. This logging enables verification that:

1. **Raw facts** entering the pipeline
2. **Strict filter decisions** (accept/reject) with detailed reasons
3. **Keyword matching** results for rule elements  
4. **Rendering filter decisions** with rejection reasons
5. **Final facts** that appear in APPLICATION TO RULE output

## Pipeline Stages

### 1. Raw Fact Pool (Start of Section)

**Log Tag:** `[FACT_FILTER_PIPELINE_START]`

```
[FACT_FILTER_PIPELINE_START] Fact pool: 15 total | 10 favorable | 5 unfavorable
```

Shows the complete fact inventory available for the APPLICATION TO RULE section.

### 2. Strict Filter (Before Rule-Element Assignment)

**Log Tag:** `[STRICT_FILTER]`

Applied in `findSupportingFactsForElement()` before keyword matching.

```
[STRICT_FILTER] Processing 10 favorable facts for element: "Post-separation payment was made..."
[STRICT_FILTER] Accepted facts:
  ✓ I paid $20,000 in post-separation mortgage payments on community property
  ✓ The payment was made in 2023 from my separate property account
[STRICT_FILTER] Rejected facts:
  ✗ 23
  ✗ real and personal $
  ✗ Description
```

Rejection reasons logged by `logFactFilter()`:

| Reason | Meaning |
|--------|---------|
| `too_short` | < 20 chars without meaningful keywords |
| `numeric_only` | Pure numeric or > 70% digits/currency |
| `table_fragment` | Table header without context |
| `boilerplate_form_text` | Form labels/boilerplate ("petitioner:", "check the box", etc.) |
| `ocr_garbage` | Broken text with < 50% alphabetic characters |
| `isolated_table_header` | "Description", "Date", etc. without surrounding context |

### 3. Keyword Matching (After Strict Filter)

**Log Tag:** `[STRICT_FILTER]` (continued)

```
[STRICT_FILTER] Keyword matching: 2 of 3 accepted facts matched element keywords
```

Logs how many accepted facts matched the rule element's keywords (e.g., "payment" element matches facts containing "paid").

### 4. Rendering Filter (Before Display)

**Log Tag:** `[RENDERING_FILTER]`

Applied in `filterHighQualityFactsWithLogging()` before facts appear in OUTPUT.

```
[RENDERING_FILTER] ACCEPTED | I paid $20,000 in mortgage | reason=passes_all_filters
[RENDERING_FILTER] Rejected at rendering stage:
  ✗ 23 | reason=numeric_only
  ✗ real and personal $ | reason=boilerplate_form_text
```

Rendering rejection reasons match strict filter criteria:
- `too_short`, `numeric_only`, `ocr_garbage`, `boilerplate_form_text`, `table_fragment`

### 5. Final Rendered Facts

**Log Tag:** `[FACT_FILTER_PIPELINE]`

```
[FACT_FILTER_PIPELINE] Final rendered for "Post-separation payment...": 2 facts
  ✓ RENDERED: I paid $20,000 in post-separation mortgage...
  ✓ RENDERED: The payment was made in 2023 from my...
```

Shows the exact facts that will appear in the APPLICATION TO RULE section.

## Enabling Debug Logging

Set logging to DEBUG level to see fact filter logs:

**logback-spring.xml or application.yml:**
```yaml
logging:
  level:
    com.agent.service.handler: DEBUG
```

Or in code:
```java
// Logs appear when logger.isDebugEnabled() == true
```

## Log Flow Diagram

```
Raw Fact Pool
    ↓
[STRICT_FILTER] Processing & decision
    ├─ ACCEPTED facts → logFactFilter(..., true, reason)
    └─ REJECTED facts → logFactFilter(..., false, reason)
    ↓
Keyword Matching
    ├─ Match counts logged
    ↓
Keyword-matching results
    ├─ List by rule element
    ↓
[RENDERING_FILTER] Final filtering  
    ├─ ACCEPTED → logFactFilter
    └─ REJECTED → logFactFilter
    ↓
[FACT_FILTER_PIPELINE] Final rendered facts
    ↓
APPLICATION TO RULE Section (OUTPUT)
```

## Example Output

**Full pipeline for one rule element:**

```
[STRICT_FILTER] Processing 6 favorable facts for element: "Post-separation payment was made to satisfy a community obligation"
[STRICT_FILTER] Accepted facts:
  ✓ I paid $20,000 in post-separation mortgage payments on community property
  ✓ The payment was made in 2023 from my separate property account
  ✓ Community property mortgaged during marriage for home purchase
[STRICT_FILTER] Rejected facts:
  ✗ 23
  ✗ real and personal $
  ✗ Description

[STRICT_FILTER] Keyword matching: 2 of 3 accepted facts matched element keywords

[FACT_FILTER_PIPELINE] Element: "Post-separation payment was made to satisfy a community obligation"
  Assigned through strict filter + keyword matching: 2 facts
    - I paid $20,000 in post-separation mortgage payments on community property
    - The payment was made in 2023 from my separate property account

[RENDERING_FILTER] ACCEPTED | I paid $20,000 in post-separation... | reason=passes_all_filters
[RENDERING_FILTER] ACCEPTED | The payment was made in 2023 from... | reason=passes_all_filters
[RENDERING_FILTER] Rejected at rendering stage:
  ✗ Description | reason=table_fragment

[FACT_FILTER_PIPELINE] Final rendered for "Post-separation payment...": 2 facts
  ✓ RENDERED: I paid $20,000 in post-separation mortgage payments on community property
  ✓ RENDERED: The payment was made in 2023 from my separate property account
```

## Troubleshooting

### Noisy snippets still appearing in output?

1. **Check logger.isDebugEnabled()** - Rendering stage requires debug logging to be active
2. **Enable debug logs and run again** - See which facts pass/fail filter
3. **Check rejection reasons** - Compare your snippets against the 6 rejection criteria
4. **Verify fact content** - Facts must be marked `isFavorable()=true` and match the issue type

### Facts missing from output that should be present?

1. **Check STRICT_FILTER logs** - Was the fact accepted by strict filter?
   - If rejected, see the reason (usually too short, numeric, or OCR garbage)
2. **Check keyword matching** - Did the fact match the element keywords?
   - Fact may be high-quality but not relevant to specific element
3. **Check RENDERING_FILTER logs** - Was the fact accepted by rendering filter?
   - Two-tier filtering means a fact can pass strict but fail rendering

### Pipeline not logging anything?

1. Verify `logger.isDebugEnabled()` returns `true` for the handler package
2. Check that `DEBUG` level is set in logging configuration
3. Run with `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` for test execution

## Implementation Details

### Methods with Logging

| Method | Log Tags | Purpose |
|--------|----------|---------|
| `appendApplicationToRuleSection()` | `[FACT_FILTER_PIPELINE_START]`, `[FACT_FILTER_PIPELINE]` | Logs pipeline start and per-element assignments |
| `findSupportingFactsForElement()` | `[STRICT_FILTER]` | Logs strict filter accept/reject + keyword matching |
| `filterHighQualityFactsWithLogging()` | `[RENDERING_FILTER]` | Logs rendering-stage filtering decisions |
| `logFactFilter()` | `[FACT_FILTER]` | Low-level logging for individual fact decisions |
| `logFactPoolPreview()` | `[FACT_FILTER_PIPELINE_START]` | Fact pool statistics |
| `logRuleElementPipeline()` | `[FACT_FILTER_PIPELINE]` | Element-level assignment details |
| `logFinalRenderedFacts()` | `[FACT_FILTER_PIPELINE]` | Final rendered fact list |

### Key Code Locations

- **Strict Filter Method:** [CaseAnalysisModeHandler.java](src/main/java/com/agent/service/handler/CaseAnalysisModeHandler.java#L1999) - `isStrictlyHighQualityFact()`
- **Rendering Filter Method:** [CaseAnalysisModeHandler.java](src/main/java/com/agent/service/handler/CaseAnalysisModeHandler.java#L2168) - `filterHighQualityFactsWithLogging()`
- **Pipeline Orchestration:** [CaseAnalysisModeHandler.java](src/main/java/com/agent/service/handler/CaseAnalysisModeHandler.java#L1736) - `appendApplicationToRuleSection()`

## Test Coverage

Test: `CaseAnalysisModeHandlerTest.testDebugLoggingShowsFilteringPipeline()`

Verifies that:
- Handler completes successfully with debug logging enabled
- Quality facts pass through the pipeline
- Noisy facts are appropriately filtered
- No errors occur due to comprehensive logging

Run with:
```bash
mvn test -Dtest=CaseAnalysisModeHandlerTest#testDebugLoggingShowsFilteringPipeline
```

All 203 tests pass including logging test.
