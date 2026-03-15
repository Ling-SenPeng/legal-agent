# Payment Evidence Integration Plan

**Status:** Phase 1 - Refactoring & Preparation  
**Last Updated:** March 14, 2026

## Overview

This document outlines the multi-phase plan to integrate the `payment_records` database table as the primary evidence source for payment-related legal analysis, while maintaining backward compatibility with PDF-based chunk retrieval as a fallback.

## Current State (Phase 1 - Complete)

### Refactored Components

#### 1. **PaymentEvidenceRoute** (NEW)
- **Location:** `src/main/java/com/agent/service/PaymentEvidenceRoute.java`
- **Purpose:** Detects payment-related queries and routes to appropriate evidence sources
- **Methods:**
  - `isPaymentRelatedQuery(String query)` - Identifies payment intent
  - `extractPropertyReferences(String query)` - Finds property mentions
  - `requiresDateFiltering(String query)` - Detects date-based filtering needs
  - `shouldFallbackToChunks(int paymentRecordCount)` - Determines fallback necessity
- **Status:** Ready for integration
- **Dependencies:** None (pure utility)

#### 2. **CaseAnalysisModeHandler** (REFACTORED)
- **Location:** `src/main/java/com/agent/service/handler/CaseAnalysisModeHandler.java`
- **Changes:**
  - Added `PaymentEvidenceRoute` dependency for payment detection
  - Updated `retrieveAndMergeEvidence()` method with payment detection logic
  - Added TODO markers for Phase 2 payment_records integration
- **Current Flow:**
  1. Detects payment query using `PaymentEvidenceRoute.isPaymentRelatedQuery()`
  2. Logs payment detection and extracted metadata
  3. Falls back to chunk retrieval (payment_records lookup not yet implemented)
  4. Returns merged evidence chunks
- **Status:** Ready for Phase 2
- **Logging:** `[CASE_ANALYSIS_PAYMENT_ROUTING]` prefix for payment detection traces

#### 3. **RuleBasedTaskRouter** (ENHANCED)
- **Location:** `src/main/java/com/agent/service/RuleBasedTaskRouter.java`
- **Changes:** Added documentation note about future payment-specific routing
- **Note:** Current CASE_ANALYSIS routing continues to work; payment routing will be handled internally
- **Status:** No changes needed for Phase 2

#### 4. **RuleBasedCaseAnalysisContextBuilder** (DOCUMENTED)
- **Location:** `src/main/java/com/agent/service/analysis/RuleBasedCaseAnalysisContextBuilder.java`
- **Changes:** Added integration notes for future payment record support
- **Relevant Methods:**
  - `buildContextWithAuthorities()` - Will support blended payment + chunk facts
- **Status:** Ready to extend

## Phase 2 - PaymentRecordRepository Integration (TODO)

### Objectives
- [ ] Connect `PaymentRecordRepository` to database (currently interface-only)
- [ ] Implement database queries for payment_records table
- [ ] Add payment record to evidence chunk conversion

### Key Integration Points

#### In `CaseAnalysisModeHandler.retrieveAndMergeEvidence()`:
```java
// TODO: PAYMENT RECORDS INTEGRATION (Phase 2)
// When PaymentRecordRepository is integrated with database:
//
// if (isPaymentQuery) {
//     try {
//         List<PaymentRecord> paymentRecords = paymentEvidenceService
//             .getPaymentsByProperty(propertyAddress, propertyCity);
//         
//         if (!paymentRecords.isEmpty()) {
//             logger.info("Found {} payment records", paymentRecords.size());
//             return convertPaymentRecordsToChunks(paymentRecords);
//         }
//     } catch (Exception e) {
//         logger.warn("PaymentEvidenceService error - fallback to chunks", e);
//     }
// }
```

**Steps:**
1. Obtain property address/city from `PaymentEvidenceRoute.extractPropertyReferences()`
2. Query payment records via `PaymentEvidenceService.getPaymentsByProperty()`
3. Convert `PaymentRecord` objects to `EvidenceChunk` format
4. Return payment-sourced evidence
5. Fall back to chunk retrieval if no payment records found

### New Methods Needed

#### `CaseAnalysisModeHandler`
```java
/**
 * Convert PaymentRecord objects to EvidenceChunk format for unified processing.
 * Ensures payment evidence can flow through existing fact extraction pipeline.
 */
private List<EvidenceChunk> convertPaymentRecordsToChunks(List<PaymentRecord> records) {
    // TODO: Implement conversion logic
}
```

