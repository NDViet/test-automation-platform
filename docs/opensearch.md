# OpenSearch in the Test Automation Platform

## Overview

OpenSearch serves as the **search and similarity layer** in the platform, complementing
PostgreSQL rather than replacing it. The two stores have distinct roles:

| Store | Technology | Primary Role |
|---|---|---|
| PostgreSQL | 17.x | Structured queries, flakiness scores, execution history, trends, JIRA metadata |
| OpenSearch | 3.5.0 | Full-text failure search, log aggregation, k-NN vector similarity detection |

The guiding architectural decision (ADR-003) is: *SQL for dashboards and trends,
OpenSearch for failure search and cross-team similarity.*

---

## Responsibilities

### 1. Full-Text Failure Search

Engineers need to search across every failure message and stack trace in the
organisation, not just within their own project. PostgreSQL `LIKE` and `ILIKE`
queries cannot scale to millions of rows with ranked relevance.

OpenSearch provides:

- **English-language analyzer** on `failure_message` — stemming, stop-word removal,
  relevance scoring
- **Unanalyzed stack-trace text** — exact-phrase matching for exception class names
  and file paths
- **Keyword fields** for faceting by `team_id`, `project_id`, `status`, `source_format`
- **Date-range queries** on `executed_at` (90-day rolling retention)

Use cases:

- "Show all failures containing `NullPointerException` in `CheckoutService` across all teams"
- "How many times has this exact error appeared in the last 30 days?"
- "Find all failures similar to this one before I open a JIRA ticket"

### 2. k-NN Vector Similarity Detection

When Claude classifies a failure it also generates a 1536-dimensional embedding of
the `failure_message` + `stack_trace` text. These embeddings are indexed in
OpenSearch using its k-NN plugin so that semantically similar failures — even if the
wording differs — can be found and grouped.

The similarity threshold of **0.85 cosine similarity** (L2 space, HNSW method)
triggers the deduplication rule in `IssueDecisionEngine`:

> Same root cause similarity > 0.85 → one incident ticket for all matching failures

Use cases:

- Group failures from Team A's Selenium suite with Team B's Playwright suite that
  trace back to the same application bug
- Surface a new failure as "already known" to avoid duplicate investigation
- Populate the *Similar Failures* card in the test-detail portal view

### 3. Log Aggregation

OpenSearch also receives structured logs from the Logstash pipeline, making it the
single search surface for both test-result events and service logs. This is an
operational concern (debugging ingestion failures, tracing slow parses) rather than
a test-quality feature.

### 4. Team-Scoped Aliases

Each team gets an **alias** over the shared `test_case_results` index filtered by
`term: { team_id: "<team>" }`. The portal BFF and API layer enforce this boundary so
teams can only search their own failures unless they hold org-admin permissions.

---

## Index Mapping

```json
// Index: test_case_results
{
  "mappings": {
    "properties": {
      "test_id":         { "type": "keyword" },
      "team_id":         { "type": "keyword" },
      "project_id":      { "type": "keyword" },
      "status":          { "type": "keyword" },
      "source_format":   { "type": "keyword" },
      "failure_message": { "type": "text", "analyzer": "english" },
      "stack_trace":     { "type": "text" },
      "tags":            { "type": "keyword" },
      "executed_at":     { "type": "date" },
      "duration_ms":     { "type": "long" },
      "failure_embedding": {
        "type":      "knn_vector",
        "dimension": 1536,
        "method":    { "name": "hnsw", "space_type": "l2" }
      }
    }
  }
}
```

The `failure_embedding` field is populated by `FailureEmbeddingService` in
`platform-ai` after Claude classifies the failure. Runs without an embedding (e.g.
passing tests) do not write to OpenSearch.

---

## Index: test_execution_logs (implemented)

This is a separate, fully implemented index for test run logs. Every SLF4J log
line produced during a test JVM invocation is shipped via Logstash and stored here.

**Index pattern:** `test_execution_logs-YYYY.MM.dd` (daily roll)

**Key fields:**

| Field | Type | Description |
|---|---|---|
| `run_id` | keyword | Suite-level — all tests in one `mvn test` share this |
| `test_id` | keyword | Per-scenario / per-method (`Feature#Scenario_name`) |
| `team_id` | keyword | Team slug from platform.properties |
| `project_id` | keyword | Project slug from platform.properties |
| `trace_id` | keyword | OTel trace ID |
| `step` | text | Current BDD step at log time |
| `level` | keyword | INFO / WARN / ERROR |
| `message` | text | Log message (full-text searchable) |
| `@timestamp` | date | Log timestamp |

**Query by run:**
```bash
curl -X GET http://localhost:9200/test_execution_logs-*/_search?pretty \
  -H 'Content-Type: application/json' \
  -d '{ "query": { "term": { "run_id": "<uuid>" } }, "sort": [{"@timestamp":"asc"}] }'
```

**REST API** (via platform-analytics):
```
GET /api/v1/logs/runs/{runId}              → all logs for a test run
GET /api/v1/logs/runs/{runId}?level=ERROR  → only errors/failures
GET /api/v1/logs/tests/{testId}            → logs for one scenario/method
GET /api/v1/logs/runs?teamId=X&projectId=Y → recent run IDs (last 7 days)
```

