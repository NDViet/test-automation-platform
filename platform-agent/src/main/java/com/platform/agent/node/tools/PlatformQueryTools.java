package com.platform.agent.node.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Client for platform-analytics APIs used by agent nodes.
 * Provides Test Impact Analysis queries and other platform data lookups.
 */
@Component
public class PlatformQueryTools {

    private static final Logger log = LoggerFactory.getLogger(PlatformQueryTools.class);
    private static final String ANALYTICS_UNAVAILABLE_RESPONSE =
            "{\"risk\":\"UNKNOWN\",\"selectedTests\":[],\"reduction\":0,\"message\":\"Analytics service unavailable\"}";

    private final String analyticsUrl;
    private final RestClient restClient;

    public PlatformQueryTools(@Value("${platform.analytics.url:http://localhost:8082}") String analyticsUrl) {
        this.analyticsUrl = analyticsUrl;
        this.restClient   = RestClient.builder()
                .baseUrl(analyticsUrl)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Calls platform-analytics Test Impact Analysis endpoint.
     *
     * GET {analyticsUrl}/api/v1/analytics/{projectId}/impact?changedFiles=file1&changedFiles=file2...
     *
     * @param projectId    platform project UUID
     * @param changedFiles list of changed file paths (relative, e.g. "src/main/java/com/Foo.java")
     * @return raw JSON string from platform-analytics, or fallback JSON if analytics is unavailable
     */
    public String getTiaImpact(UUID projectId, List<String> changedFiles) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromPath("/api/v1/analytics/{projectId}/impact");
            if (changedFiles != null && !changedFiles.isEmpty()) {
                changedFiles.forEach(f -> uriBuilder.queryParam("changedFiles", f));
            }
            URI uri = uriBuilder.buildAndExpand(projectId).toUri();

            log.debug("PlatformQueryTools: TIA request project={} changedFiles={}", projectId,
                    changedFiles != null ? changedFiles.size() : 0);

            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

        } catch (Exception e) {
            log.warn("PlatformQueryTools: analytics service unavailable at '{}', returning fallback. Cause: {}",
                    analyticsUrl, e.getMessage());
            return ANALYTICS_UNAVAILABLE_RESPONSE;
        }
    }
}
