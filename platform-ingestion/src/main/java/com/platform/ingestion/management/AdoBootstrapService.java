package com.platform.ingestion.management;

import com.platform.core.domain.AdoTeam;
import com.platform.core.domain.AdoUser;
import com.platform.core.domain.Organization;
import com.platform.core.domain.Project;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.domain.Team;
import com.platform.core.domain.TeamMember;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.AdoUserRepository;
import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamMemberRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.core.service.ado.AzureOrgSyncService;
import com.platform.ingestion.management.dto.CredentialDto;
import com.platform.ingestion.management.dto.SaveCredentialRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bootstraps the platform from Azure DevOps. A blank platform has no Organization, so onboarding
 * can accept a PAT directly: this creates the platform {@link Organization} (from the ADO account
 * name) and persists the PAT as an <b>ORG-scoped</b> {@code AZURE_DEVOPS_BOARDS} credential, which
 * {@code CredentialResolver} then resolves for every project seeded under that org. Idempotent —
 * re-running reuses the org (by slug) and upserts the credential.
 *
 * <p>B1 of the ADO-bootstrap plan: org + credential. Projects, structure sync, and user/RBAC
 * provisioning build on this in later slices.
 */
@Service
public class AdoBootstrapService {

  private static final Logger log = LoggerFactory.getLogger(AdoBootstrapService.class);
  private static final String TYPE = "AZURE_DEVOPS_BOARDS";

  private final OrganizationRepository orgRepo;
  private final CredentialService credentialService;
  private final ProjectRepository projectRepo;
  private final ProjectIntegrationConfigRepository configRepo;
  private final AzureOrgService azureOrgService;
  private final AzureOrgSyncService azureOrgSyncService;
  private final AdoTeamRepository adoTeamRepo;
  private final TeamRepository teamRepo;
  private final AdoUserRepository adoUserRepo;
  private final TeamMemberRepository memberRepo;

  public AdoBootstrapService(
      OrganizationRepository orgRepo,
      CredentialService credentialService,
      ProjectRepository projectRepo,
      ProjectIntegrationConfigRepository configRepo,
      AzureOrgService azureOrgService,
      AzureOrgSyncService azureOrgSyncService,
      AdoTeamRepository adoTeamRepo,
      TeamRepository teamRepo,
      AdoUserRepository adoUserRepo,
      TeamMemberRepository memberRepo) {
    this.orgRepo = orgRepo;
    this.credentialService = credentialService;
    this.projectRepo = projectRepo;
    this.configRepo = configRepo;
    this.azureOrgService = azureOrgService;
    this.azureOrgSyncService = azureOrgSyncService;
    this.adoTeamRepo = adoTeamRepo;
    this.teamRepo = teamRepo;
    this.adoUserRepo = adoUserRepo;
    this.memberRepo = memberRepo;
  }

  public record BootstrapOrgResult(
      UUID organizationId, String slug, UUID credentialId, boolean orgCreated) {}

  public record SeedProjectsResult(int created, int total) {}

  public record SyncStructureResult(int projectsSynced, int teamsCreated, List<String> failures) {}

  public record ProvisionMembersResult(int grantsCreated, int membersSeen, String ownerEmail) {}

  public record ResyncResult(
      SeedProjectsResult projects, SyncStructureResult structure, ProvisionMembersResult members) {}

  public record ClaimAdminResult(
      String email, String displayName, boolean granted, boolean alreadyAdmin) {}

  /** Resolves the PAT owner ("me") from the stored credential without changing anything. */
  public AzureOrgService.OwnerProfile fetchOwner(UUID credentialId) {
    return azureOrgService.resolveOwner(credentialId);
  }

  /**
   * Resolves the PAT owner ("me") and grants them an org-wide {@code ORG_ADMIN} role. Lets the
   * person onboarding claim admin when auto-provisioning couldn't (e.g. the PAT lacked the Profile
   * scope). Idempotent — a no-op if they are already an org admin.
   */
  @Transactional
  public ClaimAdminResult claimAdmin(UUID credentialId) {
    AzureOrgService.OwnerProfile me = azureOrgService.resolveOwner(credentialId);
    if (me == null || isBlank(me.email())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Could not resolve your Azure DevOps email from this token (check its scopes)");
    }
    String userId = canonicalId(me.email());
    boolean alreadyAdmin =
        memberRepo.findByUserId(userId).stream()
            .anyMatch(m -> m.getTeamId() == null && "ORG_ADMIN".equals(m.getRole()));
    boolean granted = false;
    if (!alreadyAdmin) {
      memberRepo.save(new TeamMember(userId, null, TeamMember.Role.ORG_ADMIN, "ado-claim-admin"));
      granted = true;
    }
    log.info(
        "[ADO bootstrap] claim-admin: {} (granted={}, alreadyAdmin={})",
        userId,
        granted,
        alreadyAdmin);
    return new ClaimAdminResult(userId, me.displayName(), granted, alreadyAdmin);
  }