## Infrastructure

### Docker Compose (local dev)

```yaml
# docker-compose.yml
opensearch:
  image: opensearchproject/opensearch:3.5.0
  environment:
    - discovery.type=single-node
    - DISABLE_SECURITY_PLUGIN=true   # local only — no TLS/auth
  ports:
    - "9200:9200"
  volumes:
    - opensearch_data:/usr/share/opensearch/data
```

Security is intentionally disabled for local development. The production Kubernetes
deployment runs a 3-node cluster with TLS and role-based access.

### Kubernetes (production)

```
OpenSearch    StatefulSet, PVC 200 GiB, 3-node cluster
```

The 3-node setup provides:
- Shard redundancy (1 replica per shard)
- No single point of failure for search availability
- Horizontal scaling for write throughput during CI peaks

### Data Retention

| Data | Retention |
|---|---|
| `test_case_results` index | 90 days (ILM rollover policy) |
| Embeddings | Expire with parent document |
| PostgreSQL test history | 12 months |

An Index Lifecycle Management (ILM) policy rolls the index at 90 days or 50 GiB,
whichever comes first.

---

## Planned Implementation (Platform Phase 5)

> **Current status:** OpenSearch infrastructure is running (Docker, port 9200).
> No application code interacts with it yet. The items below are the Phase 5
> deliverables ("AI Failure Intelligence, Weeks 15–17").

### Classes to implement in `platform-ai`

```
platform-ai/src/main/java/com/platform/ai/
├── similarity/
│   ├── FailureEmbeddingService.java   # Vectorize failure messages via Claude Embeddings API
│   └── SimilarFailureFinder.java      # k-NN search in OpenSearch — returns top-N similar failures
```

#### `FailureEmbeddingService`

Responsibilities:
1. Accept a `FailureAnalysis` result from `FailureClassificationService`
2. Build the embedding input: `failure_message + "\n" + first_10_lines_of_stack_trace`
3. Call the Claude Embeddings API to get a 1536-dim `float[]`
4. POST the document to OpenSearch `test_case_results` index
5. Store the OpenSearch document ID back on the `FailureAnalysis` entity for dedup

#### `SimilarFailureFinder`

Responsibilities:
1. Accept an embedding vector and a `team_id` (for scope — use `null` for cross-team)
2. Execute an OpenSearch k-NN query against `failure_embedding`
3. Filter by `executed_at >= now - 30d` to avoid ancient noise
4. Return `List<SimilarFailure>` (test_id, similarity_score, failure_message excerpt)
5. `IssueDecisionEngine` calls this to decide whether to open a new ticket or link to
   an existing one

### REST endpoint (to be added in `platform-ai`)

```
GET /api/v1/projects/{projectId}/results/{resultId}/similar
```

Returns the top-5 most similar failures across the organisation, used by the
portal's *Similar Failures* card.

### Ingestion flow (once implemented)

```
Kafka: test.results.raw
        │
        ▼
AnalysisEventConsumer (platform-ai)
        │
        ├── FailureClassificationService  →  FailureAnalysis (PostgreSQL)
        │                                         │
        │                                         ▼
        └── FailureEmbeddingService  →  POST test_case_results (OpenSearch)
                                                  │
                                                  ▼
                                    IssueDecisionEngine.findSimilar()
                                    → deduplicate JIRA tickets
```

---

## Interacting with OpenSearch Directly

During development you can query OpenSearch directly at `http://localhost:9200`.

```bash
# Health check
curl http://localhost:9200/_cluster/health?pretty

# List indices
curl http://localhost:9200/_cat/indices?v

# Create the test_case_results index (run once)
curl -X PUT http://localhost:9200/test_case_results \
  -H 'Content-Type: application/json' \
  -d @infrastructure/opensearch/mappings/test_case_results.json

# Search for failures containing a keyword
curl -X GET http://localhost:9200/test_case_results/_search?pretty \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "match": {
        "failure_message": "NullPointerException CheckoutService"
      }
    }
  }'

# k-NN similarity query (once embeddings are indexed)
curl -X GET http://localhost:9200/test_case_results/_search?pretty \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "knn": {
        "failure_embedding": {
          "vector": [0.01, -0.23, ...],
          "k": 5
        }
      }
    }
  }'
```

---

## OpenSearch vs PostgreSQL — When to Use Which

| Query type | Use |
|---|---|
| Test counts, pass rates, trends | PostgreSQL |
| Flakiness scores, JIRA ticket lookup | PostgreSQL |
| "Find failures containing this exception" | OpenSearch |
| "Find failures similar to this one" | OpenSearch (k-NN) |
| Execution history for a specific test | PostgreSQL |
| Log aggregation / debugging platform itself | OpenSearch |
| Quality gate evaluation | PostgreSQL |
| Release report generation | PostgreSQL |

As a rule of thumb: if the query uses `WHERE`, `GROUP BY`, or `JOIN` on known
columns, use PostgreSQL. If it involves free-text matching or semantic similarity,
use OpenSearch.
