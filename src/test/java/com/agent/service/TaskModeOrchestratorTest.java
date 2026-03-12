package com.agent.service;

import com.agent.model.ModeExecutionResult;
import com.agent.model.TaskMode;
import com.agent.model.TaskRoutingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaskModeOrchestrator.
 * 
 * Test Coverage:
 * 1. Correct handler selection based on routing result
 * 2. Handler execution with query and topK parameters
 * 3. Routing decision logging
 * 4. Error handling for missing handlers
 * 5. Handler registry initialization
 */
@ExtendWith(MockitoExtension.class)
class TaskModeOrchestratorTest {
    
    private TaskModeOrchestrator orchestrator;
    
    @Mock
    private TaskRouter mockRouter;
    
    @Mock
    private TaskModeHandler mockDocumentQaHandler;
    
    @Mock
    private TaskModeHandler mockLegalResearchHandler;
    
    @Mock
    private TaskModeHandler mockCaseAnalysisHandler;
    
    @Mock
    private TaskModeHandler mockDraftingHandler;

    @BeforeEach
    void setUp() {
        // Setup handler modes
        when(mockDocumentQaHandler.getMode()).thenReturn(TaskMode.DOCUMENT_QA);
        when(mockLegalResearchHandler.getMode()).thenReturn(TaskMode.LEGAL_RESEARCH);
        when(mockCaseAnalysisHandler.getMode()).thenReturn(TaskMode.CASE_ANALYSIS);
        when(mockDraftingHandler.getMode()).thenReturn(TaskMode.DRAFTING);
        
        // Create orchestrator with mocked handlers
        orchestrator = new TaskModeOrchestrator(
            mockRouter,
            Arrays.asList(
                mockDocumentQaHandler,
                mockLegalResearchHandler,
                mockCaseAnalysisHandler,
                mockDraftingHandler
            )
        );
    }

