package com.agent.service.analysis.authority;

import com.agent.model.analysis.CaseIssue;
import com.agent.model.analysis.LegalIssueType;
import com.agent.model.analysis.authority.AuthorityMatch;
import com.agent.model.analysis.authority.AuthorityType;
import com.agent.model.analysis.authority.AuthoritySummary;
import com.agent.model.analysis.authority.LegalAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AuthoritySummarizer.
 * 
 * Verifies that authority matches are summarized into coherent rule descriptions
 * for each legal issue type.
 */
class AuthoritySummarizerTest {
    
    private AuthoritySummarizer summarizer;
    
    @BeforeEach
    void setUp() {
        summarizer = new AuthoritySummarizer();
    }
    
    @Test
    void testSummarizeReimbursementAuthorities() {
        // Given: A reimbursement issue with relevant authorities
        CaseIssue issue = new CaseIssue(
            LegalIssueType.REIMBURSEMENT,
            "Seeking reimbursement for post-separation mortgage payments",
            0.85,
            "mortgage, payment"
        );
        
        LegalAuthority epsteinCase = new LegalAuthority(
            "case-epstein-001",
            "Marriage of Epstein",
            "191 Cal.App.3d 592",
            AuthorityType.CASE_LAW,
            "California Appellate Court",
            "A spouse who uses separate property to pay community obligations after separation may be entitled to reimbursement.",
            0.95
        );
        
        AuthorityMatch match = new AuthorityMatch(
            LegalIssueType.REIMBURSEMENT,
            epsteinCase,
            0.92,
            "Epstein reimbursement"
        );
        
        List<AuthorityMatch> matches = List.of(match);
        
        // When: Summarizing
        AuthoritySummary summary = summarizer.summarize(issue, matches);
        
        // Then: Should generate coherent rule summary
        assertNotNull(summary);
        assertEquals(LegalIssueType.REIMBURSEMENT, summary.getIssueType());
        assertGreater(summary.getAuthorityCount(), 0);
        assertNotNull(summary.getSummarizedRule());
        assertTrue(summary.getSummarizedRule().toLowerCase().contains("reimbursement"));
        assertTrue(summary.getSummarizedRule().toLowerCase().contains("separate"));
    }
    
    @Test
    void testSummarizeCustodyAuthorities() {
        // Given: A custody issue with relevant authorities
        CaseIssue issue = new CaseIssue(
            LegalIssueType.CUSTODY,
            "Determining custody arrangement",
            0.88,
            "custody, child"
        );
        
        LegalAuthority custodyStatute = new LegalAuthority(
            "statute-fam-3011",
            "California Family Code § 3011",
            "Cal. Fam. Code § 3011",
            AuthorityType.STATUTE,
            "California Legislature",
            "Requires courts to consider best interest of child.",
            0.93
        );
        
        AuthorityMatch match = new AuthorityMatch(
            LegalIssueType.CUSTODY,
            custodyStatute,
            0.90,
            "best interest of child custody"
        );
        
        List<AuthorityMatch> matches = List.of(match);
        
        // When: Summarizing
        AuthoritySummary summary = summarizer.summarize(issue, matches);
        
        // Then: Should generate custody-specific rule summary
        assertNotNull(summary);
        assertEquals(LegalIssueType.CUSTODY, summary.getIssueType());
        assertNotNull(summary.getSummarizedRule());
        assertTrue(summary.getSummarizedRule().toLowerCase().contains("best interest"));
        assertTrue(summary.getSummarizedRule().toLowerCase().contains("child"));
    }
    
