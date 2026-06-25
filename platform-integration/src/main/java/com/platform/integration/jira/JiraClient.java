package com.platform.integration.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Low-level HTTP wrapper for the JIRA REST API v3.
 *
 * <p>Auth: {@code Authorization: Basic base64(email:apiToken)}
 */
public class JiraClient {

  private static final Logger log = LoggerFactory.getLogger(JiraClient.class);

  private final String baseUrl;
  private final String authHeader;
  private final HttpClient http;
  private final ObjectMapper mapper;

  public JiraClient(String baseUrl, String email, String apiToken, ObjectMapper mapper) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.authHeader =
        "Basic " + Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes());
    this.http = trustAllClient(Duration.ofSeconds(10));
    this.mapper = mapper;
  }

  // ── Issue CRUD ─────────────────────────────────────────────────────────────

  public JsonNode createIssue(String body) {
    return post("/rest/api/3/issue", body);
  }

  public JsonNode addComment(String issueKey, String body) {
    return post("/rest/api/3/issue/" + issueKey + "/comment", body);
  }

  public void transition(String issueKey, String transitionId) {
    String body = "{\"transition\":{\"id\":\"" + transitionId + "\"}}";
    post("/rest/api/3/issue/" + issueKey + "/transitions", body);
  }

  public JsonNode getTransitions(String issueKey) {
    return get("/rest/api/3/issue/" + issueKey + "/transitions");
  }

  public JsonNode searchIssues(String jql) {
    // /rest/api/3/search was removed (410) — use POST /rest/api/3/search/jql
    // https://developer.atlassian.com/changelog/#CHANGE-2046
    String body =
        String.format(
            "{\"jql\":\"%s\",\"maxResults\":5,\"fields\":[\"key\",\"status\",\"summary\"]}",
            jql.replace("\"", "\\\""));
    return post("/rest/api/3/search/jql", body);
  }

  // ── HTTP helpers ──────────────────────────────────────────────────────────

  private JsonNode post(String path, String jsonBody) {
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + path))
              .timeout(Duration.ofSeconds(30))
              .header("Authorization", authHeader)
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 300) {
        throw new JiraApiException(
            "JIRA POST " + path + " returned " + resp.statusCode() + ": " + resp.body());
      }
      return resp.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(resp.body());
    } catch (JiraApiException e) {
      throw e;
    } catch (Exception e) {
      throw new JiraApiException("JIRA POST " + path + " failed: " + e.getMessage(), e);
    }
  }

  private JsonNode get(String path) {
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + path))
              .timeout(Duration.ofSeconds(30))
              .header("Authorization", authHeader)
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 300) {
        throw new JiraApiException(
            "JIRA GET " + path + " returned " + resp.statusCode() + ": " + resp.body());
      }
      return mapper.readTree(resp.body());
    } catch (JiraApiException e) {
      throw e;
    } catch (Exception e) {
      throw new JiraApiException("JIRA GET " + path + " failed: " + e.getMessage(), e);
    }
  }

  /** Finds the transition ID whose name matches {@code transitionName}. Throws if not found. */
  public String findTransitionId(String issueKey, String transitionName) {
    JsonNode transitions = getTransitions(issueKey);
    for (JsonNode t : transitions.path("transitions")) {
      if (transitionName.equalsIgnoreCase(t.path("name").asText())) {
        return t.path("id").asText();
      }
    }
    throw new JiraApiException(
        "Transition '" + transitionName + "' not found for issue " + issueKey);
  }

  public static class JiraApiException extends RuntimeException {
    public JiraApiException(String msg) {
      super(msg);
    }

    public JiraApiException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  private static HttpClient trustAllClient(Duration connectTimeout) {
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }

              public void checkClientTrusted(X509Certificate[] c, String a) {}

              public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
          },
          new SecureRandom());
      return HttpClient.newBuilder().connectTimeout(connectTimeout).sslContext(ctx).build();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot create HTTP client", e);
    }
  }
}
