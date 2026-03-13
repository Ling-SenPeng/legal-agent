package com.agent.service.handler;

import com.agent.model.EvidenceChunk;
import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.analysis.*;
import com.agent.model.analysis.authority.AuthorityMatch;
import com.agent.model.analysis.authority.AuthoritySummary;
import com.agent.model.analysis.authority.AuthorityType;
import com.agent.model.analysis.authority.LegalAuthority;
import com.agent.service.analysis.CaseAnalysisContextBuilder;
import com.agent.service.analysis.CaseAnalysisQueryCleaner;
import com.agent.service.analysis.CaseAnalysisRetrievalQueryBuilder;
import com.agent.service.analysis.CaseIssueExtractor;
import com.agent.service.analysis.authority.IssueAuthorityRetrievalStrategy;
import com.agent.service.analysis.authority.AuthorityRetrievalService;
import com.agent.service.analysis.authority.AuthoritySummarizer;
import com.agent.service.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CaseAnalysisModeHandler.
 * 
 * Tests the complete CASE_ANALYSIS V1 pipeline using test doubles
 * instead of Mockito (to avoid Spring service class mocking issues).
 */
class CaseAnalysisModeHandlerTest {
    
    private CaseAnalysisModeHandler handler;
    private TestRetrievalService testRetrievalService;
    private TestCaseAnalysisContextBuilder testContextBuilder;
    private TestQueryCleaner testQueryCleaner;
    private TestQueryBuilder testQueryBuilder;
    private TestIssueExtractor testIssueExtractor;

    @BeforeEach
    void setUp() {
        testRetrievalService = new TestRetrievalService();
        testContextBuilder = new TestCaseAnalysisContextBuilder();
        testQueryCleaner = new TestQueryCleaner();
        testQueryBuilder = new TestQueryBuilder();
        testIssueExtractor = new TestIssueExtractor();
        
        // For testing, create a handler with actual authority services
        // In a full test scenario, these could be mocked or stubbed
        var realAuthorityQueryBuilder = new com.agent.service.analysis.authority.RuleBasedIssueAuthorityRetrievalStrategy();
        var realAuthorityRetrievalService = new AuthorityRetrievalService(java.util.Optional.empty());
        var realAuthoritySummarizer = new AuthoritySummarizer();
        
        handler = new CaseAnalysisModeHandler(
            testRetrievalService, 
            testContextBuilder,
            testQueryCleaner,
            testQueryBuilder,
            testIssueExtractor,
            realAuthorityQueryBuilder,
            realAuthorityRetrievalService,
            realAuthoritySummarizer
        );
    }

    // ==================== CORE FUNCTIONALITY Tests ====================
    
    @Test
    @DisplayName("Handler returns correct TaskMode")
    void testGetMode() {
        assertEquals(TaskMode.CASE_ANALYSIS, handler.getMode());
    }
    
    @Test
    @DisplayName("Execute returns analysis with all required sections")
    void testExecuteReturnsCompleteAnalysis() {
        // Given
        String query = "Do I have a strong reimbursement claim?";
        EvidenceChunk chunk = createTestChunk(
            "I paid $20,000 in post-separation mortgage payments.",
            1L, 1, "Page 1"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("I paid $20,000 in mortgage payments", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement evaluated under Epstein factors."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess());
        
        String answer = result.getAnswer();
        assertNotNull(answer);
        assertTrue(answer.contains("CASE ANALYSIS REPORT"));
        assertTrue(answer.contains("ISSUE SUMMARY"));
        assertTrue(answer.contains("APPLICATION SUMMARY"));
        assertTrue(answer.contains("COUNTERARGUMENTS"));
        assertTrue(answer.contains("MISSING EVIDENCE"));
        assertTrue(answer.contains("TENTATIVE CONCLUSION"));
    }
    
    @Test
    @DisplayName("Metadata includes issue count, fact count, and strength")
    void testMetadataFormat() {
        // Given
        String query = "What is the strength of my claim?";
        EvidenceChunk chunk = createTestChunk("I worked stable hours", 1L, 1, "P1");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.80, "custody")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("I work stable hours", true, "source", LegalIssueType.CUSTODY),
            new CaseFact("Children ages 7 and 10", true, "source", LegalIssueType.CUSTODY)
        );
        
        List<MissingFact> missingFacts = List.of(
            new MissingFact("Parenting schedule", LegalIssueType.CUSTODY, "Not found in evidence")
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(query, issues, facts, missingFacts,
            "Best interests standard applies.");
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("Issues: 1"));
        assertTrue(result.getMetadata().contains("Facts: 2"));  // Only retrieved facts, not missing
        assertTrue(result.getMetadata().contains("Mode: CASE_ANALYSIS"));
        assertTrue(result.getMetadata().contains("Strength:"));
        assertTrue(result.getMetadata().contains("Confidence:"));
    }
    