    @Test
    void testSummarizePropertyCharacterizationAuthorities() {
        // Given: A property characterization issue
        CaseIssue issue = new CaseIssue(
            LegalIssueType.PROPERTY_CHARACTERIZATION,
            "Determining property characterization",
            0.82,
            "property, community, separate"
        );
        
        LegalAuthority statute = new LegalAuthority(
            "statute-fam-750",
            "California Family Code § 750",
            "Cal. Fam. Code § 750",
            AuthorityType.STATUTE,
            "California Legislature",
            "Establishes community property definition.",
            0.94
        );
        
        AuthorityMatch match = new AuthorityMatch(
            LegalIssueType.PROPERTY_CHARACTERIZATION,
            statute,
            0.91,
            "community property characterization"
        );
        
        List<AuthorityMatch> matches = List.of(match);
        
        // When: Summarizing
        AuthoritySummary summary = summarizer.summarize(issue, matches);
        
        // Then: Should address community vs separate property
        assertNotNull(summary);
        assertTrue(summary.getSummarizedRule().toLowerCase().contains("community"));
        assertTrue(summary.getSummarizedRule().toLowerCase().contains("separate"));
    }
    
    @Test
    void testPreservesStatuteWhenMixedAuthorities() {
        // Given: A reimbursement issue with both statute and case law authorities
        // This tests the critical preservation logic for statutes
        CaseIssue issue = new CaseIssue(
            LegalIssueType.REIMBURSEMENT,
            "post separation mortgage reimbursement",
            0.85,
            "reimbursement, mortgage"
        );
        
        // Create authorities in order: case law authorities first, statute last
        // This simulates retrieval where case law might score higher but statute should be preserved
        LegalAuthority mooreCase = new LegalAuthority(
            "case-moore-001",
            "Marriage of Moore",
            "28 Cal.4th 366 (2000)",
            AuthorityType.CASE_LAW,
            "California Supreme Court",
            "General property characterization principles.",
            0.78
        );
        
        LegalAuthority epsteinCase = new LegalAuthority(
            "case-epstein-001",
            "Marriage of Epstein",
            "191 Cal.App.3d 592 (1987)",
            AuthorityType.CASE_LAW,
            "California Appellate Court",
            "A spouse who uses separate property to pay community obligations after separation may be entitled to reimbursement.",
            0.92
        );
        
        LegalAuthority familyCodeStatute = new LegalAuthority(
            "statute-fam-750",
            "California Family Code § 750",
            "Cal. Fam. Code § 750",
            AuthorityType.STATUTE,
            "California Legislature",
            "Community property consists of all property acquired during marriage except separate property.",
            0.80
        );
        
        // Create matches with Epstein highest, then statute, then Moore
        AuthorityMatch match1 = new AuthorityMatch(LegalIssueType.REIMBURSEMENT, epsteinCase, 0.92, "query");
        AuthorityMatch match2 = new AuthorityMatch(LegalIssueType.REIMBURSEMENT, familyCodeStatute, 0.80, "query");
        AuthorityMatch match3 = new AuthorityMatch(LegalIssueType.REIMBURSEMENT, mooreCase, 0.78, "query");
        
        List<AuthorityMatch> matches = List.of(match1, match2, match3);
        
        // When: Summarizing
        AuthoritySummary summary = summarizer.summarize(issue, matches);
        
        // Then: Should preserve both Epstein AND the statute
        assertNotNull(summary, "Summary should not be null");
        assertTrue(summary.getAuthorityCount() >= 2,
            "Should have at least 2 authorities (statute + case law), but got: " + summary.getAuthorityCount());
        
        // Extract the authority IDs that were kept
        List<String> keptIds = summary.getAuthorities().stream()
            .map(auth -> auth.getAuthorityId())
            .toList();
        
        // Log what was kept for visibility
        System.out.println("✓ Kept " + keptIds.size() + " authorities: " + keptIds);
        
        // CRITICAL: Statute should be preserved
        assertTrue(keptIds.contains("statute-fam-750"),
            "CRITICAL: Statute (Family Code § 750) should be preserved in summary. " +
            "Kept authorities: " + keptIds);
        
        // CRITICAL: Epstein should be preserved
        assertTrue(keptIds.contains("case-epstein-001"),
            "Epstein case should be preserved as it's relevant to reimbursement. Kept authorities: " + keptIds);
        
        // Moore might or might not be kept depending on final count, that's OK
        // The key requirement is that statute is preserved
    }
    
