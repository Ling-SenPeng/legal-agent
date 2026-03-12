package com.agent.model;

/**
 * Enumeration of task modes for query routing.
 * 
 * Determines how queries are processed and what retrieval/reasoning strategy is applied:
 * - DOCUMENT_QA: Extract specific information from documents ("What does the document say?")
 * - LEGAL_RESEARCH: Find relevant cases, precedents, or legal authorities ("Find cases about X topic")
 * - CASE_ANALYSIS: Evaluate facts against legal principles ("Do I have a valid claim?")
 * - DRAFTING: Generate legal documents or memoranda ("Draft a brief arguing...")
 */
public enum TaskMode {
    /**
     * Extract factual information directly from documents.
     * Characteristics: literal interpretation, specific passages, plain reading.
     * Example: "What does the declaration say about Newark occupancy?"
     */
    DOCUMENT_QA,
    
    /**
     * Search for and analyze legal authorities and precedents.
     * Characteristics: case law focus, precedent relevance, legal principles.
     * Example: "Find cases about post-separation mortgage reimbursement."
     */
    LEGAL_RESEARCH,
    
    /**
     * Evaluate facts and apply legal reasoning to reach conclusions.
     * Characteristics: legal analysis, strength assessment, predictive reasoning.
     * Example: "Based on these facts, do I have a strong reimbursement claim?"
     */
    CASE_ANALYSIS,
    
    /**
     * Generate legal documents or written analyses.
     * Characteristics: document creation, persuasive writing, structured output.
     * Example: "Draft a short memo arguing for reimbursement."
     */
    DRAFTING
}
