package com.platform.ingestion.management;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Onboarding from Azure DevOps for a blank platform (no Organization yet). Accepts a PAT directly
 * and seeds the platform from the chosen ADO account.
 */
@RestController
@RequestMapping("/api/v1/ado/onboard")
@Tag(name = "ADO Onboarding")
public class AdoOnboardingController {

  private final AdoBootstrapService bootstrap;
  private final AzureOrgService azureOrgService;

  public AdoOnboardingController(AdoBootstrapService bootstrap, AzureOrgService azureOrgService) {
    this.bootstrap = bootstrap;
    this.azureOrgService = azureOrgService;
  }

  public record DiscoverRequest(String pat) {}

  /**
   * Lists the Azure DevOps organizations a raw PAT can access, so the first-run wizard can let the
   * user pick the account to onboard. No credential is persisted at this step.
   */
  @PostMapping("/discover")
  public List<AzureOrgService.OrgDto> discover(@RequestBody DiscoverRequest req) {
    return azureOrgService.discoverAccounts(req.pat());
  }

  public record OnboardOrgRequest(String pat, String adoAccount, String displayName) {}

  public record OnboardResult(
      AdoBootstrapService.BootstrapOrgResult org,
      AdoBootstrapService.SeedProjectsResult projects,
      AdoBootstrapService.SyncStructureResult structure,
      AdoBootstrapService.ProvisionMembersResult members) {}

  /**
   * Full ADO onboarding for a blank platform: create (or reuse) the Organization, store its PAT,
   * seed projects + integration configs, sync the ADO structure (teams/areas/users), and provision
   * RBAC grants from ADO members (members → VIEWER, PAT owner → ORG_ADMIN).
   */
  /** Fetch the PAT owner's identity ("me") from a stored credential — no changes persisted. */
  @org.springframework.web.bind.annotation.GetMapping("/me")
  public AzureOrgService.OwnerProfile me(
      @org.springframework.web.bind.annotation.RequestParam java.util.UUID credentialId) {
    return bootstrap.fetchOwner(credentialId);
  }

  public record ClaimAdminRequest(java.util.UUID credentialId) {}

  /** Resolve "me" from the credential and grant that user org-wide ORG_ADMIN. */
  @PostMapping("/claim-admin")
  public AdoBootstrapService.ClaimAdminResult claimAdmin(@RequestBody ClaimAdminRequest req) {
    if (req.credentialId() == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "credentialId is required");
    }
    return bootstrap.claimAdmin(req.credentialId());
  }

  public record ResyncRequest(java.util.UUID organizationId, java.util.UUID credentialId) {}

  /**
   * Re-sync an already-onboarded org from Azure DevOps using its stored ORG-scoped credential
   * (projects + structure + members). No PAT required.
   */
  @PostMapping("/resync")
  public AdoBootstrapService.ResyncResult resync(@RequestBody ResyncRequest req) {
    if (req.organizationId() == null || req.credentialId() == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "organizationId and credentialId are required");
    }
    return bootstrap.resync(req.organizationId(), req.credentialId());
  }

  @PostMapping("/org")
  public OnboardResult onboardOrg(@RequestBody OnboardOrgRequest req) {
    AdoBootstrapService.BootstrapOrgResult org =
        bootstrap.bootstrapOrg(req.pat(), req.adoAccount(), req.displayName());
    AdoBootstrapService.SeedProjectsResult projects =
        bootstrap.seedProjects(org.organizationId(), org.credentialId());
    AdoBootstrapService.SyncStructureResult structure =
        bootstrap.syncStructure(org.organizationId());
    AdoBootstrapService.ProvisionMembersResult members =
        bootstrap.provisionMembers(org.organizationId(), org.credentialId());
    return new OnboardResult(org, projects, structure, members);
  }
}
