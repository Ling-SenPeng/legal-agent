# Legal Agent Debug UI Guide

A lightweight, browser-based debug interface for testing the `/agent/query` endpoint.

## Quick Start

### 1. Start the legal-agent backend

```bash
mvn spring-boot:run
```

The server starts on `http://localhost:8080` by default.

### 2. Open the debug UI

Navigate to:
```
http://localhost:8080/debug.html
```

That's it! No additional dependencies or setup needed.

## Features

### Input Form

- **API Base URL**: Configure the endpoint (defaults to `http://localhost:8080`)
- **Question**: Enter your legal query (required)
- **Top K**: Number of evidence chunks to retrieve (1-100, default: 5)
- **Filters**: Optional JSON filters or `null`

The **Request Preview** updates in real-time as you type, showing the exact JSON that will be sent.

### Query Execution

Click **Run Query** to send the request. The UI shows:

1. **Status Badge**: Loading → Success ✓ or Error ✗
2. **Processing Time**: How long the query took (ms)
3. **Response Tabs**:
   - **Answer**: Full answer with verification status
   - **Evidence**: Structured evidence cards
   - **Raw JSON**: Complete response payload

### Evidence Display

Each evidence card shows:

- **Filename**: Source document name
- **Page**: Page number in source document
- **Chunk ID**: Unique chunk identifier
- **Score**: Relevance score (0-1 range, higher = more relevant)
- **Source Document**: Citation or document reference
- **Excerpt**: Text snippet with context (300 char limit in API, expandable in UI)

#### Empty Evidence Warning

If no evidence is returned, you'll see:
```
⚠️ No evidence returned. This may indicate response mapping is missing.
```

This typically means:
- The retrieval phase found no relevant documents
- There's a mapping issue in the response serialization
- The question doesn't match available documents well

### Verification Report

After the answer, a verification section shows:

```
✓ Verification Passed
────────────────────
Notes: All citations found in evidence
```

Or if verification failed:

```
✗ Verification Failed
────────────────────
Missing citations:
  - "paragraph 2 of the statute"
  - "Johnson v. Smith (2015)"
```

### Copy cURL Command

Click **Copy curl** to copy a ready-to-use curl command:

```bash
curl -X POST "http://localhost:8080/agent/query" \
  -H "Content-Type: application/json" \
  -d '{"question":"What is mortgage reimbursement?","topK":5,"filters":null}'
```

Useful for:
- Sharing test cases
- Debugging with command-line tools
- CI/CD pipelines
- Automation scripts

## Debug Tips

### 1. Check Request Format

Before running, review the **Request Preview** panel. Ensure:
- `question` is not empty
- `topK` is a positive integer
- `filters` is valid JSON object or `null`

### 2. View Raw Response

Switch to the **Raw JSON** tab to see the complete response structure. Useful for:
- Validating response schema
- Checking null fields
- Debugging mapping issues

### 3. Inspect Evidence Quality

Look for:
- **Score distribution**: Are scores > 0.5? Low scores indicate poor relevance.
- **Excerpt clarity**: Does the text actually answer your question?
- **Source tracking**: Can you find the source document in your knowledge base?

### 4. Diagnose Empty Evidence

If evidence is empty:

1. Check the API logs:
   ```bash
   # Terminal where spring-boot:run is executing
   tail -f target/logs/legal-agent.log
   ```

2. Look for retrieval errors:
   ```
   [ERROR] Retrieval failed: ...
   [WARN] No relevant documents found for query
   ```

3. Try with simpler queries that match document keywords

### 5. Test with Different Filters

Try various filter combinations:

```json
// Case-specific
{"caseId": "2024-001"}

// Document type
{"documentType": "agreement"}

// Time range
{"startDate": "2024-01-01", "endDate": "2024-12-31"}

// Multiple conditions
{"documentType": "contract", "status": "active"}
```

## Example Queries

### Reimbursement Analysis
```
Question: What is post-separation mortgage payment reimbursement?
TopK: 5
Filters: null
```

### Case-Specific
```
Question: What are the property distribution rules in California?
TopK: 10
Filters: {"caseId": "2024-001"}
```

### Document Search
```
Question: loan agreements
TopK: 3
Filters: {"documentType": "agreement"}
```

## UI Behavior

### Loading States

- Button disabled during query execution
- Spinner in status badge indicates processing
- Response container replaces empty state on success

### Error Handling

Network errors show clearly:
```
✗ Error
────────────────────
HTTP 500: Internal Server Error
```

Input validation errors:
```
✗ Error
────────────────────
Question is required
```

### Answer Formatting

The answer preserves:
- Whitespace and newlines
- Indentation and lists
- Code blocks or citations

This is useful for:
- Multi-paragraph legal analyses
- Numbered lists
- Formatted case law references

## Architecture

The debug UI is a **static HTML file** served by Spring Boot:

```
src/main/resources/static/debug.html
                         └─ Single-page app
                            - No build required
                            - No dependencies
                            - ~500 lines HTML/CSS/JS
```

Benefits:
- ✅ Runs immediately when Spring Boot starts
- ✅ No additional web server needed
- ✅ Works across network (use external IP instead of localhost)
- ✅ Easy to modify and extend
- ✅ Zero latency for static file serving

## Customization

### Change Default Port

Edit `application.yml`:
```yaml
server:
  port: 9000
```

Then access at: `http://localhost:9000/debug.html`

### Add Custom Filters

Edit the filters textarea HTML to include preset options:

```html
<datalist id="filterPresets">
  <option value='{"caseId": "2024-001"}'>Case 2024-001</option>
  <option value='{"documentType": "mortgage"}'>Mortgage Documents</option>
</datalist>
```

### Extend Response Display

The response is stored in `window.lastResponse`. Add custom display logic:

```javascript
// In the script section after displayResponse()
if (data.customField) {
    // Custom handling
}
```

## Troubleshooting

### "Cannot reach API"

- [ ] Backend running? Check: `curl http://localhost:8080/agent/health`
- [ ] Correct port? Edit "API Base URL" field
- [ ] Firewall blocking? Try from another machine on network

### "Evidence is empty"

- [ ] Documents loaded in database? Check: `SELECT COUNT(*) FROM documents;`
- [ ] Query too specific? Try broader keywords
- [ ] Retrieval working? Check backend logs for [RETRIEVAL] markers

### "Verification failed"

- [ ] Citations don't match evidence exactly? LLM may paraphrase.
- [ ] Check the raw JSON tab to compare answer vs evidence text
- [ ] This is normal—verification helps catch discrepancies

### "Processing takes forever"

- [ ] High topK value? Try reducing from 10 to 5
- [ ] Large knowledge base? Network delay expected
- [ ] Check backend logs for slow queries: `[SLOW_QUERY]`

## Integration

The debug UI is dev-only and isolated:

- ✅ Can run alongside production API
- ✅ Doesn't modify backend state
- ✅ No authentication needed (dev mode)
- ✅ Easy to remove: just delete `src/main/resources/static/debug.html`

For production, serve the frontend separately:
- React/Vue SPA on different domain
- Mobile app via API gateway
- Headless integration with document management systems

## Next Steps

Once you've debugged queries:

1. **Integrate into frontend**: Copy the UI logic to your main app
2. **Add authentication**: Wrap API calls with auth tokens
3. **Customize styling**: Match your brand guidelines
4. **Add features**: Export results, batch queries, etc.

---

**Questions?** Check the [main README.md](../README.md) or review [DEVELOPMENT.md](../DEVELOPMENT.md) for backend architecture details.
