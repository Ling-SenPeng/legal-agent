package com.agent.model;

import java.util.List;

/**
 * Represents a plan for retrieving relevant documents.
 * Converts user queries into optimized retrieval strategies.
 */
public class RetrievalPlan {
    private String originalQuery;
    private String cleanedQuery;  // Query after instruction words removed
    private String intent;
    private List<String> entities;
    private List<String> keywordQueries;
    private String vectorQuery;
    private String answerInstruction;
    private String outputFormat;
    private boolean fallbackUsed;  // Track if fallback was triggered

    public RetrievalPlan(String originalQuery, String cleanedQuery, String intent, List<String> entities,
                         List<String> keywordQueries, String vectorQuery,
                         String answerInstruction, String outputFormat) {
        this.originalQuery = originalQuery;
        this.cleanedQuery = cleanedQuery;
        this.intent = intent;
        this.entities = entities;
        this.keywordQueries = keywordQueries;
        this.vectorQuery = vectorQuery;
        this.answerInstruction = answerInstruction;
        this.outputFormat = outputFormat;
        this.fallbackUsed = false;
    }

    // Getters and Setters
    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }

    public String getCleanedQuery() {
        return cleanedQuery;
    }

    public void setCleanedQuery(String cleanedQuery) {
        this.cleanedQuery = cleanedQuery;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities;
    }

    public List<String> getKeywordQueries() {
        return keywordQueries;
    }

    public void setKeywordQueries(List<String> keywordQueries) {
        this.keywordQueries = keywordQueries;
    }

    public String getVectorQuery() {
        return vectorQuery;
    }

    public void setVectorQuery(String vectorQuery) {
        this.vectorQuery = vectorQuery;
    }

    public String getAnswerInstruction() {
        return answerInstruction;
    }

    public void setAnswerInstruction(String answerInstruction) {
        this.answerInstruction = answerInstruction;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    @Override
    public String toString() {
        return "RetrievalPlan{" +
                "originalQuery='" + originalQuery + '\'' +
                ", cleanedQuery='" + cleanedQuery + '\'' +
                ", intent='" + intent + '\'' +
                ", entities=" + entities +
                ", keywordQueries=" + keywordQueries +
                ", vectorQuery='" + vectorQuery + '\'' +
                ", answerInstruction='" + answerInstruction + '\'' +
                ", outputFormat='" + outputFormat + '\'' +
                ", fallbackUsed=" + fallbackUsed +
                '}';
    }
}