#### `RuleBasedCaseFactExtractor`
```java
/**
 * Extract facts directly from PaymentRecord objects.
 */
public List<CaseFact> extractFromPaymentRecords(
    List<PaymentRecord> records, 
    LegalIssueType issueType) {
    // TODO: Implement payment fact extraction
}
```

## Phase 3 - Smart Fallback Logic (TODO)

### Objectives
- [ ] Implement quality heuristics for when to fallback to chunks
- [ ] Add evidence source attribution in CaseFact
- [ ] Blend facts from multiple sources intelligently

### Decisions to Make

1. **Fallback Triggers:**
   - How many payment records trigger fallback to None?
   - How old can payment records be before chunks are considered?
   - Should missing date context always trigger chunk fallback?

2. **Evidence Blending:**
   - Should payment facts be weighted higher than chunk facts?
   - How to handle conflicting facts from different sources?
   - Should source attribution appear in legal answer?

3. **Date Filtering:**
   - Get CaseProfile.getDateOfSeparation() for post-separation filtering
   - Filter payment records to only post-separation payments
   - Implement in `PaymentEvidenceService` or `PaymentEvidenceRoute`?

## Deprecation Path for Old Payment Logic

### Text-Based Extraction Components

These components extract payment data from **PDF text** and should be deprecated once payment_records table is stable:

#### 1. **PaymentRecordExtractor**
- **Status:** DEPRECATION PENDING
- **Cleanup Checklist:**
  - [ ] Verify payment_records table is reliably populated
  - [ ] Remove usage from `CaseAnalysisModeHandler`
  - [ ] Keep as fallback for structured document parsing
  - [ ] Add `@Deprecated` annotation with migration message

#### 2. **MortgageStatementParser**
- **Status:** DEPRECATION PENDING
- **Cleanup Checklist:**
  - [ ] Verify structured sources eliminate OCR dependency
  - [ ] Remove usage from CASE_ANALYSIS pipeline
  - [ ] Document fallback-only conditions
  - [ ] Consider retiring if no use cases remain

#### 3. **PropertyPaymentAnalyzer**
- **Status:** REVIEW FOR ALIGNMENT
- **Consideration:** Ensure compatible with both PaymentRecord and chunk-based aggregation
- **Dependency on:** PaymentSummary DTO

### Database Layer

#### **PaymentRecordRepository** (Currently Interface-Only)
- **Status:** Needs Implementation
- **Required Methods:**
  ```java
  findByPdfDocumentId(Long pdfDocumentId)
  findByPropertyCity(String propertyCity)
  findByPropertyAddress(String propertyAddress)
  findByCategoryAndDateRange(String category, LocalDate from, LocalDate to)
  findMortgagePaymentsByPropertyAndDateRange(...)
  ```
- **Implementation Notes:**
  - Use JDBC with direct SQL (per design notes in repository interface)
  - Implement row mappers for PaymentRecord objects
  - Add WHERE clauses for property + date filtering

#### **PaymentRecordRowMapper** (Partially Implemented)
- **Location:** `src/main/java/com/agent/repository/PaymentRecordRowMapper.java`
- **Status:** Needs completion
- **Task:** Ensure all PaymentRecord fields mapped to database columns

## Code Search Locations for Migration

### Look for references to old payment extraction:
```bash
# Find chunk-based payment extraction
grep -r "paymentRecordExtractor" src/main/java
grep -r "mortgageStatementParser" src/main/java
grep -r "PaymentRecordExtractor" src/main/java
grep -r "MortgageStatementParser" src/main/java

# Find fact extraction from chunks
grep -r "extractFacts" src/main/java/com/agent/service/analysis

# Find payment-related issue types in fact extraction
grep -r "REIMBURSEMENT\|PROPERTY_CHARACTERIZATION" src/main/java
```

### Logging Patterns for Migration Tracking

New log prefix for payment router: `[CASE_ANALYSIS_PAYMENT_ROUTING]`

**Example output:**
```
[CASE_ANALYSIS_PAYMENT_ROUTING] Detected payment-related query
  Query: 'post separation mortgage reimbursement'
  [PAYMENT_ROUTE] query='post separation mortgage...' payment_related=true properties=[...] date_filter=true
[CASE_ANALYSIS_PAYMENT_ROUTING] Found 15 payment records
[CASE_ANALYSIS_PAYMENT_ROUTING] Using 15 payment-sourced chunks as evidence
```

