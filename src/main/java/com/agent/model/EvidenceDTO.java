package com.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data transfer object for evidence returned in API responses.
 * 
 * Contains essential evidence metadata for client consumption.
 * Simplified version of EvidenceChunk focused on response payload.
 */
public record EvidenceDTO(
    @JsonProperty("filename")
    String filename,
    
    @JsonProperty("sourceDocument")
    String sourceDocument,
    
    @JsonProperty("page")
    int page,
    
    @JsonProperty("chunkId")
    int chunkId,
    
    @JsonProperty("score")
    double score,
    
    @JsonProperty("excerpt")
    String excerpt
) {
    /**
     * Create an EvidenceDTO from an EvidenceChunk.
     * 
     * @param chunk The evidence chunk
     * @param score The relevance score (normalized)
     * @return EvidenceDTO
     */
    public static EvidenceDTO fromChunk(EvidenceChunk chunk, double score) {
        String docName = chunk.citations() != null ? chunk.citations() : String.format("Document %d", chunk.docId());
        return new EvidenceDTO(
            chunk.filename(),
            docName,
            chunk.pageNo() != null ? chunk.pageNo() : 0,
            chunk.chunkId() != null ? chunk.chunkId().intValue() : 0,
            score,
            truncateExcerpt(chunk.text(), 300)
        );
    }
    
    /**
     * Truncate excerpt to reasonable length with ellipsis if needed.
     * 
     * @param text Original text
     * @param maxLength Maximum length
     * @return Truncated text
     */
    private static String truncateExcerpt(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
