package com.agent.service.mapper;

import com.agent.model.EvidenceChunk;
import com.agent.model.EvidenceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for mapping EvidenceChunk records to EvidenceDTO for API responses.
 * 
 * Transforms internal evidence representation into client-friendly DTOs
 * and selects top-ranked chunks for response payload.
 */
@Service
public class EvidenceMapper {
    private static final Logger logger = LoggerFactory.getLogger(EvidenceMapper.class);
    
    private static final int MAX_EVIDENCE_FOR_RESPONSE = 5;
    
    /**
     * Map evidence chunks to DTOs for response, selecting top K ranked chunks.
     * 
     * @param chunks List of evidence chunks from retrieval
     * @param topK Maximum number of chunks to include in response
     * @return List of EvidenceDTO objects, sorted by relevance score
     */
    public List<EvidenceDTO> mapToDto(List<EvidenceChunk> chunks, int topK) {
        if (chunks == null || chunks.isEmpty()) {
            logger.debug("[EVIDENCE_MAPPER] No chunks to map");
            return List.of();
        }
        
        int limit = Math.min(topK, MAX_EVIDENCE_FOR_RESPONSE);
        
        List<EvidenceDTO> dtos = chunks.stream()
            .limit(limit)
            .map(chunk -> {
                // Use finalScore if available (hybrid fusion), else fallback to vector/keyword score
                double score = chunk.finalScore() != null ? chunk.finalScore() :
                           (chunk.vectorScore() != null ? chunk.vectorScore() :
                           (chunk.keywordScore() != null ? chunk.keywordScore() : 0.5));
                return EvidenceDTO.fromChunk(chunk, score);
            })
            .toList();
        
        logger.info("[EVIDENCE_MAPPER] Mapped {} chunks to {} DTOs for response", 
            chunks.size(), dtos.size());
        
        return dtos;
    }
    
    /**
     * Map evidence chunks using default limit (top 5).
     * 
     * @param chunks List of evidence chunks
     * @return List of EvidenceDTO objects
     */
    public List<EvidenceDTO> mapToDto(List<EvidenceChunk> chunks) {
        return mapToDto(chunks, MAX_EVIDENCE_FOR_RESPONSE);
    }
}
