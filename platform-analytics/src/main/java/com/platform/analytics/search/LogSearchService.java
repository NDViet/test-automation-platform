package com.platform.analytics.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries the {@code test_execution_logs-*} OpenSearch index to retrieve log
 * events by {@code run_id} or {@code test_id}.
 *
 * <p>Uses Spring's {@link RestClient} over HTTP — no separate OpenSearch client
 * library required. OpenSearch's REST API is standard JSON.</p>
 *
 * <p>On startup, applies the {@code test_execution_logs} index template if it
 * does not already exist, so the index is always created with the correct
 * field mappings.</p>
 */
@Service
public class LogSearchService {

    private static final Logger log = LoggerFactory.getLogger(LogSearchService.class);

    private static final String INDEX_PATTERN  = "test_execution_logs-*";
    private static final String TEMPLATE_NAME  = "test_execution_logs";
    private static final String TEMPLATE_PATH  = "opensearch/templates/test_execution_logs.json";
    private static final int    DEFAULT_SIZE   = 500;
    private static final int    MAX_SIZE       = 2000;

    private final RestClient    restClient;
    private final ObjectMapper  mapper;

    public LogSearchService(
            @Value("${opensearch.host:localhost}") String host,
            @Value("${opensearch.port:9200}")      int    port,
            ObjectMapper mapper) {
        this.restClient = RestClient.builder()
                .baseUrl("http://" + host + ":" + port)
                .build();
        this.mapper = mapper;
    }

    // ── Startup — apply index template ────────────────────────────────────────

    @PostConstruct
    void ensureIndexTemplate() {
        try {
            String existing = restClient.get()
                    .uri("/_index_template/" + TEMPLATE_NAME)
                    .retrieve()
                    .body(String.class);
            if (existing != null && existing.contains(TEMPLATE_NAME)) {
                log.debug("[LogSearch] Index template '{}' already exists", TEMPLATE_NAME);
                return;
            }
        } catch (RestClientException ignored) {
            // 404 → template does not exist yet
        }

        try {
            String templateJson = new ClassPathResource(TEMPLATE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
            restClient.put()
                    .uri("/_index_template/" + TEMPLATE_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(templateJson)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[LogSearch] Applied index template '{}'", TEMPLATE_NAME);
        } catch (Exception e) {
            log.warn("[LogSearch] Could not apply index template '{}' (non-fatal): {}",
                    TEMPLATE_NAME, e.getMessage());
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns up to {@code size} log entries for a given {@code run_id},
     * sorted oldest-first so the log reads in chronological order.
     *
     * @param runId  the run ID set as MDC {@code run_id} by RunContext
     * @param size   max entries to return (capped at {@value MAX_SIZE})
     * @param level  optional level filter (INFO, WARN, ERROR) — pass null for all
     */
    public List<LogEntry> findByRunId(String runId, Integer size, String level) {
        String query = buildTermQuery("run_id", runId, resolveSize(size), level);
        return search(query);
    }

    /**
     * Returns up to {@code size} log entries for a specific test case, identified
     * by its {@code test_id} (e.g. {@code login.feature#Login_with_valid_credentials}).
     *
     * @param testId  the per-scenario/per-method test ID
     * @param size    max entries to return (capped at {@value MAX_SIZE})
     * @param level   optional level filter — pass null for all
     */
    public List<LogEntry> findByTestId(String testId, Integer size, String level) {
        String query = buildTermQuery("test_id", testId, resolveSize(size), level);
        return search(query);
    }

    /**
     * Returns all distinct {@code run_id} values seen in the last {@code days} days,
     * for the given {@code teamId} and {@code projectId}. Useful for building a
     * "recent runs" list in the portal.
     */
    public List<String> listRecentRunIds(String teamId, String projectId, int days) {
        String query = """
                {
                  "size": 0,
                  "query": {
                    "bool": {
                      "must": [
                        { "term":  { "team_id":    "%s" } },
                        { "term":  { "project_id": "%s" } },
                        { "range": { "@timestamp":  { "gte": "now-%dd/d" } } }
                      ]
                    }
                  },
                  "aggs": {
                    "run_ids": {
                      "terms": { "field": "run_id", "size": 100 }
                    }
                  }
                }
                """.formatted(teamId, projectId, days);

        try {
            String response = restClient.post()
                    .uri("/" + INDEX_PATTERN + "/_search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(query)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(response);
            List<String> runIds = new ArrayList<>();
            root.path("aggregations").path("run_ids").path("buckets")
                    .forEach(bucket -> runIds.add(bucket.path("key").asText()));
            return runIds;
        } catch (Exception e) {
            log.warn("[LogSearch] listRecentRunIds failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<LogEntry> search(String queryBody) {
        try {
            String response = restClient.post()
                    .uri("/" + INDEX_PATTERN + "/_search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(queryBody)
                    .retrieve()
                    .body(String.class);

            return parseHits(response);
        } catch (RestClientException e) {
            log.warn("[LogSearch] OpenSearch query failed: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("[LogSearch] Unexpected error during log search", e);
            return List.of();
        }
    }

    private List<LogEntry> parseHits(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode hits = root.path("hits").path("hits");
        List<LogEntry> entries = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            entries.add(mapper.treeToValue(source, LogEntry.class));
        }
        return entries;
    }

    /**
     * Builds a term-query JSON body, optionally filtered by log level.
     * Results are sorted by {@code @timestamp} ascending (chronological order).
     */
    private String buildTermQuery(String field, String value, int size, String level) {
        String levelFilter = (level != null && !level.isBlank())
                ? """
                  , { "term": { "level": "%s" } }
                  """.formatted(level.toUpperCase())
                : "";

        return """
                {
                  "size": %d,
                  "sort": [ { "@timestamp": { "order": "asc" } } ],
                  "query": {
                    "bool": {
                      "must": [
                        { "term": { "%s": "%s" } }
                        %s
                      ]
                    }
                  }
                }
                """.formatted(size, field, value, levelFilter);
    }

    private int resolveSize(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_SIZE;
        return Math.min(requested, MAX_SIZE);
    }
}