  /**
   * Re-pulls an already-onboarded org from Azure DevOps using its stored credential: seeds any new
   * projects, syncs structure (teams/areas/iterations/users), and provisions RBAC for new members.
   * Idempotent — safe to run on demand from the integrations UI to refresh the platform.
   */
  public ResyncResult resync(UUID organizationId, UUID credentialId) {
    SeedProjectsResult projects = seedProjects(organizationId, credentialId);
    SyncStructureResult structure = syncStructure(organizationId);
    ProvisionMembersResult members = provisionMembers(organizationId, credentialId);
    return new ResyncResult(projects, structure, members);
  }

  /**
   * Creates (or reuses) the platform Organization for an ADO account and stores the PAT as its
   * ORG-scoped credential.
   *
   * @param pat Azure DevOps personal access token (stored encrypted)
   * @param adoAccount the ADO organization/account name (e.g. {@code acme} for dev.azure.com/acme)
   * @param displayName optional friendly org name; defaults to the account name
   */
  @Transactional
  public BootstrapOrgResult bootstrapOrg(String pat, String adoAccount, String displayName) {
    if (isBlank(pat)) throw badRequest("pat is required");
    if (isBlank(adoAccount)) throw badRequest("adoAccount is required");

    String account = adoAccount.trim();
    String name = !isBlank(displayName) ? displayName.trim() : account;
    String slug = slugify(account);

    Organization existing = orgRepo.findBySlug(slug).orElse(null);
    boolean orgCreated = existing == null;
    Organization org = existing != null ? existing : orgRepo.save(new Organization(name, slug));

    SaveCredentialRequest req =
        new SaveCredentialRequest(
            "ORG",
            org.getId(),
            TYPE,
            name + " (Azure DevOps)",
            "https://dev.azure.com/" + account,
            Map.of("organization", account),
            Map.of("pat", pat),
            true,
            null);
    CredentialDto cred = credentialService.save(req, "ado-bootstrap");

    log.info(
        "[ADO bootstrap] org {} ({}), credential {} (orgCreated={})",
        org.getId(),
        slug,
        cred.id(),
        orgCreated);
    return new BootstrapOrgResult(org.getId(), slug, cred.id(), orgCreated);
  }

  /**
   * Seeds a platform Project (+ {@code AZURE_DEVOPS_BOARDS} integration config) for every project
   * in the credential's ADO org. Idempotent: existing projects (by org+slug) are reused and their
   * config upserted. The config carries {@code project}; the ADO org comes from the ORG-scoped
   * credential.
   */
  @Transactional
  public SeedProjectsResult seedProjects(UUID organizationId, UUID credentialId) {
    Organization org =
        orgRepo.findById(organizationId).orElseThrow(() -> badRequest("organization not found"));
    List<AzureOrgService.ProjectDto> projects = azureOrgService.listProjects(credentialId);

    int created = 0;
    for (AzureOrgService.ProjectDto p : projects) {
      String slug = slugify(p.name());
      Project existing = projectRepo.findByOrganizationIdAndSlug(organizationId, slug).orElse(null);
      Project project =
          existing != null ? existing : projectRepo.save(new Project(org, p.name(), slug));
      if (existing == null) created++;

      ProjectIntegrationConfig cfg =
          configRepo.findByProjectIdAndIntegrationType(project.getId(), TYPE).orElse(null);
      if (cfg == null) {
        cfg =
            new ProjectIntegrationConfig(
                project.getId(), "DEFAULT", TYPE, p.name() + " (ADO Boards)", "INBOUND");
        // field_mappings / filter_config are NOT NULL; seed empty (configurable later in the UI).
        cfg.setFieldMappings(Map.of());
        cfg.setFilterConfig(Map.of());
      }
      cfg.setConnectionParams(Map.of("project", p.name()));
      cfg.setEnabled(true);
      configRepo.save(cfg);
    }
    log.info(
        "[ADO bootstrap] org {}: seeded {} of {} ADO projects",
        organizationId,
        created,
        projects.size());
    return new SeedProjectsResult(created, projects.size());
  }

