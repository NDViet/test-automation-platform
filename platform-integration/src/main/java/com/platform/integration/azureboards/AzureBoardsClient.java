package com.platform.integration.azureboards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Low-level HTTP wrapper for the Azure DevOps Boards (Work Items) REST API.
 *
 * <p>Base URL: {@code https://dev.azure.com/{organization}}. Auth: {@code Authorization: Basic
 * base64(":" + pat)} (PAT as password, empty user).
 */
public class AzureBoardsClient {

  private static final String API_VERSION = "7.0";

  private final String baseUrl; // https://dev.azure.com/{org}
  private final String project; // ADO project (work items live here)
  private final String authHeader;
  private final HttpClient http;
  private final ObjectMapper mapper;

  public AzureBoardsClient(
      String baseUrl, String organization, String project, String pat, ObjectMapper mapper) {
    String root = (baseUrl == null || baseUrl.isBlank()) ? "https://dev.azure.com" : baseUrl;
    root = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    // If the caller gave only the host, append the org segment.
    this.baseUrl =
        (organization != null && !root.endsWith("/" + organization))
            ? root + "/" + organization
            : root;
    this.project = project;
    this.authHeader =
        "Basic " + Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
    this.http = trustAllClient(Duration.ofSeconds(10));
    this.mapper = mapper;
  }

  /** Creates a work item of {@code type} (Bug/Task) from a json-patch body. */
  public JsonNode createWorkItem(String type, String jsonPatchBody) {
    String path =
        "/" + enc(project) + "/_apis/wit/workitems/$" + enc(type) + "?api-version=" + API_VERSION;
    return send("POST", path, jsonPatchBody, "application/json-patch+json");
  }

  /** Applies a json-patch to an existing work item (e.g. change System.State). */
  public JsonNode updateWorkItem(String id, String jsonPatchBody) {
    String path =
        "/" + enc(project) + "/_apis/wit/workitems/" + enc(id) + "?api-version=" + API_VERSION;
    return send("PATCH", path, jsonPatchBody, "application/json-patch+json");
  }

  /** Adds a discussion comment to a work item. */
  public JsonNode addComment(String id, String text) {
    String path =
        "/"
            + enc(project)
            + "/_apis/wit/workItems/"
            + enc(id)
            + "/comments?api-version=7.0-preview.3";
    String body;
    try {
      body = mapper.writeValueAsString(java.util.Map.of("text", text));
    } catch (Exception e) {
      throw new AzureBoardsApiException("Failed to serialize comment", e);
    }
    return send("POST", path, body, "application/json");
  }

  /** Runs a WIQL query and returns the raw response (with a {@code workItems} array of ids). */
  public JsonNode queryWiql(String wiql) {
    String path = "/" + enc(project) + "/_apis/wit/wiql?api-version=" + API_VERSION;
    String body;
    try {
      body = mapper.writeValueAsString(java.util.Map.of("query", wiql));
    } catch (Exception e) {
      throw new AzureBoardsApiException("Failed to serialize WIQL", e);
    }
    return send("POST", path, body, "application/json");
  }

  /** Fetches a work item (fields + _links) by id. */
  public JsonNode getWorkItem(String id) {
    String path =
        "/" + enc(project) + "/_apis/wit/workitems/" + enc(id) + "?api-version=" + API_VERSION;
    return send("GET", path, null, "application/json");
  }

  /** Validates connectivity by listing projects in the org. */
  public JsonNode listProjects() {
    return send("GET", "/_apis/projects?api-version=" + API_VERSION, null, "application/json");
  }

  /** Builds the human-facing edit URL for a work item. */
  public String workItemUrl(String id) {
    return baseUrl + "/" + enc(project) + "/_workitems/edit/" + id;
  }

  // ── HTTP ────────────────────────────────────────────────────────────────────

  private JsonNode send(String method, String path, String body, String contentType) {
    try {
      HttpRequest.Builder b =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + path))
              .timeout(Duration.ofSeconds(30))
              .header("Authorization", authHeader)
              .header("Accept", "application/json");
      if (body != null) {
        b.header("Content-Type", contentType);
        b.method(method, HttpRequest.BodyPublishers.ofString(body));
      } else {
        b.method(method, HttpRequest.BodyPublishers.noBody());
      }
      HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 300) {
        throw new AzureBoardsApiException(
            "Azure Boards "
                + method
                + " "
                + path
                + " returned "
                + resp.statusCode()
                + ": "
                + resp.body());
      }
      return resp.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(resp.body());
    } catch (AzureBoardsApiException e) {
      throw e;
    } catch (Exception e) {
      throw new AzureBoardsApiException(
          "Azure Boards " + method + " " + path + " failed: " + e.getMessage(), e);
    }
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public static class AzureBoardsApiException extends RuntimeException {
    public AzureBoardsApiException(String msg) {
      super(msg);
    }

    public AzureBoardsApiException(String msg, Throwable cause) {
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
