package com.platform.ingestion.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.AzureManagedOrg;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.repository.AzureManagedOrgRepository;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.service.CredentialCipher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Azure DevOps Boards onboarding from a PAT alone: discovers every organization the credential's
 * token can access and persists the subset the user chooses to manage. Mirrors {@link
 * GitHubRepoService} (resolve credential → call API → DTO).
 *
 * <p>Discovery uses the Azure DevOps accounts API: {@code profiles/me} resolves the member id, then
 * {@code accounts?memberId=...} lists the orgs that member belongs to. PATs authenticate via Basic
 * auth with an empty username.
 */
@Service
public class AzureOrgService {

  private static final String VSSPS = "https://app.vssps.visualstudio.com";
  private static final String API = "api-version=7.1";

  private final IntegrationCredentialRepository credRepo;
  private final AzureManagedOrgRepository managedRepo;
  private final CredentialCipher cipher;
  private final ObjectMapper om;
  private final HttpClient http = trustAllClient(Duration.ofSeconds(8));

  public AzureOrgService(
      IntegrationCredentialRepository credRepo,
      AzureManagedOrgRepository managedRepo,
      CredentialCipher cipher,
      ObjectMapper om) {
    this.credRepo = credRepo;
    this.managedRepo = managedRepo;
    this.cipher = cipher;
    this.om = om;
  }

  public record OrgDto(String accountName, String accountId, String accountUri, boolean managed) {}

  public record ProjectDto(String id, String name) {}

  /** Projects in the credential's ADO organization (from {@code connectionParams.organization}). */
  @Transactional(readOnly = true)
  public List<ProjectDto> listProjects(UUID credentialId) {
    IntegrationCredential cred = load(credentialId);
    String pat = decryptPat(cred);
    String account =
        cred.getConnectionParams() != null ? cred.getConnectionParams().get("organization") : null;
    if (account == null || account.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Credential has no 'organization' connection param");
    }
    JsonNode value =
        azureGet("https://dev.azure.com/" + account + "/_apis/projects?" + API, pat).path("value");
    List<ProjectDto> out = new ArrayList<>();
    if (value.isArray()) {
      for (JsonNode p : value) {
        String name = p.path("name").asText(null);
        if (name != null) out.add(new ProjectDto(p.path("id").asText(null), name));
      }
    }
    return out;
  }

  /** All Azure orgs the credential's PAT can access, each flagged with whether it's managed. */
  @Transactional(readOnly = true)
  public List<OrgDto> listAccessible(UUID credentialId) {
    IntegrationCredential cred = load(credentialId);
    String pat = decryptPat(cred);

    Set<String> managed =
        managedRepo.findByCredentialIdOrderByAccountName(credentialId).stream()
            .map(AzureManagedOrg::getAccountName)
            .collect(Collectors.toSet());

    return fetchAccounts(pat, managed);
  }

  /**
   * All Azure orgs a <b>raw</b> PAT can access — used by first-run onboarding, before any
   * credential has been saved. Every org is flagged {@code managed=false}. Validates the PAT is
   * present; the actual scope check happens in the Azure call.
   */
  public List<OrgDto> discoverAccounts(String pat) {
    if (pat == null || pat.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pat is required");
    }
    return fetchAccounts(pat.trim(), Set.of());
  }

  /** Accounts (orgs) the PAT owner belongs to, flagging which names are in {@code managed}. */
  private List<OrgDto> fetchAccounts(String pat, Set<String> managed) {
    String memberId = resolveMemberId(pat);
    JsonNode accounts =
        azureGet(VSSPS + "/_apis/accounts?memberId=" + memberId + "&" + API, pat).path("value");

    List<OrgDto> out = new ArrayList<>();
    if (accounts.isArray()) {
      for (JsonNode a : accounts) {
        String name = a.path("accountName").asText(null);
        if (name == null) continue;
        out.add(
            new OrgDto(
                name,
                a.path("accountId").asText(null),
                a.path("accountUri").asText(null),
                managed.contains(name)));
      }
    }
    return out;
  }