  /**
   * Runs the ADO structure sync for every project under the org (populating teams/areas/iterations/
   * users) and maps each {@link AdoTeam} to a platform {@link Team}. Per-project failures are
   * collected and reported; they don't abort the remaining projects. Idempotent.
   */
  @Transactional
  public SyncStructureResult syncStructure(UUID organizationId) {
    List<Project> projects = projectRepo.findByOrganizationId(organizationId);
    int projectsSynced = 0;
    int teamsCreated = 0;
    List<String> failures = new ArrayList<>();

    for (Project project : projects) {
      try {
        azureOrgSyncService.syncProject(project.getId());
        for (AdoTeam at : adoTeamRepo.findByProjectIdOrderByName(project.getId())) {
          String slug = slugify(at.getName());
          if (!teamRepo.existsByProjectIdAndSlug(project.getId(), slug)) {
            teamRepo.save(new Team(project.getId(), at.getName(), slug));
            teamsCreated++;
          }
        }
        projectsSynced++;
      } catch (Exception e) {
        log.warn(
            "[ADO bootstrap] structure sync failed for project {}: {}",
            project.getId(),
            e.getMessage());
        failures.add(project.getSlug() + ": " + e.getMessage());
      }
    }
    log.info(
        "[ADO bootstrap] org {}: synced {} projects, created {} teams, {} failures",
        organizationId,
        projectsSynced,
        teamsCreated,
        failures.size());
    return new SyncStructureResult(projectsSynced, teamsCreated, failures);
  }

  /**
   * Provisions RBAC grants from ADO members: every member gets an org-wide {@code VIEWER} grant,
   * and the PAT owner gets {@code ORG_ADMIN}. Identity is the ADO email (falling back to
   * uniqueName); there is no separate user account table — roles attach to that id via {@code
   * team_members}.
   *
   * <p>Strictly additive and idempotent: a member who already has any org-wide grant is left
   * untouched (never downgraded); the owner only gains ORG_ADMIN if they don't already have it.
   */
  @Transactional
  public ProvisionMembersResult provisionMembers(UUID organizationId, UUID credentialId) {
    Set<String> memberIds = new LinkedHashSet<>();
    for (Project project : projectRepo.findByOrganizationId(organizationId)) {
      for (AdoUser u : adoUserRepo.findByProjectIdOrderByDisplayName(project.getId())) {
        String id = canonicalId(!isBlank(u.getEmail()) ? u.getEmail() : u.getUniqueName());
        if (!isBlank(id)) memberIds.add(id);
      }
    }

    // Resolving the PAT owner needs the Profile (read) scope; many tokens lack it. Degrade
    // gracefully — provision member VIEWER grants regardless and just skip the owner ORG_ADMIN.
    String ownerEmail = null;
    try {
      ownerEmail = azureOrgService.resolveOwnerEmail(credentialId);
    } catch (Exception e) {
      log.warn(
          "[ADO bootstrap] org {}: could not resolve PAT owner (Profile scope?), skipping"
              + " ORG_ADMIN grant: {}",
          organizationId,
          e.getMessage());
    }
    ownerEmail = canonicalId(ownerEmail);
    if (!isBlank(ownerEmail)) memberIds.add(ownerEmail);

    int created = 0;
    for (String userId : memberIds) {
      boolean isOwner = ownerEmail != null && ownerEmail.equalsIgnoreCase(userId);
      List<TeamMember> orgGrants =
          memberRepo.findByUserId(userId).stream().filter(m -> m.getTeamId() == null).toList();

      if (isOwner) {
        boolean hasAdmin = orgGrants.stream().anyMatch(m -> "ORG_ADMIN".equals(m.getRole()));
        if (!hasAdmin) {
          memberRepo.save(new TeamMember(userId, null, TeamMember.Role.ORG_ADMIN, "ado-bootstrap"));
          created++;
        }
      } else if (orgGrants.isEmpty()) {
        memberRepo.save(new TeamMember(userId, null, TeamMember.Role.VIEWER, "ado-bootstrap"));
        created++;
      }
    }
    log.info(
        "[ADO bootstrap] org {}: {} members seen, {} grants created (owner={})",
        organizationId,
        memberIds.size(),
        created,
        ownerEmail);
    return new ProvisionMembersResult(created, memberIds.size(), ownerEmail);
  }

  /** Lowercases, collapses non-alphanumerics to hyphens, trims, and caps at 50 chars. */
  static String slugify(String s) {
    String slug = s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (slug.length() > 50) {
      slug = slug.substring(0, 50).replaceAll("-+$", "");
    }
    return slug.isBlank() ? "org" : slug;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /**
   * Canonical RBAC user id: trimmed, and lowercased when it's an email (emails are the unique key;
   * names are not). Non-email ids (rare manual grants) keep their case.
   */
  private static String canonicalId(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.contains("@") ? t.toLowerCase() : t;
  }

  private static ResponseStatusException badRequest(String msg) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
  }
}
