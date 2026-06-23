package com.platform.ingestion.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.GitHubRepoCache;
import com.platform.core.domain.GithubManagedRepo;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.repository.GitHubRepoCacheRepository;
import com.platform.core.repository.GithubManagedRepoRepository;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.service.CredentialCipher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GitHub onboarding from a PAT alone: discovers every repository the credential's token
 * can access (across the user and their orgs), and persists the subset the user chooses
 * to manage. Mirrors the ADO discovery pattern (resolve credential → call API → DTO).
 */
@Service
public class GitHubRepoService {

    private final IntegrationCredentialRepository credRepo;
    private final GithubManagedRepoRepository managedRepo;
    private final GitHubRepoCacheRepository cacheRepo;
    private final CredentialCipher cipher;
    private final ObjectMapper om;
    private final HttpClient http = trustAllClient(Duration.ofSeconds(8));

    public GitHubRepoService(IntegrationCredentialRepository credRepo,
                             GithubManagedRepoRepository managedRepo,
                             GitHubRepoCacheRepository cacheRepo,
                             CredentialCipher cipher, ObjectMapper om) {
        this.credRepo    = credRepo;
        this.managedRepo = managedRepo;
        this.cacheRepo   = cacheRepo;
        this.cipher      = cipher;
        this.om          = om;
    }

    public record RepoDto(String fullName, String owner, String name, boolean isPrivate,
                          String defaultBranch, String htmlUrl, boolean managed) {}

    public record CachedResult(List<RepoDto> repos, Instant syncedAt, int totalCount) {}

    /** All repos the credential's PAT can access, each flagged with whether it's managed. */
    @Transactional(readOnly = true)
    public List<RepoDto> listAccessible(UUID credentialId) {
        IntegrationCredential cred = load(credentialId);
        String pat = decryptPat(cred);
        String root = baseUrl(cred);

        Set<String> managed = managedRepo.findByCredentialIdOrderByFullName(credentialId)
                .stream().map(GithubManagedRepo::getFullName).collect(Collectors.toSet());

        List<RepoDto> out = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {   // cap 1000 repos
            JsonNode arr = githubGet(root + "/user/repos?per_page=100&sort=full_name&affiliation="
                    + "owner,collaborator,organization_member&page=" + page, pat);
            if (arr == null || !arr.isArray() || arr.isEmpty()) break;
            for (JsonNode r : arr) {
                String full = r.path("full_name").asText(null);
                if (full == null) continue;
                out.add(new RepoDto(full,
                        r.path("owner").path("login").asText(null),
                        r.path("name").asText(null),
                        r.path("private").asBoolean(false),
                        r.path("default_branch").asText(null),
                        r.path("html_url").asText(null),
                        managed.contains(full)));
            }
            if (arr.size() < 100) break;
        }
        return out;
    }

    /** Replaces the set of repos managed under this credential. */
    @Transactional
    public List<RepoDto> setManaged(UUID credentialId, List<RepoDto> repos) {
        load(credentialId);   // validates existence + type
        managedRepo.deleteByCredentialId(credentialId);
        LinkedHashMap<String, GithubManagedRepo> dedup = new LinkedHashMap<>();
        if (repos != null) {
            for (RepoDto r : repos) {
                if (r == null || r.fullName() == null || r.fullName().isBlank()) continue;
                String owner = r.owner(), name = r.name();
                if ((owner == null || name == null) && r.fullName().contains("/")) {
                    String[] parts = r.fullName().split("/", 2);
                    owner = parts[0]; name = parts[1];
                }
                dedup.put(r.fullName(), new GithubManagedRepo(credentialId, r.fullName(),
                        owner, name, r.isPrivate(), r.defaultBranch(), r.htmlUrl()));
            }
        }
        managedRepo.saveAll(dedup.values());
        return managedRepo.findByCredentialIdOrderByFullName(credentialId).stream()
                .map(m -> new RepoDto(m.getFullName(), m.getOwner(), m.getName(),
                        m.isPrivate(), m.getDefaultBranch(), m.getHtmlUrl(), true))
                .toList();
    }

