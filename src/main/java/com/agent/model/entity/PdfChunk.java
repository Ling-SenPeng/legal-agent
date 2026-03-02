package com.agent.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.Map;

/**
 * Entity representing a chunk of a PDF document with embedding vector.
 * Embedding is stored as a string representation of the vector (e.g., "[0.1,0.2,...,0.15]")
 * to avoid PGvector constructor issues and simplify serialization.
 */
@Table("pdf_chunks")
public class PdfChunk {
    @Id
    private Long id;
    
    private Long docId;
    
    private Integer pageNo;
    
    private Integer chunkIndex;
    
    private String text;
    
    @Column("embedding")
    private String embedding;
    
    private Map<String, Object> meta;
    
    private Instant createdAt;

    // Constructors
    public PdfChunk() {
    }

    public PdfChunk(Long docId, Integer pageNo, Integer chunkIndex, String text, String embedding) {
        this.docId = docId;
        this.pageNo = pageNo;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.embedding = embedding;
        this.createdAt = Instant.now();
        this.meta = Map.of();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
