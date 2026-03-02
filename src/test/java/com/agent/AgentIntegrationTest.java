package com.agent;

import com.agent.model.AgentQueryRequest;
import com.agent.model.AgentQueryResponse;
import com.agent.model.EvidenceChunk;
import com.pgvector.PGvector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the evidence-grounded agent using Testcontainers PostgreSQL with pgvector.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AgentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("legal_agent")
        .withUsername("postgres")
        .withPassword("postgres");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("openai.api-key", () -> "test-key");
    }

    @BeforeEach
    void setup() {
        // Enable pgvector extension
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

        // Create schema
        jdbcTemplate.execute("""
            DROP TABLE IF EXISTS pdf_chunks;
            DROP TABLE IF EXISTS pdf_documents;
            """);

        jdbcTemplate.execute("""
            CREATE TABLE pdf_documents (
                id BIGSERIAL PRIMARY KEY,
                file_name VARCHAR(255) NOT NULL,
                file_path TEXT NOT NULL,
                sha256 CHAR(64),
                file_size BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'DONE',
                error_msg TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                processed_at TIMESTAMP
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE pdf_chunks (
                id BIGSERIAL PRIMARY KEY,
                doc_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
                page_no INT NOT NULL,
                chunk_index INT NOT NULL DEFAULT 0,
                text TEXT NOT NULL,
                embedding vector(1536),
                meta JSONB NOT NULL DEFAULT '{}'::jsonb,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (doc_id, page_no, chunk_index)
            )
            """);

        jdbcTemplate.execute("""
            CREATE INDEX idx_pdf_chunks_embedding 
            ON pdf_chunks USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 10)
            """);

        insertTestData();
    }

    /**
     * Insert sample test data with embeddings.
     */
    private void insertTestData() {
        // Insert document
        Long docId = jdbcTemplate.queryForObject(
            "INSERT INTO pdf_documents (file_name, file_path, file_size, status) " +
            "VALUES ('contract.pdf', '/docs/contract.pdf', 50000, 'DONE') RETURNING id",
            Long.class
        );

        assertNotNull(docId, "Document creation failed");

        // Insert chunks with embeddings (using small fake embeddings for MVP testing)
        String embedding1 = generateFakeEmbeddingVector();
        String embedding2 = generateFakeEmbeddingVector();
        String embedding3 = generateFakeEmbeddingVector();

        jdbcTemplate.update(
            "INSERT INTO pdf_chunks (doc_id, page_no, chunk_index, text, embedding) VALUES (?, ?, ?, ?, ?::vector)",
            docId, 1, 0,
            "John Smith agreed to pay $10,000 on January 15, 2023, as per the contract terms.",
            embedding1
        );

        jdbcTemplate.update(
            "INSERT INTO pdf_chunks (doc_id, page_no, chunk_index, text, embedding) VALUES (?, ?, ?, ?, ?::vector)",
            docId, 2, 0,
            "Payment terms: 50% upfront, 50% upon completion. The buyer received the full documentation.",
            embedding2
        );

        jdbcTemplate.update(
            "INSERT INTO pdf_chunks (doc_id, page_no, chunk_index, text, embedding) VALUES (?, ?, ?, ?, ?::vector)",
            docId, 3, 0,
            "Both parties signed the agreement on February 1, 2023. Witness: Jane Doe.",
            embedding3
        );
    }

    /**
     * Generate a fake embedding vector string (1536 dimensions for testing).
     * Format: "[d1,d2,d3,...]"
     */
    private String generateFakeEmbeddingVector() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.4f", Math.random() * 0.1));
        }
        sb.append("]");
        return sb.toString();
    }

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/agent/health"))
            .andExpect(status().isOk());
    }

    @Test
    void testAgentQueryWithEvidenceRetrieval() throws Exception {
        // Note: This test will fail if OpenAI API key is not provided or is invalid.
        // For MVP, it demonstrates the endpoint structure.
        
        AgentQueryRequest request = new AgentQueryRequest(
            "What was the payment amount?",
            3,
            null
        );

        MvcResult result = mockMvc.perform(post("/agent/query")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        AgentQueryResponse response = objectMapper.readValue(responseContent, AgentQueryResponse.class);

        assertNotNull(response);
        assertNotNull(response.answer());
        assertFalse(response.evidence().isEmpty(), "Evidence should not be empty");
        assertNotNull(response.verification());
        assertTrue(response.processingTimeMs() >= 0);
    }

    @Test
    void testAgentQueryWithInvalidRequest() throws Exception {
        AgentQueryRequest request = new AgentQueryRequest(
            "",  // Empty question should be invalid
            5,
            null
        );

        mockMvc.perform(post("/agent/query")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testChunkRetrieval() {
        // Test that chunks were inserted correctly
        Integer chunkCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pdf_chunks",
            Integer.class
        );

        assertEquals(3, chunkCount, "Should have 3 test chunks");
    }

    @Test
    void testCitationFormat() throws Exception {
        // Verify that citations can be parsed from text
        String citation = "[CIT doc=1 chunk=5 p=10-11]";
        assertTrue(citation.matches(".*\\[CIT\\s+doc=\\d+\\s+chunk=\\d+\\s+p=\\d+-\\d+\\].*"));
    }
}
