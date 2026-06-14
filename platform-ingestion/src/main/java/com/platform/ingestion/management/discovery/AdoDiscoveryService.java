package com.platform.ingestion.management.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.mapping.MappingRules;
import com.platform.core.mapping.MappingRulesProvider;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Explores Azure DevOps Boards structure (projects, work-item types, fields,
 * states) using the resolved {@code AZURE_DEVOPS_BOARDS} credential for a project,
 * and produces a suggested mapping profile via {@link MappingSuggester}.
 */
@Service
public class AdoDiscoveryService {

    private static final String API = "api-version=7.0";
    private static final Set<String> STOCK_TYPES = Set.of(
            "bug", "task", "epic", "feature", "user story", "product backlog item", "issue",
            "requirement", "change request", "review", "risk",
            "test case", "test plan", "test suite", "shared steps", "shared parameter",
            "code review request", "code review response", "feedback request", "feedback response",
            "impediment");

    private final CredentialResolver credentialResolver;
    private final MappingSuggester suggester;
    private final MappingRulesProvider rulesProvider;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public AdoDiscoveryService(CredentialResolver credentialResolver, MappingSuggester suggester,
                               MappingRulesProvider rulesProvider, ObjectMapper mapper) {
        this.credentialResolver = credentialResolver;
        this.suggester = suggester;
        this.rulesProvider = rulesProvider;
        this.mapper = mapper;
    }

    public record AdoProject(String id, String name) {}
    public record TypeSummary(String name, boolean custom, String suggestedLane, String suggestedIssueType) {}
    public record FieldInfo(String referenceName, String name, String type, boolean custom, boolean required) {}
    public record StateInfo(String name, String category, String color) {}
    public record TypeSchema(String workItemType, List<FieldInfo> fields, List<StateInfo> states,
                             Object suggestedProfile) {}

    /** Lists ADO projects in the org (for the project picker). */
    public List<AdoProject> listProjects(java.util.UUID projectId) {
        Ado ado = resolve(projectId);
        JsonNode resp = get(ado, "/_apis/projects?" + API);
        List<AdoProject> out = new ArrayList<>();
        for (JsonNode p : resp.path("value")) out.add(new AdoProject(p.path("id").asText(), p.path("name").asText()));
        return out;
    }

    /** Lists work-item types of an ADO project with a custom flag + lane suggestion. */
    public List<TypeSummary> listTypes(java.util.UUID projectId, String adoProject) {
        Ado ado = resolve(projectId);
        MappingRules rules = rulesProvider.effectiveForProject(projectId);
        JsonNode resp = get(ado, "/" + enc(adoProject) + "/_apis/wit/workitemtypes?" + API);
        List<TypeSummary> out = new ArrayList<>();
        for (JsonNode t : resp.path("value")) {
            String name = t.path("name").asText();
            boolean custom = !STOCK_TYPES.contains(name.toLowerCase());
            MappingSuggester.Lane lane = suggester.suggestLane(rules, name);
            out.add(new TypeSummary(name, custom, lane.lane(), lane.issueType()));
        }
        return out;
    }

    /** Returns fields + states for a type plus a suggested mapping profile. */
    public TypeSchema typeSchema(java.util.UUID projectId, String adoProject, String type) {
        Ado ado = resolve(projectId);
        JsonNode fieldsResp = get(ado, "/" + enc(adoProject) + "/_apis/wit/workitemtypes/" + enc(type) + "/fields?$expand=all&" + API);
        List<FieldInfo> fields = new ArrayList<>();
        List<MappingSuggester.Field> suggesterFields = new ArrayList<>();
        for (JsonNode f : fieldsResp.path("value")) {
            String ref = f.path("referenceName").asText();
            String name = f.path("name").asText();
            boolean custom = !(ref.startsWith("System.") || ref.startsWith("Microsoft.VSTS."));
            boolean required = f.path("alwaysRequired").asBoolean(false);
            String ftype = f.path("type").asText("");
            fields.add(new FieldInfo(ref, name, ftype, custom, required));
            suggesterFields.add(new MappingSuggester.Field(ref, name, custom, required));
        }

        JsonNode statesResp = get(ado, "/" + enc(adoProject) + "/_apis/wit/workitemtypes/" + enc(type) + "/states?" + API);
        List<StateInfo> states = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        boolean hasBlocked = false;
        for (JsonNode s : statesResp.path("value")) {
            String name = s.path("name").asText();
            String cat = s.path("category").asText("");
            states.add(new StateInfo(name, cat, s.path("color").asText("")));
            if (!cat.isBlank() && !categories.contains(cat)) categories.add(cat);
            if ("Blocked".equalsIgnoreCase(name)) hasBlocked = true;
        }

        MappingRules rules = rulesProvider.effectiveForProject(projectId);
        Object suggested = suggester.suggest(rules, "AZURE_DEVOPS_BOARDS", type, suggesterFields, categories, hasBlocked);
        return new TypeSchema(type, fields, states, suggested);
    }

    // ── credential + http ────────────────────────────────────────────────────────

    private record Ado(String root, String authHeader) {}

    private Ado resolve(java.util.UUID projectId) {
        ResolvedCredential cred = credentialResolver.resolve(projectId, "AZURE_DEVOPS_BOARDS")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No Azure DevOps Boards credential resolved for this project. Configure one (Org/Team/Project) first."));
        String org = firstNonBlank(cred.param("organization"), cred.param("org"));
        String pat = firstNonBlank(cred.secret("pat"), cred.secret("token"));
        if (org == null || pat == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Azure credential is missing organization or pat");
        }
        String base = cred.baseUrl();
        if (base == null || base.isBlank()) base = "https://dev.azure.com";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.endsWith("/" + org)) base = base + "/" + org;
        String auth = "Basic " + Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
        return new Ado(base, auth);
    }

    private JsonNode get(Ado ado, String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ado.root() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", ado.authHeader())
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Azure DevOps auth failed (HTTP " + resp.statusCode() + ")");
            }
            if (resp.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Azure DevOps returned HTTP " + resp.statusCode());
            }
            return mapper.readTree(resp.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Azure DevOps request failed: " + e.getMessage());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String firstNonBlank(String... v) {
        for (String x : v) if (x != null && !x.isBlank()) return x;
        return null;
    }
}
