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
 * BFF proxy for the credential encryption key lifecycle. Proxies to platform-ingestion's {@code
 * /api/v1/security/cred-key/**} so a from-scratch platform can set up / unlock the key from the UI.
 * Upstream errors are forwarded with their original status + message.
 */
@RestController
@RequestMapping("/api/portal/security/cred-key")
public class PortalSecurityController {

  // Instantiated directly: the portal (Spring Boot 4) has no com.fasterxml ObjectMapper bean to
  // inject. Only used to read a field out of an upstream error body.
  private static final ObjectMapper OM = new ObjectMapper();

  private final RestClient ingestionClient;

  public PortalSecurityController(@Qualifier("ingestionClient") RestClient ingestionClient) {
    this.ingestionClient = ingestionClient;
  }

  @GetMapping("/status")
  public Object status() {
    try {
      return ingestionClient
          .get()
          .uri("/api/v1/security/cred-key/status")
          .accept(MediaType.APPLICATION_JSON)
          .retrieve()
          .body(Object.class);
    } catch (RestClientResponseException e) {
      throw new ResponseStatusException(e.getStatusCode(), upstreamMessage(e));
    }
  }

  @PostMapping("/init")
  public Object init(@RequestBody Object body) {
    return proxyPost("/api/v1/security/cred-key/init", body);
  }

  @PostMapping("/unlock")
  public Object unlock(@RequestBody Object body) {
    return proxyPost("/api/v1/security/cred-key/unlock", body);
  }

  private Object proxyPost(String uri, Object body) {
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