    // ── Cache API ─────────────────────────────────────────────────────────────

    /**
     * Fetches all accessible repos from GitHub and stores them in {@code github_repo_cache}.
     * Replaces any previous cache entries for this credential atomically.
     */
    @Transactional
    public CachedResult syncToCache(UUID credentialId) {
        IntegrationCredential cred = load(credentialId);
        String pat  = decryptPat(cred);
        String root = baseUrl(cred);
        Instant now = Instant.now();

        List<GitHubRepoCache> fetched = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            JsonNode arr = githubGet(root + "/user/repos?per_page=100&sort=full_name&affiliation="
                    + "owner,collaborator,organization_member&page=" + page, pat);
            if (arr == null || !arr.isArray() || arr.isEmpty()) break;
            for (JsonNode r : arr) {
                String full = r.path("full_name").asText(null);
                if (full == null) continue;
                fetched.add(new GitHubRepoCache(
                        credentialId,
                        full,
                        r.path("owner").path("login").asText(null),
                        r.path("name").asText(null),
                        r.path("private").asBoolean(false),
                        r.path("default_branch").asText(null),
                        r.path("html_url").asText(null),
                        now));
            }
            if (arr.size() < 100) break;
        }

        cacheRepo.deleteByCredentialId(credentialId);
        cacheRepo.saveAll(fetched);
        return buildCachedResult(credentialId, fetched, now);
    }

    /** Returns the cached repo list without calling GitHub. Empty result if never synced. */
    @Transactional(readOnly = true)
    public CachedResult listCached(UUID credentialId) {
        load(credentialId);
        List<GitHubRepoCache> cached = cacheRepo.findByCredentialIdOrderByFullName(credentialId);
        if (cached.isEmpty()) return new CachedResult(List.of(), null, 0);
        return buildCachedResult(credentialId, cached, cached.get(0).getSyncedAt());
    }

    private CachedResult buildCachedResult(UUID credentialId, List<GitHubRepoCache> rows, Instant syncedAt) {
        Set<String> managed = managedRepo.findByCredentialIdOrderByFullName(credentialId)
                .stream().map(GithubManagedRepo::getFullName).collect(Collectors.toSet());
        List<RepoDto> repos = rows.stream()
                .map(c -> new RepoDto(c.getFullName(), c.getOwner(), c.getRepoName(),
                        c.isPrivate(), c.getDefaultBranch(), c.getHtmlUrl(),
                        managed.contains(c.getFullName())))
                .toList();
        return new CachedResult(repos, syncedAt, repos.size());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private IntegrationCredential load(UUID id) {
        IntegrationCredential c = credRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found: " + id));
        String t = c.getIntegrationType();
        if (!"GITHUB".equals(t) && !"GITHUB_ISSUES".equals(t)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Not a GitHub credential: " + t);
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private String decryptPat(IntegrationCredential c) {
        if (c.getSecretCiphertext() == null || c.getSecretCiphertext().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential has no PAT");
        }
        try {
            Map<String, String> secret = om.readValue(cipher.decrypt(c.getSecretCiphertext()), Map.class);
            String pat = secret.getOrDefault("pat", secret.get("token"));
            if (pat == null || pat.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential PAT is empty");
            return pat;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read credential secret");
        }
    }

    private static String baseUrl(IntegrationCredential c) {
        String b = c.getBaseUrl();
        if (b == null || b.isBlank()) return "https://api.github.com";
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
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

    private JsonNode githubGet(String url, String pat) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + pat)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 401 || code == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "GitHub authentication failed (HTTP " + code + ") — check the PAT and its scopes");
            }
            if (code < 200 || code >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub returned HTTP " + code);
            }
            return om.readTree(resp.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub request failed: " + e.getMessage());
        }
    }
}
