package com.agent.model;

import java.util.List;

/**
 * Internal diagnostic snapshot capturing detailed retrieval execution metrics.
 * Used for logging and debugging retrieval quality issues.
 * 
 * NOT returned in API responses - purely for observability.
 */
public class RetrievalDebugSnapshot {
    // Planning metadata
    private String originalQuery;
    private String normalizedQuery;
    private String intent;
    private String outputFormat;
    private List<String> entities;
    private List<String> keywordQueries;
    private String vectorQuery;
    private boolean lowConfidenceDetected;

    // Execution results
    private int keywordHits;
    private int vectorHits;
    private int mergedHits;
    private boolean keywordFallbackUsed;
    private boolean vectorFallbackUsed;
    private boolean neighborExpansionUsed;

    // Top results preview (debug only)
    private List<ResultPreview> topResults;

    /**
     * Brief preview of a merged result for debugging
     */
    public static class ResultPreview {
        public long chunkId;
        public Long docId;
        public Integer pageNo;
        public Double keywordScore;
        public Double vectorScore;
        public Double finalScore;
        public String textPreview;
        public String matchedByKeywordQuery;
        public boolean exactPhraseMatched;

        public ResultPreview(long chunkId, Long docId, Integer pageNo, Double keywordScore,
                           Double vectorScore, Double finalScore, String textPreview,
                           String matchedByKeywordQuery, boolean exactPhraseMatched) {
            this.chunkId = chunkId;
            this.docId = docId;
            this.pageNo = pageNo;
            this.keywordScore = keywordScore;
            this.vectorScore = vectorScore;
            this.finalScore = finalScore;
            this.textPreview = textPreview;
            this.matchedByKeywordQuery = matchedByKeywordQuery;
            this.exactPhraseMatched = exactPhraseMatched;
        }
    }

    // Constructors
    public RetrievalDebugSnapshot() {}

    public RetrievalDebugSnapshot(String originalQuery, String normalizedQuery, String intent,
                                 String outputFormat, List<String> entities,
                                 List<String> keywordQueries, String vectorQuery) {
        this.originalQuery = originalQuery;
        this.normalizedQuery = normalizedQuery;
        this.intent = intent;
        this.outputFormat = outputFormat;
        this.entities = entities;
        this.keywordQueries = keywordQueries;
        this.vectorQuery = vectorQuery;
        this.lowConfidenceDetected = false;
    }

    // Getters and Setters
    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public void setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
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

    public boolean isLowConfidenceDetected() {
        return lowConfidenceDetected;
    }

    public void setLowConfidenceDetected(boolean lowConfidenceDetected) {
        this.lowConfidenceDetected = lowConfidenceDetected;
    }

    public int getKeywordHits() {
        return keywordHits;
    }

    public void setKeywordHits(int keywordHits) {
        this.keywordHits = keywordHits;
    }

    public int getVectorHits() {
        return vectorHits;
    }

    public void setVectorHits(int vectorHits) {
        this.vectorHits = vectorHits;
    }

    public int getMergedHits() {
        return mergedHits;
    }

    public void setMergedHits(int mergedHits) {
        this.mergedHits = mergedHits;
    }

    public boolean isKeywordFallbackUsed() {
        return keywordFallbackUsed;
    }

    public void setKeywordFallbackUsed(boolean keywordFallbackUsed) {
        this.keywordFallbackUsed = keywordFallbackUsed;
    }

    public boolean isVectorFallbackUsed() {
        return vectorFallbackUsed;
    }

    public void setVectorFallbackUsed(boolean vectorFallbackUsed) {
        this.vectorFallbackUsed = vectorFallbackUsed;
    }

    public boolean isNeighborExpansionUsed() {
        return neighborExpansionUsed;
    }

    public void setNeighborExpansionUsed(boolean neighborExpansionUsed) {
        this.neighborExpansionUsed = neighborExpansionUsed;
    }

    public List<ResultPreview> getTopResults() {
        return topResults;
    }

    public void setTopResults(List<ResultPreview> topResults) {
        this.topResults = topResults;
    }

    @Override
    public String toString() {
        return "RetrievalDebugSnapshot{" +
                "originalQuery='" + originalQuery + '\'' +
                ", normalizedQuery='" + normalizedQuery + '\'' +
                ", intent='" + intent + '\'' +
                ", outputFormat='" + outputFormat + '\'' +
                ", entities=" + entities +
                ", keywordQueries=" + keywordQueries +
                ", vectorQuery='" + vectorQuery + '\'' +
                ", lowConfidenceDetected=" + lowConfidenceDetected +
                ", keywordHits=" + keywordHits +
                ", vectorHits=" + vectorHits +
                ", mergedHits=" + mergedHits +
                ", keywordFallbackUsed=" + keywordFallbackUsed +
                ", vectorFallbackUsed=" + vectorFallbackUsed +
                ", neighborExpansionUsed=" + neighborExpansionUsed +
                '}';
    }
}
