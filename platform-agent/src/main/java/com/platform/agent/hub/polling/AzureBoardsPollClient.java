package com.platform.agent.hub.polling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal Azure DevOps Boards REST client for the pull-based poller. Resolves the
 * org/PAT/base-URL from the project's {@code AZURE_DEVOPS_BOARDS} credential cascade
 * (Org→Project→Team) and runs WIQL queries + batch work-item fetches (with relations,
 * for hierarchy/dependency links).
 */
@Component
public class AzureBoardsPollClient {

    private static final String API = "api-version=7.0";
    private static final int BATCH = 200; // ADO workitemsbatch hard cap

    private final CredentialResolver credentialResolver;
    private final ObjectMapper mapper;
    private final HttpClient http = trustAllClient(Duration.ofSeconds(10));

    public AzureBoardsPollClient(CredentialResolver credentialResolver, ObjectMapper mapper) {
        this.credentialResolver = credentialResolver;
        this.mapper = mapper;
    }

    /** Resolved connection (org-rooted base URL + Basic auth) for a project, or null if not configured. */
    public record Ado(String root, String authHeader) {}

    public Ado connect(UUID projectId) {
        ResolvedCredential cred = credentialResolver.resolve(projectId, "AZURE_DEVOPS_BOARDS").orElse(null);
        if (cred == null) return null;
        String org = firstNonBlank(cred.param("organization"), cred.param("org"));
        String pat = firstNonBlank(cred.secret("pat"), cred.secret("token"));
        if (org == null || pat == null) return null;
        String base = cred.baseUrl();
        if (base == null || base.isBlank()) base = "https://dev.azure.com";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.endsWith("/" + org)) base = base + "/" + org;
        String auth = "Basic " + Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
        return new Ado(base, auth);
    }

    /**
     * Returns work-item ids in {@code project} (optionally filtered by area path) that
     * changed on/after {@code sinceDate} (yyyy-MM-dd; null = all), oldest-changed first.
     */
    public List<String> queryWorkItemIds(Ado ado, String project, String areaPath, String sinceDate) {
        StringBuilder wiql = new StringBuilder("SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = '")
                .append(esc(project)).append("'");
        if (areaPath != null && !areaPath.isBlank()) {
            wiql.append(" AND [System.AreaPath] UNDER '").append(esc(areaPath)).append("'");
        }
        if (sinceDate != null && !sinceDate.isBlank()) {
            wiql.append(" AND [System.ChangedDate] >= '").append(esc(sinceDate)).append("'");
        }
        wiql.append(" ORDER BY [System.ChangedDate] ASC");

        JsonNode resp = send("POST", "/" + enc(project) + "/_apis/wit/wiql?" + API,
                json(Map.of("query", wiql.toString())), ado);
        List<String> ids = new ArrayList<>();
        for (JsonNode wi : resp.path("workItems")) ids.add(wi.path("id").asText());
        return ids;
    }

    /** Batch-fetches work items (fields + relations) for the given ids, in chunks of 200. */
    public List<JsonNode> getWorkItems(Ado ado, List<String> ids) {
        List<JsonNode> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += BATCH) {
            List<Integer> chunk = new ArrayList<>();
            for (String id : ids.subList(i, Math.min(i + BATCH, ids.size()))) {
                try { chunk.add(Integer.parseInt(id)); } catch (NumberFormatException ignored) {}
            }
            if (chunk.isEmpty()) continue;
            JsonNode resp = send("POST", "/_apis/wit/workitemsbatch?" + API,
                    json(Map.of("ids", chunk, "$expand", "Relations")), ado);
            for (JsonNode wi : resp.path("value")) out.add(wi);
        }
        return out;
    }

    // ── Org structure (teams / areas / iterations / members) ──────────────────────

    /** Teams in the ADO project ({@code value[]} of {id,name,description}). */
    public JsonNode getTeams(Ado ado, String project) {
        return send("GET", "/_apis/projects/" + enc(project) + "/teams?" + API, null, ado);
    }

    /** Members of an ADO team ({@code value[]} of {identity:{displayName,uniqueName,descriptor}}). */
    public JsonNode getTeamMembers(Ado ado, String project, String teamId) {
        return send("GET", "/_apis/projects/" + enc(project) + "/teams/" + enc(teamId)
                + "/members?" + API, null, ado);
    }

    /** A team's area field values ({defaultValue, values:[{value,includeChildren}]}). */
    public JsonNode getTeamFieldValues(Ado ado, String project, String teamId) {
        return send("GET", "/" + enc(project) + "/" + enc(teamId)
                + "/_apis/work/teamsettings/teamfieldvalues?" + API, null, ado);
    }

    /** Classification-node tree for {@code group} = "areas" | "iterations" (depth-expanded). */
    public JsonNode getClassificationNodes(Ado ado, String project, String group, int depth) {
        return send("GET", "/" + enc(project) + "/_apis/wit/classificationnodes/" + group
                + "?$depth=" + depth + "&" + API, null, ado);
    }

    /** Full change history of a work item ({@code value[]} of updates with revisedBy + field deltas). */
    public JsonNode getWorkItemUpdates(Ado ado, String project, String workItemId) {
        return send("GET", "/" + enc(project) + "/_apis/wit/workItems/" + enc(workItemId)
                + "/updates?" + API, null, ado);
    }

    /**
     * Returns the {@code authenticatedUser} node from {@code /_apis/connectionData}.
     * Contains {@code providerDisplayName} and {@code subjectDescriptor} of the PAT owner.
     */
    public JsonNode getAuthenticatedUser(Ado ado) {
        return send("GET", "/_apis/connectionData", null, ado).path("authenticatedUser");
    }

    // ── http ─────────────────────────────────────────────────────────────────────

    private JsonNode send(String method, String path, String body, Ado ado) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ado.root() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", ado.authHeader())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new IllegalStateException("Azure DevOps " + method + " " + path
                        + " returned HTTP " + resp.statusCode());
            }
            return resp.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(resp.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Azure DevOps request failed: " + e.getMessage(), e);
        }
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    private static String esc(String s) { return s == null ? "" : s.replace("'", "''"); }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }

    private static HttpClient trustAllClient(Duration connectTimeout) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }}, new SecureRandom());
            return HttpClient.newBuilder().connectTimeout(connectTimeout).sslContext(ctx).build();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create HTTP client", e);
        }
    }
}