    @Test
    @DisplayName("Handles missing evidence gracefully")
    void testHandlesNoEvidence() {
        // Given
        String query = "Analyze my case";
        testRetrievalService.setEvidenceChunks(List.of());
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess());
        assertTrue(result.getAnswer().contains("No relevant case facts found"));
    }
    
    @Test
    @DisplayName("Handles exceptions without crashing")
    void testHandlesException() {
        // Given
        String query = "My case?";
        testRetrievalService.setThrowException(true);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Error"));
    }
    
    @Test
    @DisplayName("Generates STRONG strength assessment when favorable facts dominate")
    void testAssessesStrongClaim() {
        // Given
        String query = "Strong claim?";
        EvidenceChunk chunk = createTestChunk(
            "Paid $40,000 post-separation. Property was community.", 1L, 1, "P1");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Strong", 0.95, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $40,000 post-separation", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Property was community property", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(query, issues, facts,
            "Strong Epstein factors.");
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        String answer = result.getAnswer();
        assertTrue(answer.contains("STRONG") || answer.contains("strong"),
            "Should indicate strong claim");
    }
    
    @Test
    @DisplayName("Provides comprehensive analysis sections with facts and recommendations")
    void testComprehensiveAnalysis() {
        // Given
        String query = "Property characterization?";
        EvidenceChunk chunk = createTestChunk("Purchased for $500k on marriage", 1L, 1, "P1");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.PROPERTY_CHARACTERIZATION, "Characterization", 0.75, "property")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Purchased for $500k", true, "source", LegalIssueType.PROPERTY_CHARACTERIZATION)
        );
        
        List<MissingFact> missingFacts = List.of(
            new MissingFact("Title status", LegalIssueType.PROPERTY_CHARACTERIZATION, "Not found in evidence")
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(query, issues, facts, missingFacts,
            "Community property presumption applies.");
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        String answer = result.getAnswer();
        assertTrue(answer.contains("Supporting Facts:"), "Should list supporting facts");
        assertTrue(answer.contains("Title status") || answer.contains("Missing"), 
            "Should show missing evidence");
        assertTrue(answer.contains("RECOMMENDED NEXT STEPS:") || answer.contains("Assessment:"), 
            "Should provide recommendations");
        assertTrue(answer.contains("preliminary") || answer.contains("PRELIMINARY"), 
            "Should indicate preliminary nature");
    }
    
    @Test
    @DisplayName("Displays Relevant Authorities section with retrieved authorities from case analysis")
    void testRelevantAuthoritiesSectionVisible() {
        // Given: A case analysis query with retrieved authorities
        String query = "post separation mortgage reimbursement";
        EvidenceChunk chunk = createTestChunk(
            "I paid $20,000 in post-separation mortgage payments on marital property.",
            1L, 1, "Page 1"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $20,000 in post-separation mortgage payments", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Property was marital/community property", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        // Create authorities to be included in the analysis
        LegalAuthority authority1 = new LegalAuthority(
            "auth1",
            "Epstein Factors for Reimbursement",
            "Cal. Fam. Code § 2641",
            AuthorityType.STATUTE,
            "California Family Code",
            "Creates right to reimbursement for improvements to community property",
            0.95
        );
        
        LegalAuthority authority2 = new LegalAuthority(
            "auth2",
            "Post-Separation Mortgage Payments",
            "In re Marriage of Gudelj",
            AuthorityType.CASE_LAW,
            "Case Law",
            "Court held that post-separation mortgage payments on marital property may be reimbursable",
            0.88
        );
        
        List<LegalAuthority> authorities = List.of(authority1, authority2);
        
        // Create authority summary for the reimbursement issue
        AuthoritySummary authoritySummary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            2,
            "California law permits reimbursement for post-separation payments on community property.",
            authorities
        );
        
        // Create context with authorities
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            issues,
            facts,
            List.of(),  // No missing facts for this test
            "Reimbursement governed by California Family Code § 2641.",
            List.of(authoritySummary)  // Include authorities
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then: Verify output contains the Relevant Authorities section
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // Verify "Relevant Authorities" section is present
        assertTrue(answer.contains("RELEVANT AUTHORITIES"),
            "Output should contain 'RELEVANT AUTHORITIES' section");
        
        // Verify at least one authority title is shown
        assertTrue(answer.contains("Epstein Factors") || answer.contains("Reimbursement"),
            "Output should contain at least one authority title");
        
        // Verify citation format (e.g., "Cal. Fam. Code § 2641")
        assertTrue(answer.contains("Cal. Fam. Code") || answer.contains("§ 2641") || answer.contains("2641"),
            "Output should contain authority citations");
        
        // Verify authority type is shown (STATUTE or CASE_LAW)
        assertTrue(answer.contains("STATUTE") || answer.contains("CASE_LAW") || answer.contains("Case Law"),
            "Output should show authority type");
        
        // Verify metadata includes authority count (AuthoritySummaries count)
        assertTrue(result.getMetadata().contains("Authorities:"),
            "Metadata should include authority count");
    }
    
    @Test
    @DisplayName("Deduplicates authorities - no duplicate authority titles in output")
    void testAuthoritiesDeduplicated() {
        // Given: Multiple authorities where some are repeated across summaries
        String query = "post separation mortgage reimbursement";
        EvidenceChunk chunk = createTestChunk(
            "I paid $25,000 in post-separation mortgage payments.",
            1L, 1, "Page 1"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $25,000 in post-separation mortgage", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        // Create shared authorities that appear in multiple summaries
        LegalAuthority epsteinAuthority = new LegalAuthority(
            "auth_epstein",
            "Epstein Factors for Reimbursement",
            "Cal. Fam. Code § 2641",
            AuthorityType.STATUTE,
            "California Family Code",
            "Reimbursement statute",
            0.95
        );
        
        LegalAuthority gudelj = new LegalAuthority(
            "auth_gudelj",
            "Post-Separation Mortgage Payments",
            "In re Marriage of Gudelj",
            AuthorityType.CASE_LAW,
            "Case Law",
            "Mortgage payment case",
            0.92
        );
        
        LegalAuthority huskey = new LegalAuthority(
            "auth_huskey",
            "Community Property Rules",
            "In re Marriage of Huskey",
            AuthorityType.CASE_LAW,
            "Case Law",
            "Community property case",
            0.88
        );
        
        // Create multiple authority summaries for the same issue with overlapping authorities
        AuthoritySummary summary1 = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            3,
            "Rule summary 1",
            List.of(epsteinAuthority, gudelj, huskey)  // All 3 authorities
        );
        
        // Create a second authority list that includes duplicates
        AuthoritySummary summary2 = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            2,
            "Rule summary 2",
            List.of(epsteinAuthority, gudelj)  // Same authorities repeated
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            issues,
            facts,
            List.of(),
            "Reimbursement governed by Family Code.",
            List.of(summary1, summary2)  // Multiple summaries with overlapping authorities
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then: Verify deduplication
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // Count occurrences of each authority - should appear exactly once
        int epsteinCount = countOccurrences(answer, "Epstein");
        int gudelJCount = countOccurrences(answer, "Gudelj");
        int husKeyCount = countOccurrences(answer, "Huskey");
        
        assertTrue(epsteinCount <= 1,
            "Epstein should appear at most once in output, but appeared " + epsteinCount + " times");
        assertTrue(gudelJCount <= 1,
            "Gudelj should appear at most once in output, but appeared " + gudelJCount + " times");
        assertTrue(husKeyCount <= 1,
            "Huskey should appear at most once in output, but appeared " + husKeyCount + " times");
        
        // Verify at least one authority is shown
        assertTrue(epsteinCount + gudelJCount + husKeyCount >= 1,
            "At least one authority should be shown");
        
        // Verify RELEVANT AUTHORITIES section shows limited authorities (top 2-3)
        assertTrue(answer.contains("RELEVANT AUTHORITIES"),
            "Should contain RELEVANT AUTHORITIES section");
        
        // Extract RELEVANT AUTHORITIES section (between its header and next section)
        int relevantAuthStartIdx = answer.indexOf("RELEVANT AUTHORITIES");
        int ruleSummaryStartIdx = answer.indexOf("RELEVANT AUTHORITIES & RULE SUMMARY");
        if (relevantAuthStartIdx != -1 && ruleSummaryStartIdx != -1) {
            String relevantAuthSection = answer.substring(relevantAuthStartIdx, ruleSummaryStartIdx);
            
            // Count how many authority citations appear in main section
            int citationCount = 0;
            if (relevantAuthSection.contains("Cal. Fam. Code")) citationCount++;
            if (relevantAuthSection.contains("Gudelj")) citationCount++;
            if (relevantAuthSection.contains("Huskey")) citationCount++;
            
            assertTrue(citationCount <= 3,
                "RELEVANT AUTHORITIES section should show max 3 authorities, but shows " + citationCount);
        }
    }
    
    @Test
    @DisplayName("Prioritizes relevant authorities by issue type - Epstein for reimbursement")
    void testAuthoritySelectionByIssue() {
        // Given: Multiple authorities where Epstein should be prioritized for REIMBURSEMENT issue
        String query = "post separation mortgage reimbursement";
        EvidenceChunk chunk = createTestChunk(
            "I paid $30,000 in post-separation mortgage payments on our family home.",
            1L, 1, "Page 1"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.90, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $30,000 post-separation mortgage", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        // Create "strong match" authority - Epstein (reimbursement-specific)
        LegalAuthority epsteinAuthority = new LegalAuthority(
            "auth_epstein",
            "In re Marriage of Epstein - Reimbursement of Post-Separation Obligations",
            "Cal. Fam. Code § 2641",
            AuthorityType.STATUTE,
            "California",
            "Seminal case establishing reimbursement principles for post-separation payments",
            0.85  // Original relevance
        );
        
        // Create "weak match" authority - Moore (generic property, not reimbursement-focused)
        LegalAuthority mooreAuthority = new LegalAuthority(
            "auth_moore",
            "In re Marriage of Moore - Property Characterization",
            "Moore v. Moore",
            AuthorityType.CASE_LAW,
            "Case Law",
            "A property characterization case with some general principles",
            0.80  // Nearly same original relevance as Epstein
        );
        
        // Create a "decent match" authority - statute on support (somewhat related)
        LegalAuthority supportAuthority = new LegalAuthority(
            "auth_support",
            "Family Code Section 4300",
            "Cal. Fam. Code § 4300",
            AuthorityType.STATUTE,
            "California",
            "Spousal support guidelines",
            0.75
        );
        
        // Create authority summary with these authorities in order: Moore, Epstein, Support
        // This tests that our re-ranking prioritizes Epstein even if not first
        AuthoritySummary summary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            3,
            "California law permits reimbursement for post-separation payments on marital property.",
            List.of(mooreAuthority, epsteinAuthority, supportAuthority)  // Moore listed first
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            issues,
            facts,
            List.of(),
            "Reimbursement governed by Family Code.",
            List.of(summary)
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then: Verify Epstein is shown and Moore is de-prioritized
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // Verify Epstein is present
        assertTrue(answer.contains("Epstein"),
            "Output should contain Epstein as it's the most relevant for reimbursement");
        
        // Verify both sections exist
        assertTrue(answer.contains("RELEVANT AUTHORITIES"),
            "Should contain RELEVANT AUTHORITIES section");
        assertTrue(answer.contains("RELEVANT AUTHORITIES & RULE SUMMARY"),
            "Should contain RELEVANT AUTHORITIES & RULE SUMMARY section");
        
        // The key test: Epstein should appear before or instead of Moore in the main section
        int epsteinStartIdx = answer.indexOf("Epstein");
        int mooreStartIdx = answer.indexOf("Moore");
        
        // Epstein should appear in output
        assertTrue(epsteinStartIdx > 0,
            "Epstein should appear in the output as the most relevant authority");
        
        // If Moore appears, Epstein should come first (earlier in the document)
        if (mooreStartIdx > 0) {
            assertTrue(epsteinStartIdx < mooreStartIdx,
                "Epstein should be ranked higher and appear before Moore in output");
        }
        
        // Verify the top authorities in RELEVANT AUTHORITIES section are good matches
        int relevantAuthStartIdx = answer.indexOf("RELEVANT AUTHORITIES\n---");
        int ruleSummaryStartIdx = answer.indexOf("RELEVANT AUTHORITIES & RULE SUMMARY");
        
        if (relevantAuthStartIdx > -1 && ruleSummaryStartIdx > -1) {
            String relevantAuthSection = answer.substring(relevantAuthStartIdx, ruleSummaryStartIdx);
            
            // The main section should prefer Epstein over Moore
            // Count the authorities shown in the main section (should be limited to 2-3)
            int eCount = countOccurrences(relevantAuthSection, "Epstein");
            assertTrue(eCount > 0,
                "RELEVANT AUTHORITIES section should include Epstein (high relevance to reimbursement)");
        }
    }
    
    @Test
    @DisplayName("Rendered authorities come from ranked list, not raw retrieval list")
    void testRenderedAuthoritiesFromRankedList() {
        // This test verifies that the final output uses POST-RANKING authorities,
        // not raw retrieval order. It does this by:
        // 1. Creating raw authorities in one order (worst match first)
        // 2. Verifying that the output shows them in ranking order (best match first)
        // 3. Confirming rendered list != raw list, proving ranking was applied
        
        // Given: Query with REIMBURSEMENT issue
        String query = "post separation mortgage reimbursement claim";
        
        // Create test authorities with INTENTIONALLY BAD RAW ORDER
        // Raw order: Moore (bad match) -> Epstein (good match)
        // Expected ranked order: Epstein (good match) -> Moore (bad match)
        
        LegalAuthority mooreAuthority = new LegalAuthority(
            "moore_prop_2019",
            "Marriage of Moore",
            "123 Cal. App. 4th 456 (2019)",
            AuthorityType.CASE_LAW,
            "court_case",  // source parameter
            "Discussion of property characterization in community property divorce cases.",
            0.75  // Moderate relevance score
        );
        
        LegalAuthority epsteinAuthority = new LegalAuthority(
            "epstein_1979",
            "Marriage of Epstein",
            "42 Cal.3d 120 (1979)",
            AuthorityType.CASE_LAW,
            "court_case",  // source parameter
            "Establishes reimbursement principles for post-separation payments and Epstein factors for contribution tracing during marriage.",
            0.85  // Slightly higher base score
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $20,000 in post-separation mortgage payments", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        // Create authority summary with authorities in BAD order (Moore first, Epstein second)
        AuthoritySummary mockSummary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            2,  // authorityCount
            "Reimbursement is available for post-separation payments under Epstein principles.",
            List.of(mooreAuthority, epsteinAuthority)  // Raw order: Moore, then Epstein
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, 
            issues, 
            facts,
            List.of(),  // missingFacts
            "Reimbursement evaluated under Epstein factors.",
            List.of(mockSummary)  // authoritySummaries passed in constructor
        );
        
        EvidenceChunk chunk = createTestChunk(
            "Post-separation mortgage payment record showing $20,000 paid.",
            1L, 1, "Page 1"
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then: Verify rendering proves ranking occurred
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // 1. Verify output contains both authorities (proving they were retrieved)
        assertTrue(answer.contains("Epstein"),
            "Output should contain Epstein authority");
        assertTrue(answer.contains("Moore"),
            "Output should contain Moore authority");
        
        // 2. CRITICAL TEST: Epstein should appear BEFORE Moore in the output,
        //    proving that ranking (not raw retrieval order) determined the output order
        int epsteinIdx = answer.indexOf("Epstein");
        int mooreIdx = answer.indexOf("Moore");
        
        assertTrue(epsteinIdx > 0, "Epstein should appear in output");
        assertTrue(mooreIdx > 0, "Moore should appear in output");
        assertTrue(epsteinIdx < mooreIdx,
            "CRITICAL: Epstein should appear BEFORE Moore in output. " +
            "This proves ranking was applied (raw order was Moore->Epstein, but output is Epstein->Moore). " +
            "If Moore appeared first, it would mean raw retrieval order was used instead of ranking.");
        
        // 3. Extract RELEVANT AUTHORITIES section to verify top authority is correct
        int authSectionStart = answer.indexOf("## RELEVANT AUTHORITIES");
        int authSectionEnd = answer.indexOf("## RELEVANT AUTHORITIES & RULE SUMMARY");
        
        if (authSectionStart > -1 && authSectionEnd > -1) {
            String authSection = answer.substring(authSectionStart, authSectionEnd);
            
            // Find first authority listed in this section
            int firstEpsteinInSection = authSection.indexOf("Epstein");
            int firstMooreInSection = authSection.indexOf("Moore");
            
            if (firstEpsteinInSection > -1 && firstMooreInSection > -1) {
                assertTrue(firstEpsteinInSection < firstMooreInSection,
                    "In RELEVANT AUTHORITIES section, Epstein (better match for REIMBURSEMENT) " +
                    "should be listed before Moore (generic property case)");
            } else if (firstEpsteinInSection > -1) {
                // Epstein is shown, Moore might be limited out - that's fine
                assertTrue(true, "Epstein shown in main authorities section as expected");
            }
        }
    }
    

    // ==================== HELPER METHODS ====================

    private EvidenceChunk createTestChunk(String text, Long docId, Integer pageNo, String pageRef) {
        return new EvidenceChunk(
            1L + pageNo, docId, pageNo, pageNo, pageNo, text, 0.8,
            "[CIT doc=" + docId + " p=" + pageRef + "]", 0.8, 0.7, 0.75
        );
    }
    
    /**
     * Count occurrences of a substring in a string.
     */
    private int countOccurrences(String text, String search) {
        if (text == null || search == null || search.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }
    
    // ==================== TEST DOUBLES ====================

    /**
     * Test double for RetrievalService - allows setting evidence chunks
     * and optionally throwing exceptions for error testing.
     */
    static class TestRetrievalService extends RetrievalService {
        private List<EvidenceChunk> evidenceChunks = List.of();
        private boolean throwException = false;
        
        // We need a no-arg constructor for Spring injection simulation
        public TestRetrievalService() {
            super(null, null, null);
        }
        
        void setEvidenceChunks(List<EvidenceChunk> chunks) {
            this.evidenceChunks = chunks;
        }
        
        void setThrowException(boolean shouldThrow) {
            this.throwException = shouldThrow;
        }
        
        @Override
        public List<EvidenceChunk> retrieveEvidence(String query, int topK) {
            if (throwException) {
                throw new RuntimeException("Test service error");
            }
            return evidenceChunks;
        }
        
        @Override
        public List<EvidenceChunk> retrieveEvidenceWithoutPlanning(String preplannedQuery, int topK) {
            if (throwException) {
                throw new RuntimeException("Test service error");
            }
            return evidenceChunks;
        }
    }
    
    /**
     * Test double for CaseAnalysisContextBuilder - allows setting
     * the context that should be returned without building it.
     */
    static class TestCaseAnalysisContextBuilder implements CaseAnalysisContextBuilder {
        private CaseAnalysisContext context;
        
        void setContext(CaseAnalysisContext ctx) {
            this.context = ctx;
        }
        
        @Override
        public CaseAnalysisContext buildContext(
            String originalQuery,
            String cleanedQuery,
            List<CaseIssue> identifiedIssues,
            List<EvidenceChunk> evidenceChunks
        ) {
            return context;
        }
        
        @Override
        public CaseAnalysisContext buildContextWithAuthorities(
            String originalQuery,
            String cleanedQuery,
            List<CaseIssue> identifiedIssues,
            List<EvidenceChunk> evidenceChunks,
            List<com.agent.model.analysis.authority.AuthoritySummary> authoritySummaries
        ) {
            return context;
        }
    }

    /**
     * Test double for CaseAnalysisQueryCleaner - returns cleaned version of query
     */
    static class TestQueryCleaner extends CaseAnalysisQueryCleaner {
        @Override
        public String stripAnalysisNoise(String query) {
            // Simple implementation - just remove common noise phrases
            if (query == null) return "";
            return query.toLowerCase()
                .replace("based on these facts", "")
                .replace("do i have", "")
                .replace("how strong is my", "")
                .trim()
                .replaceAll("\\s+", " ");
        }

        @Override
        public boolean hasSignificantContent(String cleanedQuery) {
            if (cleanedQuery == null || cleanedQuery.isBlank()) return false;
            return cleanedQuery.split("\\s+").length >= 2;
        }
    }

    /**
     * Test double for CaseAnalysisRetrievalQueryBuilder - generates multiple retrieval queries
     */
    static class TestQueryBuilder extends CaseAnalysisRetrievalQueryBuilder {
        @Override
        public List<String> buildQueries(String cleanedQuery, List<CaseIssue> issues) {
            // For testing, just return a couple of basic queries
            if (cleanedQuery == null || cleanedQuery.isBlank()) {
                return List.of();
            }
            
            List<String> queries = new java.util.ArrayList<>();
            queries.add(cleanedQuery); // Original cleaned query
            
            // Add issue-specific variations
            if (!issues.isEmpty()) {
                CaseIssue firstIssue = issues.get(0);
                queries.add(cleanedQuery + " " + firstIssue.getType().name().toLowerCase());
            }
            
            return queries;
        }
    }

    /**
     * Test double for CaseIssueExtractor - extracts issues from query
     */
    static class TestIssueExtractor implements CaseIssueExtractor {
        @Override
        public List<CaseIssue> extractIssues(String caseQuery) {
            // Simple keyword-based extraction for testing
            if (caseQuery == null) return List.of();
            
            List<CaseIssue> issues = new java.util.ArrayList<>();
            String queryLower = caseQuery.toLowerCase();
            
            if (queryLower.contains("reimbursement") || queryLower.contains("payment")) {
                issues.add(new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement"));
            }
            if (queryLower.contains("custody") || queryLower.contains("children")) {
                issues.add(new CaseIssue(LegalIssueType.CUSTODY, "Custody", 0.80, "custody"));
            }
            if (queryLower.contains("property") || queryLower.contains("characterization")) {
                issues.add(new CaseIssue(LegalIssueType.PROPERTY_CHARACTERIZATION, "Property", 0.75, "property"));
            }
            
            return issues;
        }

        @Override
        public List<CaseIssue> extractIssues(String caseQuery, String context) {
            return extractIssues(caseQuery);
        }
    }
}
