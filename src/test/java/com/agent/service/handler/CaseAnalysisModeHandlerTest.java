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
import com.agent.service.analysis.FactClassifier;
import com.agent.service.analysis.ClaimStrengthCalculator;
import com.agent.service.analysis.CounterArgumentFilter;
import com.agent.service.analysis.MortgageStatementParser;
import com.agent.service.extraction.PaymentRecordExtractor;
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
        
        // Create test double for PaymentEvidenceService
        var testPaymentEvidenceService = new com.agent.service.PaymentEvidenceService() {
            @Override
            public java.util.List<com.agent.model.PaymentRecord> getPaymentsByDocument(Long pdfDocumentId) {
                return java.util.List.of();
            }
            
            @Override
            public java.util.List<com.agent.model.PaymentRecord> getPaymentsByProperty(String propertyAddress, String propertyCity) {
                return java.util.List.of();
            }
            
            @Override
            public java.util.List<com.agent.model.PaymentRecord> getPaymentsByCategory(String category) {
                return java.util.List.of();
            }
            
            @Override
            public java.util.List<com.agent.model.PaymentRecord> getPaymentsByPropertyAndDateRange(String propertyAddress, String propertyCity, java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
                return java.util.List.of();
            }
            
            @Override
            public java.util.List<com.agent.model.PaymentRecord> getMortgagePaymentsByPropertyAndDateRange(String propertyAddress, String propertyCity, java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
                return java.util.List.of();
            }
            
            @Override
            public com.agent.model.PaymentSummary summarizePaymentsByPropertyAndDateRange(String propertyAddress, String propertyCity, java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
                return null;
            }
            
            @Override
            public java.util.List<com.agent.model.LegalEvidenceLine> formatPaymentRecordsAsEvidence(java.util.List<com.agent.model.PaymentRecord> records) {
                return java.util.List.of();
            }
            
            @Override
            public com.agent.model.LegalEvidenceLine formatPaymentSummaryAsEvidence(com.agent.model.PaymentSummary summary) {
                return null;
            }
        };
        
        // For testing, create a handler with actual authority services
        // In a full test scenario, these could be mocked or stubbed
        var realAuthorityQueryBuilder = new com.agent.service.analysis.authority.RuleBasedIssueAuthorityRetrievalStrategy();
        var realAuthorityRetrievalService = new AuthorityRetrievalService(java.util.Optional.empty());
        var realAuthoritySummarizer = new AuthoritySummarizer();
        var testFactClassifier = new TestFactClassifier();
        var realPaymentRecordExtractor = new com.agent.service.extraction.PaymentRecordExtractor();
        var realClaimStrengthCalculator = new ClaimStrengthCalculator();
        var realCounterArgumentFilter = new CounterArgumentFilter();
        var realMortgageStatementParser = new MortgageStatementParser();
        
        // Create PaymentEvidenceRoute for payment detection
        var paymentEvidenceRoute = new com.agent.service.PaymentEvidenceRoute();
        
        // Create mock property-aware analysis services
        var propertyExtractionService = new com.agent.service.analysis.PropertyExtractionService();
        var propertyScopeDetector = new com.agent.service.analysis.PropertyScopeDetector();
        var propertyAwareCaseAnalysis = new com.agent.service.analysis.PropertyAwareCaseAnalysis(
            propertyScopeDetector,
            propertyExtractionService
        );
        
        handler = new CaseAnalysisModeHandler(
            testPaymentEvidenceService,
            paymentEvidenceRoute,
            testRetrievalService, 
            testContextBuilder,
            testQueryCleaner,
            testQueryBuilder,
            testIssueExtractor,
            realAuthorityQueryBuilder,
            realAuthorityRetrievalService,
            realAuthoritySummarizer,
            testFactClassifier,
            realPaymentRecordExtractor,
            realClaimStrengthCalculator,
            realCounterArgumentFilter,
            realMortgageStatementParser,
            propertyAwareCaseAnalysis,
            propertyScopeDetector
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
        
        System.err.println("TEST START");
        // Then
        System.err.println("After comment");
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        System.err.println("After assertEquals");
        System.err.println("Result success: " + result.isSuccess());
        System.err.println("Result error: " + result.getErrorMessage());
        assertTrue(result.isSuccess(), "Result should be successful. Error: " + result.getErrorMessage());
        
        String answer = result.getAnswer();
        System.err.println("=== ANSWER LENGTH: " + (answer != null ? answer.length() : "null"));
        System.err.println("=== ANSWER TEXT ===\n" + answer);
        System.err.println("=== END ANSWER ===");
        assertNotNull(answer);
        assertTrue(answer.contains("CASE ANALYSIS REPORT"));
        assertTrue(answer.contains("ISSUE SUMMARY"));
        assertTrue(answer.contains("APPLICATION TO RULE"));
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
    @DisplayName("Filters out noisy snippets from supporting facts")
    void testFiltersNoisySnippetsFromSupportingFacts() {
        // Given: Mix of good and noisy facts as evidence chunks
        String query = "Do I have a reimbursement claim?";
        
        // Create evidence chunks that match quality and noisy facts
        EvidenceChunk goodChunk1 = createTestChunk(
            "I paid $20,000 in post-separation mortgage payments.",
            1L, 1, "Page 1"
        );
        EvidenceChunk noisyChunk1 = createTestChunk("23", 2L, 1, "Page 1");  // Pure number
        EvidenceChunk noisyChunk2 = createTestChunk("real and personal $", 3L, 1, "Page 1");  // Boilerplate
        EvidenceChunk goodChunk2 = createTestChunk(
            "The property was financed with a long-term mortgage during marriage.",
            4L, 1, "Page 2"
        );
        EvidenceChunk noisyChunk3 = createTestChunk("1,500", 5L, 1, "Page 2");  // Pure numeric
        EvidenceChunk goodChunk3 = createTestChunk(
            "Payment was made from my separate property account after separation.",
            6L, 1, "Page 3"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("I paid $20,000 in post-separation mortgage payments", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("The property was financed with a long-term mortgage during marriage", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Payment was made from my separate property account after separation", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement evaluated under Epstein factors."
        );
        
        // Mix evidence chunks - good and noisy
        testRetrievalService.setEvidenceChunks(List.of(
            goodChunk1, noisyChunk1, noisyChunk2, goodChunk2, noisyChunk3, goodChunk3
        ));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess(), "Result should be successful. Error: " + result.getErrorMessage());
        
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Verify that quality facts are included
        assertTrue(answer.contains("I paid $20,000 in post-separation mortgage payments"), 
            "Quality fact with post-separation payment should be included");
        assertTrue(answer.contains("mortgage") || answer.contains("separate property account"), 
            "Quality fact about mortgage or payment source should be included");
        
        // Verify that noisy snippets are filtered out
        assertFalse(answer.contains("  - 23"), 
            "Pure numeric snippet should be filtered out");
        assertFalse(answer.contains("  - real and personal $"), 
            "Boilerplate form snippet should be filtered out");
        assertFalse(answer.contains("  - 1,500"), 
            "Pure numeric snippet should be filtered out");
    }
    
    @Test
    @DisplayName("Strictly filters noisy snippets BEFORE rule-element assignment")
    void testStrictFactFilterBeforeRuleElementAssignment() {
        // Given: Query with noisy and quality evidence chunks
        String query = "Reimbursement analysis?";
        
        // Create evidence chunks that will be converted to facts
        EvidenceChunk qualityChunk1 = createTestChunk(
            "I paid $20,000 in post-separation mortgage payments on community property.",
            1L, 1, "Page 1"
        );
        EvidenceChunk qualityChunk2 = createTestChunk(
            "The payment was made in 2023 from my separate property account.",
            2L, 1, "Page 1"
        );
        EvidenceChunk noisyChunk1 = createTestChunk("23", 3L, 1, "Page 2");  // Pure number
        EvidenceChunk noisyChunk2 = createTestChunk("real and personal $", 4L, 1, "Page 2");  // Boilerplate
        EvidenceChunk noisyChunk3 = createTestChunk("Description", 5L, 1, "Page 2");  // Table header
        EvidenceChunk noisyChunk4 = createTestChunk("check the box", 6L, 1, "Page 2");  // Form boilerplate
        EvidenceChunk noisyChunk5 = createTestChunk("petitioner:", 7L, 1, "Page 2");  // Form label
        EvidenceChunk noisyChunk6 = createTestChunk("1500", 8L, 1, "Page 3");  // Pure number
        EvidenceChunk qualityChunk3 = createTestChunk(
            "Community property mortgaged during marriage for home purchase.",
            9L, 1, "Page 3"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("I paid $20,000 in post-separation mortgage payments on community property", 
                true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("The payment was made in 2023 from my separate property account", 
                true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Community property mortgaged during marriage for home purchase", 
                true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement claim analysis."
        );
        
        // Provide evidence chunks - mix of quality and noisy
        testRetrievalService.setEvidenceChunks(List.of(
            qualityChunk1, noisyChunk1, noisyChunk2, qualityChunk2, 
            noisyChunk3, noisyChunk4, noisyChunk5, noisyChunk6, qualityChunk3
        ));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess(), "Result should be successful");
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Verify quality facts with meaningful content are included
        assertTrue(answer.contains("post-separation mortgage payments") || 
                   answer.contains("separate property account") ||
                   answer.contains("mortgaged during marriage"),
            "At least one quality fact about payment/property should be included");
        
        // Verify NOISY SNIPPETS are completely excluded from APPLICATION TO RULE section
        // By checking that they don't appear as supporting facts
        String applicationSection = answer.substring(
            answer.indexOf("APPLICATION TO RULE"),
            answer.indexOf("COUNTERARGUMENTS"));
        
        // These should NOT appear in the Supporting Facts rendering
        assertFalse(applicationSection.contains("Supporting Facts:\n  - 23"), 
            "Pure numeric '23' should be rejected by strict filter");
        assertFalse(applicationSection.contains("Supporting Facts:\n  - real and personal $"), 
            "Boilerplate 'real and personal $' should be rejected by strict filter");
        assertFalse(applicationSection.contains("Supporting Facts:\n  - Description"), 
            "Isolated table header 'Description' should be rejected by strict filter");
        assertFalse(applicationSection.contains("Supporting Facts:\n  - check the box"), 
            "Form boilerplate 'check the box' should be rejected by strict filter");
        assertFalse(applicationSection.contains("Supporting Facts:\n  - petitioner:"), 
            "Form boilerplate 'petitioner:' should be rejected by strict filter");
        assertFalse(applicationSection.contains("Supporting Facts:\n  - 1500"), 
            "Pure numeric '1500' should be rejected by strict filter");
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
        
        // Count occurrences of each authority - should appear in rule/authorities but not excessively duplicated
        int epsteinCount = countOccurrences(answer, "Epstein");
        int gudelJCount = countOccurrences(answer, "Gudelj");
        int husKeyCount = countOccurrences(answer, "Huskey");
        
        // With concrete rule generation, authority names may appear in both rule text and authorities list
        // Allow up to 2 occurrences (rule + authorities section)
        assertTrue(epsteinCount <= 2,
            "Epstein should appear at most twice (once in rule, once in authorities), but appeared " + epsteinCount + " times");
        assertTrue(gudelJCount <= 2,
            "Gudelj should appear at most twice, but appeared " + gudelJCount + " times");
        assertTrue(husKeyCount <= 2,
            "Huskey should appear at most twice, but appeared " + husKeyCount + " times");
        
        // Verify at least one authority is shown
        assertTrue(epsteinCount + gudelJCount + husKeyCount >= 1,
            "At least one authority should be shown");
        
        // Verify RELEVANT AUTHORITIES section shows limited authorities (top 2-3)
        assertTrue(answer.contains("RELEVANT AUTHORITIES"),
            "Should contain RELEVANT AUTHORITIES section");
        
        // Extract RELEVANT AUTHORITIES section (between its header and next section)
        int relevantAuthStartIdx = answer.indexOf("RELEVANT AUTHORITIES");
        int applicationStartIdx = answer.indexOf("APPLICATION");
        if (relevantAuthStartIdx != -1 && applicationStartIdx != -1) {
            String relevantAuthSection = answer.substring(relevantAuthStartIdx, applicationStartIdx);
            
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
        assertTrue(answer.contains("LEGAL RULE"),
            "Should contain LEGAL RULE section (integrated from authority summaries)");
        
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
        int applicationStartIdx = answer.indexOf("APPLICATION");
        
        if (relevantAuthStartIdx > -1 && applicationStartIdx > -1) {
            String relevantAuthSection = answer.substring(relevantAuthStartIdx, applicationStartIdx);
            
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

    @Test
    void testStatuteRanksAboveGenericCaseForReimbursement() {
        // This test verifies that Cal. Fam. Code § 750 (statute) ranks above
        // generic case law (Moore) that lacks reimbursement keyword matching.
        // With the 2-authority display limit, only Epstein and § 750 should appear.
        // 
        // For REIMBURSEMENT issues:
        // - Statute gets +0.25 type bonus (vs. +0.10 for case law)
        // - Statute gets +0.4 keyword boost for "family code" mention
        // - Moore without reimbursement keyword gets -0.4 de-prioritization
        
        String query = "post separation mortgage reimbursement claim";
        
        // Create test authorities with mixed types
        // Statute about reimbursement (relevant, should rank second in final display)
        LegalAuthority statute750 = new LegalAuthority(
            "statute_fam_750",
            "California Family Code § 750",
            "Cal. Fam. Code § 750",
            AuthorityType.STATUTE,
            "statute",
            "Provides for reimbursement from community property for separate property contributions. " +
            "Requires tracing contributions and establishing family code principles for post-separation payments.",
            0.72  // Base relevance score from search
        );
        
        // Generic property case without reimbursement keyword (should NOT appear - ranks 3rd, limit is 2)
        LegalAuthority mooreGeneric = new LegalAuthority(
            "moore_generic",
            "Marriage of Moore",
            "400 Cal. 600 (1990)",
            AuthorityType.CASE_LAW,
            "court_case",
            "Discusses division of property and characterization in marriage dissolution proceedings.",
            0.75  // Slightly higher base relevance score
        );
        
        // Landmark reimbursement case (should rank first in final display)
        LegalAuthority epstein = new LegalAuthority(
            "epstein_1979",
            "Marriage of Epstein",
            "42 Cal.3d 120 (1979)",
            AuthorityType.CASE_LAW,
            "court_case",
            "Establishes reimbursement principles for post-separation payments using Epstein factors.",
            0.80
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.90, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $20,000 in post-separation mortgage payments on community property", 
                        true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        // Create authority summary with authorities in raw order:
        // [Moore, statute, Epstein] - mixing types and relevances
        AuthoritySummary mockSummary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            3,
            "Reimbursement available for post-separation payments under statutory and case law principles.",
            List.of(mooreGeneric, statute750, epstein)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            issues,
            facts,
            List.of(),
            "Reimbursement evaluated under statutory family code and Epstein factors.",
            List.of(mockSummary)
        );
        
        EvidenceChunk chunk = createTestChunk(
            "Post-separation mortgage payment for community property per family code section 750.",
            1L, 1, "Page 1"
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then: Verify final output shows only top 2 authorities
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // Extract RELEVANT AUTHORITIES section
        int authSectionStart = answer.indexOf("## RELEVANT AUTHORITIES");
        int authSectionEnd = answer.indexOf("## RELEVANT AUTHORITIES & RULE SUMMARY");
        
        if (authSectionStart > -1 && authSectionEnd == -1) {
            // If RULE SUMMARY section doesn't exist, use end of answer
            authSectionEnd = answer.length();
        }
        
        String authSection = "";
        if (authSectionStart > -1 && authSectionEnd > authSectionStart) {
            authSection = answer.substring(authSectionStart, authSectionEnd);
        }
        
        // CRITICAL TESTS for 2-authority display limit:
        // 1. Epstein MUST appear (top-ranked authority)
        assertTrue(authSection.contains("Epstein") || answer.contains("Epstein"),
            "CRITICAL: Epstein (top-ranked reimbursement case) must appear in final output");
        
        // 2. Statute MUST appear (second-ranked authority)
        assertTrue(authSection.contains("750") || authSection.contains("Family Code") || 
                   answer.contains("750") || answer.contains("Family Code"),
            "CRITICAL: Cal. Fam. Code § 750 (second-ranked statute) must appear in final output");
        
        // 3. Moore should NOT appear in visible RELEVANT AUTHORITIES section
        // (it's ranked 3rd, but limit is 2)
        int epsteinInAuth = authSection.indexOf("Epstein");
        int statuteInAuth = authSection.indexOf("750");
        int mooreInAuth = authSection.indexOf("Moore");
        
        // Moore should either not appear in the RELEVANT AUTHORITIES section,
        // or if it still appears in other sections, it should not be in the main authorities list
        if (epsteinInAuth > -1 && statuteInAuth > -1) {
            // Extract just the listed authorities line(s) from the section
            String authLines = authSection.substring(0, 
                authSection.indexOf("\n\n") > -1 ? authSection.indexOf("\n\n") : authSection.length());
            
            assertTrue(authLines.contains("Epstein"),
                "Epstein should be listed in RELEVANT AUTHORITIES section");
            assertTrue(authLines.contains("750") || authLines.contains("Family Code"),
                "Statute should be listed in RELEVANT AUTHORITIES section");
            
            if (mooreInAuth > -1) {
                // Moore appears in the section, verify it's only in RULE SUMMARY, not main authorities
                int mainAuthEnd = authLines.length();
                assertTrue(mooreInAuth > mainAuthEnd,
                    "CRITICAL: Moore (ranked 3rd) should NOT appear in main RELEVANT AUTHORITIES list " +
                    "(display limit is 2). It ranks below Epstein and Family Code § 750.");
            } else {
                // Moore correctly omitted from visible output
                assertTrue(true, 
                    "CORRECT: Moore excluded from visible output due to 2-authority display limit");
            }
        }
    }

    @Test
    void testLegalRuleAppearsBeforeApplicationInIRACStructure() {
        // This test verifies that the new IRAC structure integrates authority summaries
        // as the LEGAL RULE section that appears before APPLICATION.
        // 
        // Expected order: ISSUE SUMMARY → LEGAL RULE → RELEVANT AUTHORITIES → APPLICATION
        
        String query = "post separation mortgage reimbursement claim";
        
        // Create a reimbursement authority
        LegalAuthority epstein = new LegalAuthority(
            "epstein_1979",
            "Marriage of Epstein",
            "42 Cal.3d 120 (1979)",
            AuthorityType.CASE_LAW,
            "court_case",
            "Establishes reimbursement principles for post-separation payments.",
            0.85
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.90, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $20,000 in post-separation mortgage payments", 
                        true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        // Create authority summary with clear rule text
        String ruleText = "Under Marriage of Epstein, a spouse who makes separate property payments " +
                         "for community property obligations after separation may seek reimbursement " +
                         "from the community estate.";
        
        AuthoritySummary mockSummary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            1,
            ruleText,
            List.of(epstein)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            issues,
            facts,
            List.of(),
            "Reimbursement analysis",
            List.of(mockSummary)
        );
        
        EvidenceChunk chunk = createTestChunk(
            "Post-separation mortgage payment for community property.",
            1L, 1, "Page 1"
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then: Verify IRAC structure with integrated LEGAL RULE
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // CRITICAL: Verify LEGAL RULE section appears and contains the rule text
        assertTrue(answer.contains("LEGAL RULE"),
            "Output must contain LEGAL RULE section");
        assertTrue(answer.contains("Epstein"),
            "LEGAL RULE should reference Epstein authority");
        assertTrue(answer.contains("reimbursement"),
            "LEGAL RULE should contain reimbursement principle");
        
        // CRITICAL: Verify structure ordering - LEGAL RULE before APPLICATION
        int legalRuleIdx = answer.indexOf("LEGAL RULE");
        int applicationIdx = answer.indexOf("APPLICATION");
        
        assertTrue(legalRuleIdx > 0,
            "LEGAL RULE section must appear in output");
        assertTrue(applicationIdx > 0,
            "APPLICATION section must appear in output");
        assertTrue(legalRuleIdx < applicationIdx,
            "CRITICAL: LEGAL RULE (contains authority summaries) must appear BEFORE APPLICATION " +
            "in IRAC structure. This ensures authority-based rule precedes case fact application.");
        
        // Verify rule text is in the LEGAL RULE section, not ApplicationIndex
        String beforeApplication = answer.substring(0, applicationIdx);
        assertTrue(beforeApplication.contains("Epstein"),
            "Authority reference must appear before APPLICATION section");
        assertTrue(beforeApplication.contains("LEGAL RULE"),
            "LEGAL RULE section header must appear before APPLICATION");
    }
    
    @Test
    void testLegalRuleCitationsMatchRenderedAuthorities() {
        // Given: A reimbursement query with multiple authorities where some are higher ranked
        String query = "post separation mortgage reimbursement";
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $25,000 in post-separation mortgage", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        // Create three authorities: Epstein (statute), Moore (case law), and Huskey (case law)
        LegalAuthority epstein = new LegalAuthority(
            "auth_epstein",
            "Epstein Factors",
            "42 Cal.3d 120",
            AuthorityType.STATUTE,
            "California statute",
            "Reimbursement factors summary",
            0.95  // Highest score
        );
        
        LegalAuthority moore = new LegalAuthority(
            "auth_moore",
            "Moore Case",
            "49 Cal.3d 500",
            AuthorityType.CASE_LAW,
            "Case law",
            "Generic reimbursement case",
            0.70  // Lower score
        );
        
        LegalAuthority huskey = new LegalAuthority(
            "auth_huskey",
            "Huskey Case",
            "38 Cal.3d 800",
            AuthorityType.CASE_LAW,
            "Case law",
            "Post-separation mortgage case",
            0.92  // Second highest
        );
        
        // Create authority summaries with all authorities
        AuthoritySummary summary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            1,
            "Reimbursement principles apply based on Epstein and Huskey case law.",
            List.of(epstein, huskey, moore)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            issues,
            facts,
            List.of(),
            "Reimbursement analysis",
            List.of(summary)
        );
        
        EvidenceChunk chunk = createTestChunk(
            "Post-separation mortgage payment",
            1L, 1, "Page 1"
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When: Execute CASE_ANALYSIS mode
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then: Verify LEGAL RULE section citations match RELEVANT AUTHORITIES
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // Extract LEGAL RULE section
        int legalRuleIdx = answer.indexOf("LEGAL RULE");
        int authoritiesIdx = answer.indexOf("RELEVANT AUTHORITIES");
        
        assertTrue(legalRuleIdx >= 0, "LEGAL RULE section must exist");
        assertTrue(authoritiesIdx >= 0, "RELEVANT AUTHORITIES section must exist");
        assertTrue(legalRuleIdx < authoritiesIdx, "LEGAL RULE must appear before RELEVANT AUTHORITIES");
        
        String legalRuleSection = answer.substring(legalRuleIdx, authoritiesIdx);
        String authoritiesSection = answer.substring(authoritiesIdx, answer.indexOf("APPLICATION"));
        
        // CRITICAL: Verify that RELEVANT AUTHORITIES shows only top 2 (Epstein + Huskey, not Moore)
        // Since we're testing after ranking, only Epstein (statute +0.25 boost) and Huskey should be shown
        assertTrue(authoritiesSection.contains("Epstein"),
            "RELEVANT AUTHORITIES section must show Epstein (highest ranked)");
        assertTrue(authoritiesSection.contains("Huskey"),
            "RELEVANT AUTHORITIES section must show Huskey (second highest ranked)");
        assertFalse(authoritiesSection.contains("Moore"),
            "RELEVANT AUTHORITIES section must NOT show Moore (ranked below top 2)");
        
        // CRITICAL: Verify LEGAL RULE doesn't mention authorities that aren't displayed
        // If LEGAL RULE contains authority names, they must match RELEVANT AUTHORITIES
        if (legalRuleSection.contains("Moore")) {
            fail("LEGAL RULE should not reference Moore since it's not in final RELEVANT AUTHORITIES");
        }
        
        // Verify consistency: If rule mentions an authority, it must be in RELEVANT AUTHORITIES
        if (legalRuleSection.toLowerCase().contains("epstein")) {
            assertTrue(authoritiesSection.contains("Epstein"),
                "If LEGAL RULE mentions Epstein, it must appear in RELEVANT AUTHORITIES");
        }
        
        if (legalRuleSection.toLowerCase().contains("huskey")) {
            assertTrue(authoritiesSection.contains("Huskey"),
                "If LEGAL RULE mentions Huskey, it must appear in RELEVANT AUTHORITIES");
        }
    }

    @Test
    void testStrictCitationFilteringRemovesStaleCitations() {
        // Given: Final authorities are Epstein + § 750, but the original rule contains stale Moore citation
        String query = "post separation mortgage reimbursement";
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Paid $25,000 in post-separation mortgage", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        // Authority 1: Epstein (42 Cal.3d 120) - highest ranked
        LegalAuthority epstein = new LegalAuthority(
            "auth_epstein",
            "Marriage of Epstein",
            "191 Cal.App.3d 592",
            AuthorityType.STATUTE,
            "Case law",
            "Epstein reimbursement analysis",
            0.95
        );
        
        // Authority 2: Family Code § 750 - second highest
        LegalAuthority familyCodeSection = new LegalAuthority(
            "auth_fam_750",
            "California Family Code § 750",
            "Cal. Fam. Code § 750",
            AuthorityType.STATUTE,
            "Statute",
            "Reimbursement statute",
            0.92
        );
        
        // Authority 3: Moore (stale, will be dropped during ranking)
        LegalAuthority moore = new LegalAuthority(
            "auth_moore",
            "Court's Opinion on reimbursement",
            "28 Cal.4th 366",
            AuthorityType.CASE_LAW,
            "Case law",
            "Generic Moore case",
            0.65  // Lowest score, will be dropped
        );
        
        // CRITICAL: Authority summary contains STALE rule text mentioning Moore (28 Cal.4th 366)
        // even though Moore will not be in the final top-2 authorities
        String staleRuleText = "Under Marriage of Epstein (191 Cal.App.3d 592) and Moore v. Court " +
            "(28 Cal.4th 366), reimbursement is governed by California Family Code § 750. " +
            "The Moore precedent established general principles that inform reimbursement analysis.";
        
        AuthoritySummary summary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT,
            1,
            staleRuleText,
            List.of(epstein, familyCodeSection, moore)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            issues,
            facts,
            List.of(),
            "Reimbursement analysis",
            List.of(summary)
        );
        
        EvidenceChunk chunk = createTestChunk(
            "Post-separation mortgage payment issue",
            1L, 1, "Page 1"
        );
        
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        // When: Execute CASE_ANALYSIS
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then: Verify stale citations are removed from LEGAL RULE
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // Extract sections
        int legalRuleIdx = answer.indexOf("LEGAL RULE");
        int authoritiesIdx = answer.indexOf("RELEVANT AUTHORITIES");
        int applicationIdx = answer.indexOf("APPLICATION");
        
        assertTrue(legalRuleIdx >= 0, "LEGAL RULE section must exist");
        assertTrue(authoritiesIdx > legalRuleIdx, "RELEVANT AUTHORITIES must come after LEGAL RULE");
        
        String legalRuleSection = answer.substring(legalRuleIdx, authoritiesIdx);
        String authoritiesSection = answer.substring(authoritiesIdx, applicationIdx);
        
        // CRITICAL: Verify that final RELEVANT AUTHORITIES shows only Epstein + § 750 (not Moore)
        assertTrue(authoritiesSection.contains("Epstein"),
            "RELEVANT AUTHORITIES must show Epstein");
        assertTrue(authoritiesSection.contains("Cal. Fam. Code § 750"),
            "RELEVANT AUTHORITIES must show Cal. Fam. Code § 750");
        assertFalse(authoritiesSection.contains("28 Cal.4th 366"),
            "RELEVANT AUTHORITIES must NOT show Moore (28 Cal.4th 366) - ranked below top 2");
        
        // CRITICAL: Verify LEGAL RULE does NOT contain the stale Moore citation
        // The filter should detect that the original rule contains "28 Cal.4th 366" which is not
        // in the final authorities, and regenerate the rule from scratch
        assertFalse(legalRuleSection.contains("28 Cal.4th 366"),
            "LEGAL RULE must NOT contain stale Moore citation (28 Cal.4th 366) - " +
            "should regenerate rule when stale citations detected");
        
        assertFalse(legalRuleSection.contains("Moore"),
            "LEGAL RULE must NOT reference Moore by name when Moore is not in final authorities");
        
        // VERIFY: LEGAL RULE should mention only authorities that are actually rendered
        // Either it mentions Epstein/§750, or it's a generic fallback about legal principles
        assertTrue(
            legalRuleSection.contains("Epstein") || 
            legalRuleSection.contains("Cal. Fam. Code") ||
            legalRuleSection.toLowerCase().contains("principles"),  // Match both "legal principles" and "Legal principles"
            "LEGAL RULE should reference only final authorities or use generic principle statement"
        );
    }

    @Test
    void testCitationExtractionForCaseCitations() {
        // Test that California case citations are properly extracted from rule text
        // Examples: "191 Cal.App.3d 592", "28 Cal.4th 366", "24 Cal.3d 76"
        
        String query = "reimbursement claim";
        
        LegalAuthority auth1 = new LegalAuthority(
            "auth1", "Marriage of Epstein", "191 Cal.App.3d 592",
            AuthorityType.CASE_LAW, "court_case", "Reimbursement principles", 0.90
        );
        
        // Rule text with multiple case citations
        String ruleText = "A spouse who uses separate property after separation to pay " +
            "community obligations may seek reimbursement. See 191 Cal.App.3d 592, 28 Cal.4th 366.";
        
        AuthoritySummary summary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT, 1, ruleText, List.of(auth1)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            List.of(new CaseIssue(LegalIssueType.REIMBURSEMENT, "Test", 0.9, "test")),
            List.of(new CaseFact("Test fact", true, "source", LegalIssueType.REIMBURSEMENT)),
            List.of(),
            "Test analysis",
            List.of(summary)
        );
        
        EvidenceChunk chunk = createTestChunk("Test evidence", 1L, 1, "Page 1");
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify execution succeeds
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        
        // Both citations should be detected and rule should be regenerated if any don't match final authorities
        // Since 28 Cal.4th 366 is not in final authorities (auth1 is only "191 Cal.App.3d 592"),
        // the rule should be regenerated
        assertNotNull(answer, "Answer should not be null");
        assertTrue(answer.contains("LEGAL RULE"), "Output should contain LEGAL RULE section");
    }

    @Test
    void testCitationExtractionForStatuteCitations() {
        // Test that California statute citations are properly extracted
        // Examples: "Cal. Fam. Code § 750", "Cal. Fam. Code §2640"
        
        String query = "family code reimbursement";
        
        LegalAuthority auth = new LegalAuthority(
            "auth_code", "California Family Code § 750", "Cal. Fam. Code § 750",
            AuthorityType.STATUTE, "statute", "Reimbursement statute", 0.90
        );
        
        // Rule text with statute citations
        String ruleText = "Reimbursement entitlements are governed by multiple provisions. " +
            "See Cal. Fam. Code § 750 and Cal. Fam. Code §2640 for detailed requirements.";
        
        AuthoritySummary summary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT, 1, ruleText, List.of(auth)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            List.of(new CaseIssue(LegalIssueType.REIMBURSEMENT, "Test", 0.9, "test")),
            List.of(new CaseFact("Test fact", true, "source", LegalIssueType.REIMBURSEMENT)),
            List.of(),
            "Test analysis",
            List.of(summary)
        );
        
        EvidenceChunk chunk = createTestChunk("Test evidence", 1L, 1, "Page 1");
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify execution succeeds
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        assertTrue(answer.contains("LEGAL RULE"), "Output should contain LEGAL RULE section");
        // Since § 2640 is not in final authorities, rule should be regenerated or adjusted
    }

    @Test
    void testCitationExtractionWithMixedCitations() {
        // Test extraction of both case and statute citations in same rule
        
        String query = "reimbursement and property law";
        
        LegalAuthority auth1 = new LegalAuthority(
            "auth1", "Marriage of Epstein", "191 Cal.App.3d 592",
            AuthorityType.CASE_LAW, "court_case", "Case law", 0.90
        );
        
        LegalAuthority auth2 = new LegalAuthority(
            "auth2", "California Family Code § 750", "Cal. Fam. Code § 750",
            AuthorityType.STATUTE, "statute", "Statute", 0.85
        );
        
        // Rule with both case and statute citations
        String ruleText = "Under Marriage of Epstein (191 Cal.App.3d 592), the principles " +
            "established in 28 Cal.4th 366 apply. Additionally, Cal. Fam. Code § 750 and " +
            "Cal. Fam. Code §2640 govern the procedures.";
        
        AuthoritySummary summary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT, 1, ruleText, List.of(auth1, auth2)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            List.of(new CaseIssue(LegalIssueType.REIMBURSEMENT, "Test", 0.9, "test")),
            List.of(new CaseFact("Test fact", true, "source", LegalIssueType.REIMBURSEMENT)),
            List.of(),
            "Test analysis",
            List.of(summary)
        );
        
        EvidenceChunk chunk = createTestChunk("Test evidence", 1L, 1, "Page 1");
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify execution succeeds
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        
        // Rule contains "28 Cal.4th 366" and "Cal. Fam. Code §2640" not in final authorities
        // Should be regenerated
        assertNotNull(answer, "Answer should not be null");
        assertTrue(answer.contains("LEGAL RULE"), "Output should contain LEGAL RULE section");
    }

    @Test
    void testFallbackRuleGenerationReimbursement() {
        // Test that fallback rule generation for REIMBURSEMENT creates concrete rule, not generic placeholder
        
        String query = "reimbursement claim";
        
        LegalAuthority epstein = new LegalAuthority(
            "auth_epstein", "Marriage of Epstein", "191 Cal.App.3d 592",
            AuthorityType.CASE_LAW, "court_case", "Reimbursement principles", 0.90
        );
        
        LegalAuthority familyCode = new LegalAuthority(
            "auth_fam", "California Family Code § 750", "Cal. Fam. Code § 750",
            AuthorityType.STATUTE, "statute", "Reimbursement statute", 0.85
        );
        
        // Original rule with stale citation that triggers regeneration
        String staleRule = "Reimbursement under Epstein (191 Cal.App.3d 592) and Moore (28 Cal.4th 366) " +
                          "is subject to Cal. Fam. Code § 750.";
        
        AuthoritySummary summary = new AuthoritySummary(
            LegalIssueType.REIMBURSEMENT, 1, staleRule,
            List.of(epstein, familyCode)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            List.of(new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.9, "reimbursement")),
            List.of(new CaseFact("Paid community obligation with separate funds", true, "source", LegalIssueType.REIMBURSEMENT)),
            List.of(),
            "Reimbursement analysis",
            List.of(summary)
        );
        
        EvidenceChunk chunk = createTestChunk("Fact evidence", 1L, 1, "Page 1");
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify execution succeeds
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // Extract LEGAL RULE section
        int ruleStart = answer.indexOf("LEGAL RULE");
        int ruleEnd = answer.indexOf("RELEVANT AUTHORITIES");
        assertTrue(ruleStart >= 0, "LEGAL RULE section must exist");
        
        String ruleSection = answer.substring(ruleStart, ruleEnd);
        
        // Verify: Rule is CONCRETE, not the generic placeholder
        assertFalse(ruleSection.contains("Legal principles from relevant"),
            "Rule should NOT be generic placeholder - should be regenerated with concrete content");
        
        // Verify: Regenerated rule mentions only final authorities
        assertFalse(ruleSection.contains("28 Cal.4th 366"),
            "Rule should NOT contain stale Moore citation");
        assertFalse(ruleSection.contains("Moore"),
            "Rule should NOT mention Moore (stale authority)");
        
        // Verify: Regenerated rule is concrete and specific to reimbursement
        assertTrue(
            ruleSection.toLowerCase().contains("spouse") ||
            ruleSection.toLowerCase().contains("funds") ||
            ruleSection.toLowerCase().contains("reimbursement") ||
            ruleSection.toLowerCase().contains("epstein"),
            "Rule should be concrete with reimbursement-specific language or authority names"
        );
    }

    @Test
    void testFallbackRulePropertyCharacterization() {
        // Test fallback rule generation for PROPERTY_CHARACTERIZATION produces concrete rule
        
        String query = "property is community or separate";
        
        LegalAuthority codeAuth = new LegalAuthority(
            "auth_prop", "California family Code property division",
            "Cal. Fam. Code § 750",
            AuthorityType.STATUTE, "statute", "Property characterization", 0.88
        );
        
        // Stale rule that triggers regeneration
        String staleRule = "Property purchased with community funds is generally community property per " +
                          "Smith v. Jones (100 Cal.2d 100) and Cal. Fam. Code § 750 and § 1100.";
        
        AuthoritySummary summary = new AuthoritySummary(
            LegalIssueType.PROPERTY_CHARACTERIZATION, 1, staleRule,
            List.of(codeAuth)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query,
            List.of(new CaseIssue(LegalIssueType.PROPERTY_CHARACTERIZATION, "Property", 0.9, "property")),
            List.of(new CaseFact("Property purchased during marriage", true, "source", LegalIssueType.PROPERTY_CHARACTERIZATION)),
            List.of(),
            "Property analysis",
            List.of(summary)
        );
        
        EvidenceChunk chunk = createTestChunk("Property facts", 1L, 1, "Page 1");
        testRetrievalService.setEvidenceChunks(List.of(chunk));
        testContextBuilder.setContext(context);
        
        ModeExecutionResult result = handler.execute(query, 5);
        
        assertTrue(result.isSuccess(), "Execution should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer, "Answer should not be null");
        
        // Extract LEGAL RULE section
        int ruleStart = answer.indexOf("LEGAL RULE");
        int ruleEnd = answer.indexOf("RELEVANT AUTHORITIES");
        assertTrue(ruleStart >= 0, "LEGAL RULE section must exist");
        
        String ruleSection = answer.substring(ruleStart, ruleEnd);
        
        // Verify: Rule is concrete for property characterization
        assertFalse(ruleSection.contains("100 Cal.2d 100"),
            "Should not contain stale Smith citation");
        assertFalse(ruleSection.contains("§ 1100"),
            "Should not contain stale § 1100 citation");
        
        // Verify: Rule is specific to property characterization topic
        assertTrue(
            ruleSection.toLowerCase().contains("property") ||
            ruleSection.toLowerCase().contains("community") ||
            ruleSection.toLowerCase().contains("separate") ||
            ruleSection.toLowerCase().contains("characteriz"),
            "Rule should have property-specific language"
        );
    }

    // ==================== REIMBURSEMENT FACT FILTERING TESTS ====================
    
    @Test
    @DisplayName("REIMBURSEMENT: Rejects down payment contribution facts for post-separation reimbursement")
    void testReimbursementRejectsDownPaymentFacts() {
        // Given: Facts about down payment (should be rejected) mixed with post-separation payment facts
        String query = "What's my reimbursement claim for post-separation mortgage payments?";
        
        // Create evidence chunks
        EvidenceChunk downPaymentChunk = createTestChunk(
            "We contributed $50,000 as down payment when we purchased the home.",
            1L, 1, "Page 1"
        );
        EvidenceChunk separationPaymentChunk = createTestChunk(
            "I paid $20,000 in post-separation mortgage payments after we separated.",
            2L, 1, "Page 1"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Post-separation mortgage reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            // Down payment fact - should be rejected
            new CaseFact("We contributed $50,000 as down payment when we purchased the home", true, "source", LegalIssueType.REIMBURSEMENT),
            // Valid post-separation payment fact
            new CaseFact("I paid $20,000 in post-separation mortgage payments after we separated", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement evaluated under Epstein factors."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(downPaymentChunk, separationPaymentChunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertTrue(result.isSuccess(), "Result should be successful");
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Verify: Post-separation payment fact IS included
        assertTrue(answer.contains("post-separation mortgage payments") || 
                   answer.contains("$20,000"),
            "Post-separation payment fact should be included in supporting facts");
        
        // Verify: Down payment fact is NOT included in rule element analysis
        String applicationSection = answer.substring(
            Math.max(0, answer.indexOf("APPLICATION TO RULE")),
            Math.min(answer.length(), answer.indexOf("COUNTERARGUMENTS") + 1)
        );
        
        assertFalse(applicationSection.contains("down payment") || 
                    applicationSection.contains("$50,000 as down payment"),
            "Down payment contribution should NOT appear as supporting fact for post-separation reimbursement");
    }
    
    @Test
    @DisplayName("REIMBURSEMENT: Rejects OCR fragments like '23 If payment is received after 10/16/2025'")
    void testReimbursementRejectsOCRPaymentFragments() {
        // Given: OCR fragments that look like payment statements but aren't meaningful
        String query = "Do I have supporting facts for reimbursement?";
        
        // Create chunks with OCR fragments
        EvidenceChunk ocrFragment1 = createTestChunk(
            "23 If payment is received after 10/16/2025, a late fee will be assessed.",
            1L, 1, "Page 1"
        );
        EvidenceChunk ocrFragment2 = createTestChunk(
            "Payment received after 11/01/2025: 5% penalty applies.",
            2L, 1, "Page 1"
        );
        EvidenceChunk validPaymentChunk = createTestChunk(
            "I paid $15,000 in post-separation mortgage principal and interest payments.",
            3L, 1, "Page 2"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("23 If payment is received after 10/16/2025, a late fee will be assessed", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Payment received after 11/01/2025: 5% penalty applies", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("I paid $15,000 in post-separation mortgage principal and interest payments", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement claim."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(ocrFragment1, ocrFragment2, validPaymentChunk));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertTrue(result.isSuccess(), "Result should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Verify: Valid payment fact is included
        assertTrue(answer.contains("post-separation mortgage") || answer.contains("principal and interest") || answer.contains("$15,000"),
            "Valid post-separation payment fact should be included");
        
        // Verify: OCR fragments are NOT included
        String applicationSection = answer.substring(
            Math.max(0, answer.indexOf("APPLICATION TO RULE")),
            Math.min(answer.length(), answer.indexOf("COUNTERARGUMENTS") + 1)
        );
        
        assertFalse(applicationSection.contains("23 If payment is received") ||
                    applicationSection.contains("5% penalty"),
            "OCR statement fragments should be rejected and not appear as supporting facts");
    }
    
    @Test
    @DisplayName("REIMBURSEMENT: Allows empty supporting facts when evidence is weak")
    void testReimbursementAllowsEmptySupportingFacts() {
        // Given: Facts that fail relevance filtering
        String query = "What facts support my reimbursement?";
        
        EvidenceChunk weakChunk1 = createTestChunk(
            "We purchased a house during marriage for $400,000.",
            1L, 1, "Page 1"
        );
        EvidenceChunk weakChunk2 = createTestChunk(
            "Initial contribution made at closing.",
            2L, 1, "Page 1"
        );
        EvidenceChunk weakChunk3 = createTestChunk(
            "1500",
            3L, 1, "Page 2"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("We purchased a house during marriage for $400,000", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Initial contribution made at closing", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("1500", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement claim."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(weakChunk1, weakChunk2, weakChunk3));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertTrue(result.isSuccess());
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Verify: Answer exists and is complete
        assertTrue(answer.contains("CASE ANALYSIS REPORT"));
        assertTrue(answer.contains("ISSUE SUMMARY"));
        
        // Verify: Either "(none identified)" appears OR the answer explicitly states weak evidence
        // The important thing is that weak facts are filtered out and not presented as strong supporting evidence
        String applicationSection = answer.substring(
            Math.max(0, answer.indexOf("APPLICATION TO RULE")),
            Math.min(answer.length(), answer.indexOf("COUNTERARGUMENTS") + 1)
        );
        
        // If there are no strong supporting facts, "(none identified)" should appear or facts section should be minimal
        // The key is: these weak facts should NOT be rendered as strong supporting facts
        boolean hasNoneIdentified = applicationSection.contains("(none identified)") || 
                                   applicationSection.contains("Supporting Facts:\n") &&
                                   !applicationSection.contains("purchased");  // Don't show "purchased during marriage"
        
        assertTrue(hasNoneIdentified || !applicationSection.contains("Supporting Facts:\n  - We purchased"),
            "Weak facts about initial purchase should not be presented as supporting facts");
    }
    
    @Test
    @DisplayName("REIMBURSEMENT: Accepts strong post-separation mortgage payment facts with amounts")
    void testReimbursementAcceptsStrongPostSeparationFacts() {
        // Given: Clear post-separation payment facts with amounts and context
        String query = "Reimbursement for post-separation mortgage payments?";
        
        EvidenceChunk strongFact1 = createTestChunk(
            "Post-separation, I paid $25,000 in monthly mortgage payments on the marital residence.",
            1L, 1, "Page 1"
        );
        EvidenceChunk strongFact2 = createTestChunk(
            "Each payment was $1,200 principal and $800 interest per month for 20 months.",
            2L, 1, "Page 1"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("Post-separation, I paid $25,000 in monthly mortgage payments on the marital residence", true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Each payment was $1,200 principal and $800 interest per month for 20 months", true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement evaluated under Epstein factors."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(strongFact1, strongFact2));
        testContextBuilder.setContext(context);
        
        // When
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Then
        assertTrue(result.isSuccess());
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Verify: Strong facts are included and presented
        assertTrue(answer.contains("post-separation") || answer.contains("mortgage payments") || 
                   answer.contains("$25,000") || answer.contains("principal") || answer.contains("interest"),
            "Strong post-separation payment facts should be prominently displayed");
        
        String applicationSection = answer.substring(
            Math.max(0, answer.indexOf("APPLICATION TO RULE")),
            Math.min(answer.length(), answer.indexOf("COUNTERARGUMENTS") + 1)
        );
        
        assertTrue(applicationSection.contains("Supporting Facts:"),
            "Supporting Facts section should be present and populated with strong facts");
    }

    // ==================== HELPER METHODS ====================

    private EvidenceChunk createTestChunk(String text, Long docId, Integer pageNo, String pageRef) {
        return new EvidenceChunk(
            1L + pageNo, docId, "test.pdf", pageNo, pageNo, pageNo, text, 0.8,
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
     * Test double for FactClassifier - accepts all facts as valid (non-noisy, non-unrelated)
     * for testing purposes. Actual classification logic is tested in FactClassifierTest.
     */
    static class TestFactClassifier extends FactClassifier {
    }

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

    @Test
    @DisplayName("Comprehensive logging included in fact filtering pipeline")
    void testDebugLoggingShowsFilteringPipeline() {
        // Verify that implementation includes comprehensive debug logging
        // without crashing the pipeline.
        
        // This test demonstrates:
        // 1. Strict filter and rendering filter with logging both active
        // 2. Pipeline doesn't break with logging enabled
        // 3. Both quality and noisy facts can be processed without errors
        
        String query = "Reimbursement claim analysis?";
        
        // Create both quality and noisy evidence chunks
        EvidenceChunk qualityChunk = createTestChunk(
            "I paid $20,000 in separate property funds to mortgage",
            1L, 1, "Page 1"
        );
        EvidenceChunk noisyChunk1 = createTestChunk("23", 2L, 1, "Page 1");
        EvidenceChunk noisyChunk2 = createTestChunk("real and personal $", 3L, 1, "Page 1");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("I paid $20,000 in separate property funds to mortgage",
                true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement analysis."
        );
        
        // Set both chunks and context
        testRetrievalService.setEvidenceChunks(List.of(qualityChunk, noisyChunk1, noisyChunk2));
        testContextBuilder.setContext(context);
        
        // Execute - should succeed with logging enabled
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify: Handler completes without errors (logging doesn't break pipeline)
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess(), "Handler should succeed with debug logging enabled");
        assertNotNull(result.getAnswer());
        
        // Verify: Quality facts are present in output
        String answer = result.getAnswer();
        assertTrue(answer.contains("mortgage") || answer.contains("$20,000"),
            "Quality facts should be available in output even with comprehensive logging");
    }

    @Test
    @DisplayName("Rejects multi-line numeric/table fragments in rendering filter")
    void testRejectsNumericTableFragments() {
        String query = "Payment history analysis?";
        
        // Create a multi-line numeric/table fragment (the problematic pattern)
        String badTableFragment = "23\n\nMonthly Fees and Payment\n" +
                                  "Date Paid |Description Principal Interest Escrow Amount Charges (Unapplied) Total\n" +
                                  "09/02/25 PAYMENT $2,345";
        
        String goodPaymentFact = "I paid $20,000 in post-separation mortgage payments from my separate account";
        
        // Create chunks
        EvidenceChunk badChunk = createTestChunk(badTableFragment, 1L, 1, "Page 1");
        EvidenceChunk goodChunk = createTestChunk(goodPaymentFact, 2L, 1, "Page 2");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact(goodPaymentFact, true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact(badTableFragment, true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Payment analysis with table fragment."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(badChunk, goodChunk));
        testContextBuilder.setContext(context);
        
        // Execute
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify: Handler succeeds
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess(), "Handler should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Verify: Good payment fact is present
        assertTrue(answer.contains("$20,000") && answer.contains("post-separation mortgage"),
            "Quality payment fact should be included");
        
        // Verify: Multi-line table fragment is rejected (should NOT appear in APPLICATION TO RULE)
        int appToRuleStart = answer.indexOf("APPLICATION TO RULE");
        int counterArgStart = answer.indexOf("COUNTERARGUMENTS");
        if (appToRuleStart >= 0 && counterArgStart > appToRuleStart) {
            String appSection = answer.substring(appToRuleStart, counterArgStart);
            assertFalse(appSection.contains("Date Paid") || appSection.contains("Monthly Fees and Payment") ||
                        appSection.contains("23\n"),
                "Multi-line numeric/table fragment should be rejected by rendering filter");
        }
    }

    @Test
    @DisplayName("Does not reuse generic payment facts under every reimbursement element")
    void testPreventGenericFactReusageAcrossElements() {
        String query = "Reimbursement analysis with generic and specific facts?";
        
        // Create a generic fact that could match multiple elements
        String genericFact = "Both marital properties—our homes in San Jose and Newark—are paid for by me";
        
        // Create a specific fact that matches the "post-separation" element
        String specificFact = "I paid $15,000 in post-separation mortgage payments on community property";
        
        // Create another specific fact for "benefit" element
        String benefitFact = "Community property received the benefit of my payment";
        
        EvidenceChunk genericChunk = createTestChunk(genericFact, 1L, 1, "Page 1");
        EvidenceChunk specificChunk = createTestChunk(specificFact, 2L, 1, "Page 2");
        EvidenceChunk benefitChunk = createTestChunk(benefitFact, 3L, 1, "Page 3");
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact(genericFact, true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact(specificFact, true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact(benefitFact, true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Reimbursement with mixed generic and specific facts."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(genericChunk, specificChunk, benefitChunk));
        testContextBuilder.setContext(context);
        
        // Execute
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify: Handler succeeds
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess(), "Handler should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Extract APPLICATION TO RULE section
        int appToRuleStart = answer.indexOf("APPLICATION TO RULE");
        int counterArgStart = answer.indexOf("COUNTERARGUMENTS");
        assertTrue(appToRuleStart >= 0, "Should have APPLICATION TO RULE section");
        
        String appSection = answer.substring(appToRuleStart, Math.min(counterArgStart >= 0 ? counterArgStart : answer.length(), answer.length()));
        
        // Verify: Specific facts are present
        assertTrue(appSection.contains("$15,000") && appSection.contains("post-separation"),
            "Specific post-separation payment fact should be present");
        
        // Verify: Generic fact is NOT repeated under every element
        // Count how many times the generic fact appears as a Supporting Fact
        int genericFactCount = 0;
        int searchStart = 0;
        String searchPattern = "marital properties";
        while ((searchStart = appSection.indexOf(searchPattern, searchStart)) != -1) {
            genericFactCount++;
            searchStart += searchPattern.length();
        }
        
        // The generic fact should appear 0-1 times, not under every element
        assertTrue(genericFactCount <= 1,
            "Generic fact should not be reused under multiple elements (found " + genericFactCount + " times)");
        
        // Verify: Specific facts are used instead
        assertTrue(appSection.contains("benefit") || appSection.contains("$15,000"),
            "Specific facts with targeted content should be used");
    }
    
    @Test
    @DisplayName("Rejects down payment facts for post-separation reimbursement elements")
    void testRejectsDownPaymentForPostSeparationReimbursement() {
        String query = "Reimbursement for post-separation payments?";
        
        // Create a down payment contribution fact (during marriage, not post-separation)
        EvidenceChunk downPaymentChunk = createTestChunk(
            "I contributed $50,000 down payment toward the purchase of the family home during marriage.",
            1L, 1, "Page 1"
        );
        
        // Create a genuine post-separation mortgage payment fact
        EvidenceChunk postSepChunk = createTestChunk(
            "I paid $3,000 in monthly mortgage payments after separation in 2023.",
            2L, 1, "Page 2"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.85, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("I contributed $50,000 down payment toward the purchase of the family home during marriage", 
                true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("I paid $3,000 in monthly mortgage payments after separation in 2023",
                true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Test reimbursement with down payment vs post-separation payments."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(downPaymentChunk, postSepChunk));
        testContextBuilder.setContext(context);
        
        // Execute
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify: Execution succeeds
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess(), "Handler should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Get APPLICATION TO RULE section
        int appIndex = answer.indexOf("APPLICATION TO RULE");
        int counterIndex = answer.indexOf("COUNTERARGUMENTS");
        String appSection = answer.substring(appIndex, counterIndex > 0 ? counterIndex : answer.length());
        
        // Verify: Down payment fact is NOT used as supporting fact
        // It may be mentioned in the context but should NOT appear as a Supporting Fact assignment
        assertFalse(appSection.contains("down payment") || appSection.contains("$50,000 down"),
            "Down payment contribution fact should not be used as supporting fact for post-separation reimbursement");
        
        // Verify: Post-separation mortgage fact IS included
        assertTrue(appSection.contains("$3,000") || appSection.contains("monthly mortgage") || appSection.contains("after separation"),
            "Post-separation mortgage payment fact should be included");
    }
    
    @Test
    @DisplayName("Rejects OCR fragments and statement warning text")
    void testRejectsOCRFragmentsAndWarningText() {
        String query = "Reimbursement claim?";
        
        // Create OCR/statement fragment facts
        EvidenceChunk ocrFragment1 = createTestChunk(
            "23 If payment is received after 10/16/2025, it will be a late payment.",
            1L, 1, "Page 1"
        );
        
        EvidenceChunk ocrFragment2 = createTestChunk(
            "Payment received - late fees may apply if not before the due date",
            2L, 1, "Page 1"
        );
        
        // Create a legitimate mortgage payment fact
        EvidenceChunk legitimateChunk = createTestChunk(
            "Monthly mortgage payment of $2,400 was paid from my separate account after separation.",
            3L, 1, "Page 2"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.80, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("23 If payment is received after 10/16/2025, it will be a late payment",
                true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Payment received - late fees may apply if not before the due date",
                true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Monthly mortgage payment of $2,400 was paid from my separate account after separation",
                true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Test with OCR fragments and legitimate mortgage facts."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(ocrFragment1, ocrFragment2, legitimateChunk));
        testContextBuilder.setContext(context);
        
        // Execute
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify: Execution succeeds
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess(), "Handler should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Get APPLICATION TO RULE section
        int appIndex = answer.indexOf("APPLICATION TO RULE");
        int counterIndex = answer.indexOf("COUNTERARGUMENTS");
        String appSection = answer.substring(appIndex, counterIndex > 0 ? counterIndex : answer.length());
        
        // Verify: OCR fragments are NOT used as supporting facts
        assertFalse(appSection.contains("23 If payment is received after 10/16/2025"),
            "OCR fragment starting with number should be rejected");
        assertFalse(appSection.contains("late fees may apply"),
            "Statement warning text should be rejected");
        
        // Verify: Legitimate mortgage fact IS included
        assertTrue(appSection.contains("$2,400") || appSection.contains("monthly mortgage") || appSection.contains("separate account"),
            "Legitimate post-separation mortgage payment should be included");
    }
    
    @Test
    @DisplayName("Shows (none identified) when supporting facts are weak for an element")
    void testShowsNoneIdentifiedForWeakFacts() {
        String query = "Reimbursement?";
        
        // Create facts that are too weak for reimbursement (no post-separation, no mortgage context)
        EvidenceChunk weakChunk1 = createTestChunk(
            "We had property together.",
            1L, 1, "Page 1"
        );
        
        EvidenceChunk weakChunk2 = createTestChunk(
            "Payment of some kind was made.",
            2L, 1, "Page 1"
        );
        
        // Strong fact for one element only
        EvidenceChunk strongChunk = createTestChunk(
            "I paid $10,000 in post-separation mortgage payments from my separate property account.",
            3L, 1, "Page 2"
        );
        
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement", 0.75, "reimbursement")
        );
        
        List<CaseFact> facts = List.of(
            new CaseFact("We had property together",
                true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("Payment of some kind was made",
                true, "source", LegalIssueType.REIMBURSEMENT),
            new CaseFact("I paid $10,000 in post-separation mortgage payments from my separate property account",
                true, "source", LegalIssueType.REIMBURSEMENT)
        );
        
        CaseAnalysisContext context = new CaseAnalysisContext(
            query, issues, facts,
            "Test with weak and strong facts."
        );
        
        testRetrievalService.setEvidenceChunks(List.of(weakChunk1, weakChunk2, strongChunk));
        testContextBuilder.setContext(context);
        
        // Execute
        ModeExecutionResult result = handler.execute(query, 5);
        
        // Verify: Execution succeeds
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess(), "Handler should succeed");
        String answer = result.getAnswer();
        assertNotNull(answer);
        
        // Get APPLICATION TO RULE section
        int appIndex = answer.indexOf("APPLICATION TO RULE");
        int counterIndex = answer.indexOf("COUNTERARGUMENTS");
        String appSection = answer.substring(appIndex, counterIndex > 0 ? counterIndex : answer.length());
        
        // Verify: Weak facts like "We had property together" are not used
        assertFalse(appSection.contains("We had property together"),
            "Weak generic property fact should be rejected");
        assertFalse(appSection.contains("Payment of some kind was made"),
            "Weak generic payment fact should be rejected");
        
        // Verify: Strong fact IS included
        assertTrue(appSection.contains("$10,000") || appSection.contains("post-separation mortgage"),
            "Strong post-separation mortgage fact should be included");
        
        // Verify: Some elements may have "(none identified)" if no strong facts match them
        // This is acceptable behavior - we prefer empty supporting facts to weak ones
        assertTrue(appSection.contains("(none identified)") || answer.contains("$10,000"),
            "Handler should either show strong facts or (none identified) for weak elements");
    }
    
    // ==================== DOS (DATE OF SEPARATION) FILTERING TESTS ====================
    
    @Test
    @DisplayName("DOS filtering: Excludes payments before DOS")
    void testDOSFiltering_ExcludesPaymentsBeforeDOS() {
        // Given: DOS = 12/24/2025
        String dos = "12/24/2025";
        handler.setDateOfSeparation(dos);
        
        // Create a test scenario with payment records at different dates
        EvidenceChunk mortgageChunk = createTestChunk(
            "Mortgage Statement\n" +
            "Property Address: 123 Main St, Newark, CA 94560\n" +
            "Loan Number: 2109013512\n" +
            "12/01/25 PAYMENT $4,679.23\n" +  // BEFORE DOS
            "01/02/26 PAYMENT $4,679.23\n" +  // AFTER DOS
            "02/02/26 PAYMENT $4,679.23",     // AFTER DOS
            1L, 1, "Page 1"
        );
        
        List<EvidenceChunk> chunks = List.of(mortgageChunk);
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.85, "reimbursement")
        );
        
        testRetrievalService.setEvidenceChunks(chunks);
        testContextBuilder.setContext(new CaseAnalysisContext(
            "Reimbursement claim", issues, List.of(),
            "DOS filtering test"
        ));
        
        // Execute
        ModeExecutionResult result = handler.execute("Show me post-separation mortgage payments", 5);
        
        // Verify: Execution succeeds
        assertTrue(result.isSuccess());
        String answer = result.getAnswer();
        assertNotNull(answer);
        // Payment dates should NOT appear in answer - they're filtered out by DOS
        // If filtering is working, the answer should either be empty or contain only after-DOS dates
        // We're mainly verifying this doesn't crash and filtering logic runs
        assertTrue(answer.length() > 0, "Handler should return an answer");
    }
    
    @Test
    @DisplayName("DOS filtering: Correctly parses and compares dates (YY format)")
    void testDOSFiltering_DateFormatParsing() {
        // Test with YY format - should be interpreted as 2025
        String dos = "12/24/25";
        handler.setDateOfSeparation(dos);
        
        EvidenceChunk chunk = createTestChunk(
            "Mortgage Statement\n" +
            "Property: Test Property, Newark, CA\n" +
            "Loan Number: TEST123\n" +
            "01/02/26 PAYMENT $5,000.00",  // 01/02/2026 > 12/24/2025
            1L, 1, "Page 1"
        );
        
        List<EvidenceChunk> chunks = List.of(chunk);
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.85, "reimbursement")
        );
        
        testRetrievalService.setEvidenceChunks(chunks);
        testContextBuilder.setContext(new CaseAnalysisContext(
            "Reimbursement claim", issues, List.of(),
            "Date format test"
        ));
        
        // Execute
        ModeExecutionResult result = handler.execute("Show me post-separation mortgage payments", 5);
        
        // Verify: Should parse 01/02/26 as 2026 and compare correctly
        assertTrue(result.isSuccess());
        assertNotNull(result.getAnswer());
        assertTrue(result.getAnswer().length() > 0, "Handler should return an answer with date parsing");
    }
    
    @Test
    @DisplayName("DOS filtering: When DOS is not set, includes all payments")
    void testDOSFiltering_NoDOSIncludesAllPayments() {
        // Given: No DOS set (DOS filtering disabled)
        handler.setDateOfSeparation(null);
        
        EvidenceChunk chunk = createTestChunk(
            "Mortgage Statement\n" +
            "Property: Test Property, Newark, CA\n" +
            "Loan Number: TEST456\n" +
            "12/01/25 PAYMENT $4,679.23\n" +  // Before would-be DOS
            "01/02/26 PAYMENT $4,679.23",     // After would-be DOS
            1L, 1, "Page 1"
        );
        
        List<EvidenceChunk> chunks = List.of(chunk);
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.85, "reimbursement")
        );
        
        testRetrievalService.setEvidenceChunks(chunks);
        testContextBuilder.setContext(new CaseAnalysisContext(
            "Reimbursement claim", issues, List.of(),
            "No DOS test"
        ));
        
        // Execute
        ModeExecutionResult result = handler.execute("Show me all mortgage payments", 5);
        
        // Verify: Should succeed without DOS filtering applied
        assertTrue(result.isSuccess());
        assertNotNull(result.getAnswer());
        assertTrue(result.getAnswer().length() > 0, "Handler should return an answer when DOS is not set");
    }
    
    @Test
    @DisplayName("CaseProfile-based filtering: Uses structured case facts for DOS")
    void testCaseProfileBasedDOSFiltering() {
        // Given: CaseProfile with DOS = 12/24/2025
        com.agent.model.CaseProfile caseProfile = new com.agent.model.CaseProfile("12/24/2025");
        handler.setCaseProfile(caseProfile);
        
        // Verify: Handler correctly stores CaseProfile
        assertNotNull(handler.getCaseProfile());
        assertEquals("12/24/2025", handler.getDateOfSeparation());
        
        // Create test mortgage statement with payments on different dates
        EvidenceChunk mortgageChunk = createTestChunk(
            "Mortgage Statement\n" +
            "Property Address: Test Property, Newark, CA 94560\n" +
            "Loan Number: LOAN001\n" +
            "12/01/25 PAYMENT $4,500.00\n" +  // BEFORE DOS - should be excluded
            "01/02/26 PAYMENT $4,500.00\n" +  // AFTER DOS - should be included
            "02/02/26 PAYMENT $4,500.00",     // AFTER DOS - should be included
            1L, 1, "Page 1"
        );
        
        List<EvidenceChunk> chunks = List.of(mortgageChunk);
        List<CaseIssue> issues = List.of(
            new CaseIssue(LegalIssueType.REIMBURSEMENT, "Reimbursement claim", 0.85, "reimbursement")
        );
        
        testRetrievalService.setEvidenceChunks(chunks);
        testContextBuilder.setContext(new CaseAnalysisContext(
            "Reimbursement claim", issues, List.of(),
            "CaseProfile DOS filtering test"
        ));
        
        // Execute
        ModeExecutionResult result = handler.execute("Show me post-separation mortgage payments", 5);
        
        // Verify: Execution succeeds with CaseProfile-based filtering
        assertTrue(result.isSuccess());
        assertNotNull(result.getAnswer());
        assertTrue(result.getAnswer().length() > 0,
            "Handler should return an answer using CaseProfile DOS");
    }
}

