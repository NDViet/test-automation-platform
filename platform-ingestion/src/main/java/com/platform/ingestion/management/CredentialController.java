package com.platform.ingestion.management;

import com.platform.ingestion.management.dto.CredentialDto;
import com.platform.ingestion.management.dto.SaveCredentialRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin API for scoped, encrypted integration credentials — the centralized "Admin PAT" plus
 * Team/Project overrides resolved by the Org→Team→Project cascade.
 */
@RestController
@RequestMapping("/api/v1/credentials")
@Tag(name = "Integration Credentials")
public class CredentialController {

  private final CredentialService service;
  private final GitHubRepoService gitHubRepoService;
  private final AzureOrgService azureOrgService;

  public CredentialController(
      CredentialService service,
      GitHubRepoService gitHubRepoService,
      AzureOrgService azureOrgService) {
    this.service = service;
    this.gitHubRepoService = gitHubRepoService;
    this.azureOrgService = azureOrgService;
  }

  /** List credentials at a scope. ORG: no scopeId; TEAM/PROJECT: scopeId required. */
  @GetMapping
  public List<CredentialDto> list(
      @RequestParam String scope, @RequestParam(required = false) UUID scopeId) {
    return service.list(scope, scopeId);
  }

  @PostMapping
  public CredentialDto save(
      @Valid @RequestBody SaveCredentialRequest req,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return service.save(req, actor != null ? actor : "portal");
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/test")
  public Map<String, Object> testConnection(@PathVariable UUID id) {
    CredentialHealthChecker.Result r = service.testConnection(id);
    return Map.of("ok", r.ok(), "message", r.message());
  }

  // ── GitHub: discover accessible repos + select which to manage ───────────────

  /** All repos the GitHub credential's PAT can access, each flagged if already managed. */
  @GetMapping("/{id}/github/repos")
  public List<GitHubRepoService.RepoDto> githubRepos(@PathVariable UUID id) {
    return gitHubRepoService.listAccessible(id);
  }

  public record SetReposRequest(List<GitHubRepoService.RepoDto> repos) {}

  /** Replace the set of repos managed under this GitHub credential. */
  @PutMapping("/{id}/github/repos")
  public List<GitHubRepoService.RepoDto> setGithubRepos(
      @PathVariable UUID id, @RequestBody SetReposRequest req) {
    return gitHubRepoService.setManaged(id, req.repos());
  }

  /** Fetch all repos from GitHub and refresh the local cache. Returns the new cache state. */
  @PostMapping("/{id}/github/repos/sync")
  public GitHubRepoService.CachedResult syncGithubRepos(@PathVariable UUID id) {
    return gitHubRepoService.syncToCache(id);
  }

  /** Return cached repos (no GitHub API call). Returns empty list if cache was never synced. */
  @GetMapping("/{id}/github/repos/cached")
  public GitHubRepoService.CachedResult cachedGithubRepos(@PathVariable UUID id) {
    return gitHubRepoService.listCached(id);
  }

  // ── Azure DevOps Boards: discover accessible orgs + select which to manage ────

  /** All Azure orgs the credential's PAT can access, each flagged if already managed. */
  @GetMapping("/{id}/azure/orgs")
  public List<AzureOrgService.OrgDto> azureOrgs(@PathVariable UUID id) {
    return azureOrgService.listAccessible(id);
  }

  public record SetOrgsRequest(List<AzureOrgService.OrgDto> orgs) {}

  /** Replace the set of Azure orgs managed under this credential. */
  @PutMapping("/{id}/azure/orgs")
  public List<AzureOrgService.OrgDto> setAzureOrgs(
      @PathVariable UUID id, @RequestBody SetOrgsRequest req) {
    return azureOrgService.setManaged(id, req.orgs());
  }

  /** Update the auto-sync interval (minutes; 0 = manual only). */
  @PatchMapping("/{id}/sync-interval")
  public CredentialDto updateSyncInterval(@PathVariable UUID id, @RequestParam int minutes) {
    return service.updateSyncInterval(id, minutes);
  }
}