## Database Schema Expectations

### payment_records Table

```sql
-- Expected columns based on PaymentRecord model
pdf_document_id    (FK to pdf_documents)
statement_index    (int)
statement_start    (DATE)
statement_end      (DATE)
payment_date       (DATE)
category           (VARCHAR) -- 'mortgage', 'escrow', 'tax', etc.
property_address   (VARCHAR)
property_city      (VARCHAR)
property_state     (VARCHAR)
loan_number        (VARCHAR)
total_amount       (DECIMAL)
principal_amount   (DECIMAL)
interest_amount    (DECIMAL)
escrow_amount      (DECIMAL)
confidence_score   (DOUBLE)
source_citation    (VARCHAR)
```

### Existing Tables
- `pdf_documents` - Metadata about uploaded documents
- `pdf_chunks` - Text chunks from OCR/parsing
- `pdf_payment_extraction_runs` - History of payment extraction operations

## Testing Strategy

### Unit Tests
- [ ] Test `PaymentEvidenceRoute.isPaymentRelatedQuery()` with various inputs
- [ ] Test property extraction heuristics
- [ ] Test date filtering detection

### Integration Tests (Phase 2)
- [ ] Test payment record retrieval from database
- [ ] Test conversion from PaymentRecord to EvidenceChunk
- [ ] Test fact extraction from payment records
- [ ] Test blending of payment + chunk facts

### E2E Tests (Phase 3)
- [ ] Test full CASE_ANALYSIS flow with payment questions
- [ ] Verify fallback to chunks when payment records missing
- [ ] Verify post-separation filtering applied correctly

## Migration Checklist

### Phase 1 (Complete)
- [x] Create PaymentEvidenceRoute utility
- [x] Add payment detection to CaseAnalysisModeHandler
- [x] Document integration points
- [x] Add TODO markers for Phase 2
- [x] Create this documentation

### Phase 2 (Next)
- [ ] Implement PaymentRecordRepository database methods
- [ ] Connect PaymentEvidenceService to repository
- [ ] Implement PaymentRecord to EvidenceChunk conversion
- [ ] Extract payment facts from records
- [ ] Test end-to-end with payment questions
- [ ] Remove chunk fallback from payment detection code (once tested)

### Phase 3 (Polish)
- [ ] Implement smart fallback heuristics
- [ ] Add evidence source attribution
- [ ] Blend facts from multiple sources intelligently
- [ ] Deprecate text-based payment extraction
- [ ] Update CI/CD for payment queries
- [ ] Document for legal team

## Key Design Principles

### 1. Separation of Concerns
- **PaymentEvidenceRoute:** Detection and routing logic
- **PaymentEvidenceService:** Payment evidence pipeline
- **PaymentRecordRepository:** Data access
- **CaseAnalysisModeHandler:** Orchestration

### 2. Gradual Migration
- Payment detection is safe and reversible
- Fallback to chunks always available
- No breaking changes to existing handlers

### 3. Backward Compatibility
- Existing CASE_ANALYSIS flow unchanged
- Payment-as-fallback doesn't affect non-payment queries
- Chunk retrieval remains available for all scenarios

### 4. Observability
- Detailed logging with `[CASE_ANALYSIS_PAYMENT_ROUTING]` prefix
- Payment route decision tracked
- Evidence sources attributed

## Questions for Legal Team

1. Should payment records be **primary** for REIMBURSEMENT issues, or should chunks always be consulted?
2. Should **post-separation filtering** be automatic, or should it be optional based on CaseProfile?
3. How should **missing property context** be handled? Fall back to chunks or ask user?
4. Should **payment facts** be **weighted differently** than chunk facts in analysis?
5. Should evidence **source attribution** (chunks vs. payment_records) appear in final answer?

## References

- **PaymentEvidenceService:** `src/main/java/com.agent.service/PaymentEvidenceService.java`
- **RuleBasedPaymentEvidenceService:** `src/main/java/com.agent.service/RuleBasedPaymentEvidenceService.java`
- **PaymentRecord Model:** `src/main/java/com.agent.model/PaymentRecord.java`  
- **PaymentSummary DTO:** `src/main/java/com.agent.model/PaymentSummary.java`
- **LegalEvidenceLine DTO:** `src/main/java/com.agent.model/LegalEvidenceLine.java`
- **PaymentRecordRepository:** `src/main/java/com.agent.repository/PaymentRecordRepository.java`
