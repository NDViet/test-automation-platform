package com.platform.agent.node.tools;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients for analytics endpoints used by InsightNode. All methods return raw JSON strings so
 * Claude can interpret the data.
 */
@Component
public class PlatformInsightTools {

  private static final Logger log = LoggerFactory.getLogger(PlatformInsightTools.class);
  private static final String UNAVAILABLE = "{\"error\":\"analytics service unavailable\"}";

  private final RestClient restClient;

  public PlatformInsightTools(
      @Value("${platform.analytics.url:http://localhost:8082}") String analyticsUrl) {
    this.restClient =
        RestClient.builder()
            .baseUrl(analyticsUrl)
            .defaultHeader("Accept", "application/json")
            .build();
  }

  /** GET /api/v1/analytics/{projectId}/trends?days=7 */
  public String getTrends(UUID projectId, int days) {
    try {
      return restClient
          .get()
          .uri("/api/v1/analytics/{projectId}/trends?days={days}", projectId, days)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      log.warn("getTrends failed for {}: {}", projectId, e.getMessage());
      return UNAVAILABLE;
    }
  }

  /** GET /api/v1/analytics/{projectId}/flakiness?limit=20 */
  public String getFlakinessLeaderboard(UUID projectId, int limit) {
    try {
      return restClient
          .get()
          .uri("/api/v1/analytics/{projectId}/flakiness?limit={limit}", projectId, limit)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      log.warn("getFlakinessLeaderboard failed for {}: {}", projectId, e.getMessage());
      return UNAVAILABLE;
    }
  }

  /** GET /api/v1/analytics/{projectId}/quality-gate */
  public String getQualityGate(UUID projectId) {
    try {
      return restClient
          .get()
          .uri("/api/v1/analytics/{projectId}/quality-gate", projectId)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      log.warn("getQualityGate failed for {}: {}", projectId, e.getMessage());
      return UNAVAILABLE;
    }
  }
}