    @Test
    @DisplayName("Execute selects DOCUMENT_QA handler when routed to DOCUMENT_QA")
    void testSelectDocumentQaHandler() {
        // Given
        String query = "What does the document say?";
        int topK = 10;
        TaskRoutingResult routingResult = new TaskRoutingResult(
            TaskMode.DOCUMENT_QA,
            0.85,
            "Detected QA intent"
        );
        ModeExecutionResult handlerResult = new ModeExecutionResult(
            TaskMode.DOCUMENT_QA,
            "This is the answer.",
            "Retrieved 5 chunks"
        );
        
        when(mockRouter.route(query)).thenReturn(routingResult);
        when(mockDocumentQaHandler.execute(query, topK)).thenReturn(handlerResult);
        
        // When
        ModeExecutionResult result = orchestrator.execute(query, topK);
        
        // Then
        verify(mockRouter).route(query);
        verify(mockDocumentQaHandler).execute(query, topK);
        verify(mockLegalResearchHandler, never()).execute(anyString(), anyInt());
        verify(mockCaseAnalysisHandler, never()).execute(anyString(), anyInt());
        verify(mockDraftingHandler, never()).execute(anyString(), anyInt());
        
        assertEquals(TaskMode.DOCUMENT_QA, result.getMode());
        assertEquals("This is the answer.", result.getAnswer());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Execute selects LEGAL_RESEARCH handler when routed to LEGAL_RESEARCH")
    void testSelectLegalResearchHandler() {
        // Given
        String query = "Find cases about reimbursement.";
        int topK = 15;
        TaskRoutingResult routingResult = new TaskRoutingResult(
            TaskMode.LEGAL_RESEARCH,
            0.80,
            "Detected research intent"
        );
        ModeExecutionResult handlerResult = new ModeExecutionResult(
            TaskMode.LEGAL_RESEARCH,
            "Found 3 relevant cases.",
            "Mode: LEGAL_RESEARCH"
        );
        
        when(mockRouter.route(query)).thenReturn(routingResult);
        when(mockLegalResearchHandler.execute(query, topK)).thenReturn(handlerResult);
        
        // When
        ModeExecutionResult result = orchestrator.execute(query, topK);
        
        // Then
        verify(mockRouter).route(query);
        verify(mockLegalResearchHandler).execute(query, topK);
        verify(mockDocumentQaHandler, never()).execute(anyString(), anyInt());
        verify(mockCaseAnalysisHandler, never()).execute(anyString(), anyInt());
        verify(mockDraftingHandler, never()).execute(anyString(), anyInt());
        
        assertEquals(TaskMode.LEGAL_RESEARCH, result.getMode());
        assertEquals("Found 3 relevant cases.", result.getAnswer());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Execute selects CASE_ANALYSIS handler when routed to CASE_ANALYSIS")
    void testSelectCaseAnalysisHandler() {
        // Given
        String query = "Do I have a strong claim?";
        int topK = 12;
        TaskRoutingResult routingResult = new TaskRoutingResult(
            TaskMode.CASE_ANALYSIS,
            0.75,
            "Detected analysis intent"
        );
        ModeExecutionResult handlerResult = new ModeExecutionResult(
            TaskMode.CASE_ANALYSIS,
            "Analysis shows moderate strength.",
            "Assessment: Moderate risk"
        );
        
        when(mockRouter.route(query)).thenReturn(routingResult);
        when(mockCaseAnalysisHandler.execute(query, topK)).thenReturn(handlerResult);
        
        // When
        ModeExecutionResult result = orchestrator.execute(query, topK);
        
        // Then
        verify(mockRouter).route(query);
        verify(mockCaseAnalysisHandler).execute(query, topK);
        verify(mockDocumentQaHandler, never()).execute(anyString(), anyInt());
        verify(mockLegalResearchHandler, never()).execute(anyString(), anyInt());
        verify(mockDraftingHandler, never()).execute(anyString(), anyInt());
        
        assertEquals(TaskMode.CASE_ANALYSIS, result.getMode());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Execute selects DRAFTING handler when routed to DRAFTING")
    void testSelectDraftingHandler() {
        // Given
        String query = "Draft a memo arguing for reimbursement.";
        int topK = 20;
        TaskRoutingResult routingResult = new TaskRoutingResult(
            TaskMode.DRAFTING,
            0.90,
            "Detected drafting intent"
        );
        ModeExecutionResult handlerResult = new ModeExecutionResult(
            TaskMode.DRAFTING,
            "MEMORANDUM...",
            "Document type: Memo"
        );
        
        when(mockRouter.route(query)).thenReturn(routingResult);
        when(mockDraftingHandler.execute(query, topK)).thenReturn(handlerResult);
        
        // When
        ModeExecutionResult result = orchestrator.execute(query, topK);
        
        // Then
        verify(mockRouter).route(query);
        verify(mockDraftingHandler).execute(query, topK);
        verify(mockDocumentQaHandler, never()).execute(anyString(), anyInt());
        verify(mockLegalResearchHandler, never()).execute(anyString(), anyInt());
        verify(mockCaseAnalysisHandler, never()).execute(anyString(), anyInt());
        
        assertEquals(TaskMode.DRAFTING, result.getMode());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Handler is called with correct query and topK parameters")
    void testHandlerParameterPassing() {
        // Given
        String query = "Extract dates from the document.";
        int topK = 25;
        
        when(mockRouter.route(query)).thenReturn(
            new TaskRoutingResult(TaskMode.DOCUMENT_QA, 0.80, "QA intent")
        );
        when(mockDocumentQaHandler.execute(query, topK)).thenReturn(
            new ModeExecutionResult(TaskMode.DOCUMENT_QA, "Dates: ...", "metadata")
        );
        
        // When
        orchestrator.execute(query, topK);
        
        // Then - verify exact parameters passed to handler
        verify(mockDocumentQaHandler).execute(query, topK);
    }

    @Test
    @DisplayName("Orchestrator returns handler result without modification")
    void testResultPassthrough() {
        // Given
        ModeExecutionResult expectedResult = new ModeExecutionResult(
            TaskMode.DOCUMENT_QA,
            "Expected answer",
            "Expected metadata"
        );
        
        when(mockRouter.route(anyString())).thenReturn(
            new TaskRoutingResult(TaskMode.DOCUMENT_QA, 0.85, "intent")
        );
        when(mockDocumentQaHandler.execute(anyString(), anyInt())).thenReturn(expectedResult);
        
        // When
        ModeExecutionResult result = orchestrator.execute("query", 10);
        
        // Then
        assertEquals(expectedResult.getMode(), result.getMode());
        assertEquals(expectedResult.getAnswer(), result.getAnswer());
        assertEquals(expectedResult.getMetadata(), result.getMetadata());
        assertEquals(expectedResult.isSuccess(), result.isSuccess());
    }

    @Test
    @DisplayName("Orchestrator handles handler execution errors gracefully")
    void testHandlerErrorHandling() {
        // Given
        String query = "test query";
        
        when(mockRouter.route(query)).thenReturn(
            new TaskRoutingResult(TaskMode.DOCUMENT_QA, 0.85, "intent")
        );
        when(mockDocumentQaHandler.execute(anyString(), anyInt()))
            .thenThrow(new RuntimeException("Handler error"));
        
        // When
        ModeExecutionResult result = orchestrator.execute(query, 10);
        
        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Orchestrator error"));
    }

    @Test
    @DisplayName("getHandler returns correct handler for mode")
    void testGetHandlerByMode() {
        // When & Then
        assertEquals(mockDocumentQaHandler, orchestrator.getHandler(TaskMode.DOCUMENT_QA));
        assertEquals(mockLegalResearchHandler, orchestrator.getHandler(TaskMode.LEGAL_RESEARCH));
        assertEquals(mockCaseAnalysisHandler, orchestrator.getHandler(TaskMode.CASE_ANALYSIS));
        assertEquals(mockDraftingHandler, orchestrator.getHandler(TaskMode.DRAFTING));
    }

    @Test
    @DisplayName("Handler registry contains all registered handlers")
    void testHandlerRegistry() {
        // When
        var registry = orchestrator.getHandlerRegistry();
        
        // Then
        assertEquals(4, registry.size());
        assertTrue(registry.containsKey(TaskMode.DOCUMENT_QA));
        assertTrue(registry.containsKey(TaskMode.LEGAL_RESEARCH));
        assertTrue(registry.containsKey(TaskMode.CASE_ANALYSIS));
        assertTrue(registry.containsKey(TaskMode.DRAFTING));
    }
}
