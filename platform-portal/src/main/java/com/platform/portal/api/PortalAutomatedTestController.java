package com.platform.portal.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Portal BFF — proxies automated-test analytics to platform-analytics. */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/automated-tests")
@Tag(name = "Portal Automated Tests", description = "Automated test catalog and execution trends")
public class PortalAutomatedTestController {

  private final RestClient analyticsClient;

  public PortalAutomatedTestController(@Qualifier("analyticsClient") RestClient analyticsClient) {
    this.analyticsClient = analyticsClient;
  }

  @GetMapping
  @Operation(summary = "List automated tests with pass/fail stats")
  public Object list(
      @PathVariable String projectId,
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "ALL") String status,
      @RequestParam(required = false) List<String> tags,
      @RequestParam(required = false) List<String> browsers,
      @RequestParam(required = false) List<String> annotationTypes,
      @RequestParam(required = false) String labelKey,
      @RequestParam(required = false) String labelValue,
      @RequestParam(required = false) String specFile) {

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromPath("/api/v1/analytics/{projectId}/automated-tests")
            .queryParam("days", days)
            .queryParam("status", status)
            .queryParamIfPresent("search", Optional.ofNullable(search))
            .queryParamIfPresent("labelKey", Optional.ofNullable(labelKey))
            .queryParamIfPresent("labelValue", Optional.ofNullable(labelValue))
            .queryParamIfPresent("specFile", Optional.ofNullable(specFile));

    if (tags != null) tags.forEach(t -> builder.queryParam("tags", t));
    if (browsers != null) browsers.forEach(b -> builder.queryParam("browsers", b));
    if (annotationTypes != null)
      annotationTypes.forEach(a -> builder.queryParam("annotationTypes", a));

    return proxy(builder.buildAndExpand(projectId).toUriString());
  }

  @GetMapping("/tags")
  @Operation(summary = "Distinct tags seen in the project within the time window")
  public Object tags(@PathVariable String projectId, @RequestParam(defaultValue = "30") int days) {
    return proxy(discoveryUri(projectId, "tags", days));
  }

  @GetMapping("/browsers")
  @Operation(
      summary = "Distinct Playwright project names (browsers/devices) within the time window")
  public Object browsers(
      @PathVariable String projectId, @RequestParam(defaultValue = "30") int days) {
    return proxy(discoveryUri(projectId, "browsers", days));
  }

  @GetMapping("/annotation-types")
  @Operation(summary = "Distinct annotation types (fixme, slow, fail, …) within the time window")
  public Object annotationTypes(
      @PathVariable String projectId, @RequestParam(defaultValue = "30") int days) {
    return proxy(discoveryUri(projectId, "annotation-types", days));
  }

  @GetMapping("/label-keys")
  @Operation(summary = "Distinct label keys (owner, jira, team, …) within the time window")
  public Object labelKeys(
      @PathVariable String projectId, @RequestParam(defaultValue = "30") int days) {
    return proxy(discoveryUri(projectId, "label-keys", days));
  }

  @GetMapping("/label-values")
  @Operation(summary = "Distinct values for a specific label key within the time window")
  public Object labelValues(
      @PathVariable String projectId,
      @RequestParam(defaultValue = "30") int days,
      @RequestParam String labelKey) {
    String uri =
        UriComponentsBuilder.fromPath("/api/v1/analytics/{projectId}/automated-tests/label-values")
            .queryParam("days", days)
            .queryParam("labelKey", labelKey)
            .buildAndExpand(projectId)
            .toUriString();
    return proxy(uri);
  }

  @GetMapping("/detail")
  @Operation(summary = "Daily execution trend + recent runs for a single test")
  public Object detail(
      @PathVariable String projectId,
      @RequestParam String testId,
      @RequestParam(defaultValue = "30") int days) {
    String uri =
        UriComponentsBuilder.fromPath("/api/v1/analytics/{projectId}/automated-tests/detail")
            .queryParam("testId", testId)
            .queryParam("days", days)
            .buildAndExpand(projectId)
            .toUriString();
    return proxy(uri);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Object proxy(String uri) {
    return analyticsClient
        .get()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Object.class);
  }

  private String discoveryUri(String projectId, String segment, int days) {
    return UriComponentsBuilder.fromPath("/api/v1/analytics/{projectId}/automated-tests/" + segment)
        .queryParam("days", days)
        .buildAndExpand(projectId)
        .toUriString();
  }
}
