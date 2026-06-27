package com.platform.portal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * BFF proxy for first-run Azure DevOps onboarding (blank platform, no Organization yet). Proxies to
 * platform-ingestion's {@code /api/v1/ado/onboard/**}.
 *
 * <p>Upstream errors are forwarded with their original status and message (rather than collapsing
 * to a generic 500) so the onboarding wizard can show actionable feedback — e.g. a missing {@code
 * PLATFORM_CRED_KEY} or a PAT rejected for insufficient scopes.
 */
@RestController
@RequestMapping("/api/portal/ado/onboard")
public class PortalAdoOnboardController {

  // Instantiated directly: the portal (Spring Boot 4) has no com.fasterxml ObjectMapper bean to
  // inject. Only used to read a field out of an upstream error body.
  private static final ObjectMapper OM = new ObjectMapper();

  private final RestClient ingestionClient;

  public PortalAdoOnboardController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  /** Discover the orgs a raw PAT can access (no credential saved). */
  @PostMapping("/discover")
  public Object discover(@RequestBody Object body) {
    return proxy("/api/v1/ado/onboard/discover", body);
  }

  /** Run the full bootstrap for the chosen ADO account (org + projects + structure + members). */
  @PostMapping("/org")
  public Object onboardOrg(@RequestBody Object body) {
    return proxy("/api/v1/ado/onboard/org", body);
  }

  /**
   * Re-sync an already-onboarded org (projects + structure + members) from its stored credential.
   */
  @PostMapping("/resync")
  public Object resync(@RequestBody Object body) {
    return proxy("/api/v1/ado/onboard/resync", body);
  }

  /** Fetch the PAT owner's identity ("me") for a stored credential. */
  @GetMapping("/me")
  public Object me(@RequestParam String credentialId) {
    try {
      return ingestionClient
          .get()
          .uri("/api/v1/ado/onboard/me?credentialId={c}", credentialId)
          .accept(MediaType.APPLICATION_JSON)
          .retrieve()
          .body(Object.class);
    } catch (RestClientResponseException e) {
      throw new ResponseStatusException(e.getStatusCode(), upstreamMessage(e));
    }
  }

  /** Resolve "me" from the credential and grant that user org-wide ORG_ADMIN. */
  @PostMapping("/claim-admin")
  public Object claimAdmin(@RequestBody Object body) {
    return proxy("/api/v1/ado/onboard/claim-admin", body);
  }

  private Object proxy(String uri, Object body) {
    try {
      return ingestionClient
          .post()
          .uri(uri)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Object.class);
    } catch (RestClientResponseException e) {
      throw new ResponseStatusException(e.getStatusCode(), upstreamMessage(e));
    }
  }

  /** Pulls the human-readable message out of an upstream ProblemDetail body, if present. */
  private String upstreamMessage(RestClientResponseException e) {
    try {
      JsonNode n = OM.readTree(e.getResponseBodyAsString());
      for (String field : new String[] {"detail", "message", "title"}) {
        String v = n.path(field).asText(null);
        if (v != null && !v.isBlank()) return v;
      }
    } catch (Exception ignore) {
      /* fall through */
    }
    return e.getStatusText();
  }
}