    @Test
    void testSummarizeWithNoAuthorities() {
        // Given: An issue with no matching authorities
        CaseIssue issue = new CaseIssue(
            LegalIssueType.REIMBURSEMENT,
            "Seeking reimbursement",
            0.85,
            "reimbursement"
        );
        
        List<AuthorityMatch> emptyMatches = new ArrayList<>();
        
        // When: Summarizing
        AuthoritySummary summary = summarizer.summarize(issue, emptyMatches);
        
        // Then: Should return valid empty summary
        assertNotNull(summary);
        assertEquals(LegalIssueType.REIMBURSEMENT, summary.getIssueType());
        assertEquals(0, summary.getAuthorityCount());
        assertNotNull(summary.getSummarizedRule());
        assertTrue(summary.getSummarizedRule().toLowerCase().contains("no authorities"));
    }
    
    @Test
    void testSummarizeWithMultipleAuthorities() {
        // Given: Multiple matching authorities
        CaseIssue issue = new CaseIssue(
            LegalIssueType.REIMBURSEMENT,
            "Seeking reimbursement",
            0.85,
            "reimbursement"
        );
        
        LegalAuthority authority1 = new LegalAuthority(
            "case-1",
            "Marriage of Epstein",
            "191 Cal.App.3d 592",
            AuthorityType.CASE_LAW,
            "Court",
            "Rule about reimbursement.",
            0.95
        );
        
        LegalAuthority authority2 = new LegalAuthority(
            "statute-1",
            "Family Code § 2640",
            "Cal. Fam. Code § 2640",
            AuthorityType.STATUTE,
            "Legislature",
            "Reimbursement mechanism.",
            0.92
        );
        
        List<AuthorityMatch> matches = List.of(
            new AuthorityMatch(LegalIssueType.REIMBURSEMENT, authority1, 0.92, "Epstein"),
            new AuthorityMatch(LegalIssueType.REIMBURSEMENT, authority2, 0.90, "statute")
        );
        
        // When: Summarizing
        AuthoritySummary summary = summarizer.summarize(issue, matches);
        
        // Then: Should reference top authorities by citation
        assertNotNull(summary);
        assertGreater(summary.getAuthorityCount(), 0);
        String ruleSummary = summary.getSummarizedRule();
        assertTrue(ruleSummary.contains("191 Cal.App.3d") || ruleSummary.contains("Epstein"));
    }
    
    @Test
    void testSummaryReferencesTopAuthorities() {
        // Given: Multiple authority matches with varying scores
        CaseIssue issue = new CaseIssue(
            LegalIssueType.CUSTODY,
            "Custody determination",
            0.80,
            "custody"
        );
        
        LegalAuthority topAuth = new LegalAuthority(
            "top-auth",
            "Best Interest Standard",
            "Cal. Fam. Code § 3011",
            AuthorityType.STATUTE,
            "Legislature",
            "Best interest standard.",
            0.98
        );
        
        LegalAuthority lowerAuth = new LegalAuthority(
            "lower-auth",
            "Secondary Authority",
            "Court Case 2023",
            AuthorityType.CASE_LAW,
            "Court",
            "Supporting case.",
            0.75
        );
        
        List<AuthorityMatch> matches = List.of(
            new AuthorityMatch(LegalIssueType.CUSTODY, lowerAuth, 0.75, "query"),
            new AuthorityMatch(LegalIssueType.CUSTODY, topAuth, 0.98, "query")
        );
        
        // When: Summarizing (should select top by score)
        AuthoritySummary summary = summarizer.summarize(issue, matches);
        
        // Then: Top authority should be included
        assertNotNull(summary);
        assertTrue(summary.getAuthorities().contains(topAuth));
    }
    
    private void assertGreater(int actual, int threshold) {
        assertTrue(actual > threshold, "Expected " + actual + " > " + threshold);
    }
}