  /** Replaces the set of Azure orgs managed under this credential. */
  @Transactional
  public List<OrgDto> setManaged(UUID credentialId, List<OrgDto> orgs) {
    load(credentialId); // validates existence + type
    managedRepo.deleteByCredentialId(credentialId);
    LinkedHashMap<String, AzureManagedOrg> dedup = new LinkedHashMap<>();
    if (orgs != null) {
      for (OrgDto o : orgs) {
        if (o == null || o.accountName() == null || o.accountName().isBlank()) continue;
        dedup.put(
            o.accountName(),
            new AzureManagedOrg(credentialId, o.accountName(), o.accountId(), o.accountUri()));
      }
    }
    managedRepo.saveAll(dedup.values());
    return managedRepo.findByCredentialIdOrderByAccountName(credentialId).stream()
        .map(m -> new OrgDto(m.getAccountName(), m.getAccountId(), m.getAccountUri(), true))
        .toList();
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private IntegrationCredential load(UUID id) {
    IntegrationCredential c =
        credRepo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Credential not found: " + id));
    if (!"AZURE_DEVOPS_BOARDS".equals(c.getIntegrationType())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Not an Azure DevOps Boards credential: " + c.getIntegrationType());
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
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read credential secret");
    }
  }

  /** Resolves the PAT owner's member id via the profile API (needed by the accounts API). */
  private String resolveMemberId(String pat) {
    JsonNode me = azureGet(VSSPS + "/_apis/profiles/me?" + API, pat);
    String id = me.path("id").asText(null);
    if (id == null || id.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Could not resolve Azure DevOps profile from PAT");
    }
    return id;
  }

  /** The PAT owner's identity. Any field may be {@code null} if the token can't reveal it. */
  public record OwnerProfile(String email, String displayName, String id) {}

  /** Email of the PAT owner, or {@code null} if unavailable. */
  @Transactional(readOnly = true)
  public String resolveOwnerEmail(UUID credentialId) {
    return resolveOwner(credentialId).email();
  }

  /**
   * Resolves the PAT owner ("me"). Tries the profile API ({@code /_apis/profiles/me}, which needs
   * the Profile scope); falls back to the org's {@code connectionData} (works with a basic PAT — no
   * Profile scope), reading {@code authenticatedUser.properties.Account} for the email/UPN.
   */
  @Transactional(readOnly = true)
  public OwnerProfile resolveOwner(UUID credentialId) {
    IntegrationCredential cred = load(credentialId);
    String pat = decryptPat(cred);

    // 1) Profile API (richest, but needs the Profile scope).
    try {
      JsonNode me = azureGet(VSSPS + "/_apis/profiles/me?" + API, pat);
      String email = trimToNull(me.path("emailAddress").asText(null));
      String name = trimToNull(me.path("displayName").asText(null));
      String id = trimToNull(me.path("id").asText(null));
      if (email != null) return new OwnerProfile(email, name, id);
    } catch (RuntimeException ignore) {
      /* fall through to connectionData */
    }

    // 2) connectionData (org-scoped; works without the Profile scope).
    String account =
        cred.getConnectionParams() != null ? cred.getConnectionParams().get("organization") : null;
    if (account != null && !account.isBlank()) {
      try {
        // connectionData is preview-only; "api-version=7.1" is rejected ("must supply -preview").
        JsonNode user =
            azureGet(
                    "https://dev.azure.com/"
                        + account
                        + "/_apis/connectionData?api-version=7.1-preview",
                    pat)
                .path("authenticatedUser");
        String email =
            trimToNull(user.path("properties").path("Account").path("$value").asText(null));
        String name =
            trimToNull(
                user.path("providerDisplayName")
                    .asText(user.path("customDisplayName").asText(null)));
        String id = trimToNull(user.path("id").asText(null));
        return new OwnerProfile(email, name, id);
      } catch (RuntimeException ignore) {
        /* fall through */
      }
    }
    return new OwnerProfile(null, null, null);
  }

  private static String trimToNull(String s) {
    return s != null && !s.isBlank() ? s.trim() : null;
  }

  private JsonNode azureGet(String url, String pat) {
    try {
      String basic =
          Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Authorization", "Basic " + basic)
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(15))
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() == 401 || resp.statusCode() == 403) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Azure DevOps rejected the PAT (check scopes)");
      }
      if (resp.statusCode() / 100 != 2) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Azure DevOps API error: HTTP " + resp.statusCode());
      }
      return om.readTree(resp.body());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Azure DevOps request failed: " + e.getMessage());
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
