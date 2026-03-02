#!/bin/bash

# ============================================================================
# Legal Agent API Examples
# ============================================================================
# Example curl commands to interact with the evidence-grounded agent API.
# 
# Prerequisites:
# 1. Agent running on http://localhost:8080
# 2. OpenAI API key configured
# 3. PostgreSQL with sample data loaded

set -e

API_URL="http://localhost:8080/agent"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Legal Agent API Examples ===${NC}\n"

# ============================================================================
# 1. Health Check
# ============================================================================
echo -e "${GREEN}1. Health Check${NC}"
curl -X GET "${API_URL}/health"
echo -e "\n"

# ============================================================================
# 2. Simple Query
# ============================================================================
echo -e "${GREEN}2. Query: What was the payment amount?${NC}"
curl -X POST "${API_URL}/query" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What was the payment amount?",
    "topK": 8,
    "filters": null
  }' | jq .
echo -e "\n"

# ============================================================================
# 3. Query with Fewer Results
# ============================================================================
echo -e "${GREEN}3. Query with topK=3${NC}"
curl -X POST "${API_URL}/query" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "When was the agreement signed?",
    "topK": 3,
    "filters": null
  }' | jq .
echo -e "\n"

# ============================================================================
# 4. Extract Evidence Only
# ============================================================================
echo -e "${GREEN}4. Extract evidence for a specific question${NC}"
curl -X POST "${API_URL}/query" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Who was the witness to the agreement?",
    "topK": 5,
    "filters": null
  }' | jq '.evidence[]'
echo -e "\n"

# ============================================================================
# 5. Complex Query with Multiple Factual Elements
# ============================================================================
echo -e "${GREEN}5. Complex query${NC}"
curl -X POST "${API_URL}/query" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are the payment terms, amounts, and dates mentioned in the contract?",
    "topK": 10,
    "filters": null
  }' | jq .
echo -e "\n"

# ============================================================================
# Pretty-print helpers
# ============================================================================

echo -e "${BLUE}=== Pretty Print Examples ===${NC}\n"

# Extract just the answer
echo -e "${GREEN}Extract answer only:${NC}"
curl -s -X POST "${API_URL}/query" \
  -H "Content-Type: application/json" \
  -d '{"question": "What was paid?", "topK": 5, "filters": null}' | jq '.answer'
echo -e "\n"

# Extract verification status
echo -e "${GREEN}Verification status:${NC}"
curl -s -X POST "${API_URL}/query" \
  -H "Content-Type: application/json" \
  -d '{"question": "List the key facts.", "topK": 8, "filters": null}' | jq '.verification'
echo -e "\n"

# Extract evidence with citations
echo -e "${GREEN}Evidence citations only:${NC}"
curl -s -X POST "${API_URL}/query" \
  -H "Content-Type: application/json" \
  -d '{"question": "What happened?", "topK": 5, "filters": null}' | jq '.evidence[] | {text: .text, citations: .citations, similarity: .similarity}'
echo -e "\n"

# Extract processing time
echo -e "${GREEN}Processing time:${NC}"
curl -s -X POST "${API_URL}/query" \
  -H "Content-Type: application/json" \
  -d '{"question": "Key details?", "topK": 5, "filters": null}' | jq '.processingTimeMs'
echo -e "\n"

echo -e "${BLUE}=== End of Examples ===${NC}"
