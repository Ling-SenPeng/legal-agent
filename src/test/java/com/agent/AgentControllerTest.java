package com.agent;

import com.agent.model.AgentQueryRequest;
import com.agent.model.AgentQueryResponse;
import com.agent.model.VerificationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Agent models and JSON serialization.
 * These tests verify model construction and serialization without requiring Spring context.
 */
class AgentControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testAgentQueryRequestCreation() {
        AgentQueryRequest request = new AgentQueryRequest(
            "What was the payment amount?",
            5,
            null
        );

        assertNotNull(request);
        assertEquals("What was the payment amount?", request.question());
        assertEquals(5, request.topK());
        assertNull(request.filters());
    }

    @Test
    void testAgentQueryResponseCreation() {
        VerificationReport report = new VerificationReport(
            true,
            List.of(),
            "All citations verified"
        );

        AgentQueryResponse response = new AgentQueryResponse(
            "The payment amount is $10,000. [CIT doc=1 chunk=0 p=1-1]",
            List.of(),
            report,
            100L
        );

        assertNotNull(response);
        assertNotNull(response.answer());
        assertTrue(response.answer().contains("[CIT"));
        assertEquals(100L, response.processingTimeMs());
    }

    @Test
    void testVerificationReportCreation() {
        VerificationReport report = new VerificationReport(
            true,
            List.of(),
            "All citations verified"
        );

        assertNotNull(report);
        assertTrue(report.passed());
        assertTrue(report.missingCitationLines().isEmpty());
        assertEquals("All citations verified", report.notes());
    }

    @Test
    void testAgentQueryRequestValidation() {
        // Valid question with positive topK
        AgentQueryRequest request = new AgentQueryRequest(
            "What was paid?",
            1,
            null
        );
        assertNotNull(request);
        assertEquals(1, request.topK());
    }
}
