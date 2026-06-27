package com.platform.ingestion.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.Organization;
import com.platform.core.repository.OrganizationRepository;
import com.platform.ingestion.management.dto.CredentialDto;
import com.platform.ingestion.management.dto.SaveCredentialRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdoBootstrapServiceTest {

  @Mock OrganizationRepository orgRepo;
  @Mock CredentialService credentialService;
  @Mock com.platform.core.repository.ProjectRepository projectRepo;
  @Mock com.platform.core.repository.ProjectIntegrationConfigRepository configRepo;
  @Mock AzureOrgService azureOrgService;
  @Mock com.platform.core.service.ado.AzureOrgSyncService azureOrgSyncService;
  @Mock com.platform.core.repository.AdoTeamRepository adoTeamRepo;
  @Mock com.platform.core.repository.TeamRepository teamRepo;
  @Mock com.platform.core.repository.AdoUserRepository adoUserRepo;
  @Mock com.platform.core.repository.TeamMemberRepository memberRepo;
  @Mock Organization savedOrg;
  @Mock com.platform.core.domain.Project savedProject;

  AdoBootstrapService service;

  final UUID orgId = UUID.randomUUID();
  final UUID credId = UUID.randomUUID();
  final UUID projId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new AdoBootstrapService(
            orgRepo,
            credentialService,
            projectRepo,
            configRepo,
            azureOrgService,
            azureOrgSyncService,
            adoTeamRepo,
            teamRepo,
            adoUserRepo,
            memberRepo);
  }

  private CredentialDto credDto() {
    return new CredentialDto(
        credId,
        "ORG",
        orgId,
        "AZURE_DEVOPS_BOARDS",
        "Acme (Azure DevOps)",
        "https://dev.azure.com/acme",
        Map.of("organization", "acme"),
        true,
        true,
        0,
        "ado-bootstrap",
        Instant.now(),
        Instant.now());
  }

  @Test
  void createsOrgAndOrgScopedCredentialWhenNew() {
    when(orgRepo.findBySlug("acme")).thenReturn(Optional.empty());
    when(savedOrg.getId()).thenReturn(orgId);
    when(orgRepo.save(any())).thenReturn(savedOrg);
    when(credentialService.save(any(), eq("ado-bootstrap"))).thenReturn(credDto());

    AdoBootstrapService.BootstrapOrgResult r = service.bootstrapOrg("pat-123", "acme", "Acme");

    assertThat(r.orgCreated()).isTrue();
    assertThat(r.organizationId()).isEqualTo(orgId);
    assertThat(r.credentialId()).isEqualTo(credId);

    ArgumentCaptor<SaveCredentialRequest> cap =
        ArgumentCaptor.forClass(SaveCredentialRequest.class);
    verify(credentialService).save(cap.capture(), eq("ado-bootstrap"));
    SaveCredentialRequest req = cap.getValue();
    assertThat(req.scope()).isEqualTo("ORG");
    assertThat(req.scopeId()).isEqualTo(orgId);
    assertThat(req.integrationType()).isEqualTo("AZURE_DEVOPS_BOARDS");
    assertThat(req.secret()).containsEntry("pat", "pat-123");
    assertThat(req.connectionParams()).containsEntry("organization", "acme");
  }

  @Test
  void reusesExistingOrgIdempotently() {
    when(orgRepo.findBySlug("acme")).thenReturn(Optional.of(savedOrg));
    when(savedOrg.getId()).thenReturn(orgId);
    when(credentialService.save(any(), any())).thenReturn(credDto());

    AdoBootstrapService.BootstrapOrgResult r = service.bootstrapOrg("pat", "acme", null);

    assertThat(r.orgCreated()).isFalse();
    verify(orgRepo, never()).save(any());
  }

  @Test
  void rejectsBlankPat() {
    assertThatThrownBy(() -> service.bootstrapOrg("  ", "acme", "Acme"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void slugifyNormalizesAccountName() {
    assertThat(AdoBootstrapService.slugify("Acme Corp!!")).isEqualTo("acme-corp");
  }

  @Test
  void seedProjectsCreatesProjectsAndConfigs() {
    when(orgRepo.findById(orgId)).thenReturn(Optional.of(savedOrg));
    when(azureOrgService.listProjects(credId))
        .thenReturn(
            java.util.List.of(
                new AzureOrgService.ProjectDto("p1", "Checkout"),
                new AzureOrgService.ProjectDto("p2", "Payments")));
    when(projectRepo.findByOrganizationIdAndSlug(eq(orgId), any())).thenReturn(Optional.empty());
    when(savedProject.getId()).thenReturn(projId);
    when(projectRepo.save(any())).thenReturn(savedProject);
    when(configRepo.findByProjectIdAndIntegrationType(eq(projId), eq("AZURE_DEVOPS_BOARDS")))
        .thenReturn(Optional.empty());

    AdoBootstrapService.SeedProjectsResult r = service.seedProjects(orgId, credId);

    assertThat(r.created()).isEqualTo(2);
    assertThat(r.total()).isEqualTo(2);
    verify(projectRepo, org.mockito.Mockito.times(2)).save(any());
    verify(configRepo, org.mockito.Mockito.times(2)).save(any());
  }

  @Test
  void seedProjectsReusesExistingProjectIdempotently() {
    when(orgRepo.findById(orgId)).thenReturn(Optional.of(savedOrg));
    when(azureOrgService.listProjects(credId))
        .thenReturn(java.util.List.of(new AzureOrgService.ProjectDto("p1", "Checkout")));
    when(savedProject.getId()).thenReturn(projId);
    when(projectRepo.findByOrganizationIdAndSlug(eq(orgId), any()))
        .thenReturn(Optional.of(savedProject));
    when(configRepo.findByProjectIdAndIntegrationType(eq(projId), eq("AZURE_DEVOPS_BOARDS")))
        .thenReturn(Optional.empty());

    AdoBootstrapService.SeedProjectsResult r = service.seedProjects(orgId, credId);

    assertThat(r.created()).isZero();
    verify(projectRepo, never()).save(any());
    verify(configRepo).save(any());
  }

  @Test
  void syncStructureMapsAdoTeamsToPlatformTeams() {
    when(projectRepo.findByOrganizationId(orgId)).thenReturn(java.util.List.of(savedProject));
    when(savedProject.getId()).thenReturn(projId);
    when(azureOrgSyncService.syncProject(projId))
        .thenReturn(new com.platform.core.service.ado.AzureOrgSyncService.SyncResult(1, 0, 0, 3));
    when(adoTeamRepo.findByProjectIdOrderByName(projId))
        .thenReturn(java.util.List.of(new com.platform.core.domain.AdoTeam(projId, "t1", "Alpha")));
    when(teamRepo.existsByProjectIdAndSlug(projId, "alpha")).thenReturn(false);

    AdoBootstrapService.SyncStructureResult r = service.syncStructure(orgId);

    assertThat(r.projectsSynced()).isEqualTo(1);
    assertThat(r.teamsCreated()).isEqualTo(1);
    assertThat(r.failures()).isEmpty();
    verify(teamRepo).save(any());
  }

  @Test
  void syncStructureCollectsPerProjectFailures() {
    when(projectRepo.findByOrganizationId(orgId)).thenReturn(java.util.List.of(savedProject));
    when(savedProject.getId()).thenReturn(projId);
    when(savedProject.getSlug()).thenReturn("checkout");
    when(azureOrgSyncService.syncProject(projId)).thenThrow(new IllegalStateException("ADO down"));

    AdoBootstrapService.SyncStructureResult r = service.syncStructure(orgId);

    assertThat(r.projectsSynced()).isZero();
    assertThat(r.failures()).hasSize(1);
    assertThat(r.failures().get(0)).contains("checkout");
    verify(teamRepo, never()).save(any());
  }

  // ── B4: provision members + RBAC ──────────────────────────────────────────

  private com.platform.core.domain.AdoUser adoUser(String email, String uniqueName) {
    com.platform.core.domain.AdoUser u =
        new com.platform.core.domain.AdoUser(projId, uniqueName, "n");
    u.setEmail(email);
    return u;
  }

  @Test
  void provisionMembersGrantsViewerToMembersAndOrgAdminToOwner() {
    when(projectRepo.findByOrganizationId(orgId)).thenReturn(java.util.List.of(savedProject));
    when(savedProject.getId()).thenReturn(projId);
    when(adoUserRepo.findByProjectIdOrderByDisplayName(projId))
        .thenReturn(java.util.List.of(adoUser("alice@acme.com", "alice"), adoUser(null, "bob")));
    when(azureOrgService.resolveOwnerEmail(credId)).thenReturn("alice@acme.com");
    when(memberRepo.findByUserId(any())).thenReturn(java.util.List.of());

    AdoBootstrapService.ProvisionMembersResult r = service.provisionMembers(orgId, credId);

    assertThat(r.membersSeen()).isEqualTo(2);
    assertThat(r.grantsCreated()).isEqualTo(2);
    assertThat(r.ownerEmail()).isEqualTo("alice@acme.com");

    ArgumentCaptor<com.platform.core.domain.TeamMember> cap =
        ArgumentCaptor.forClass(com.platform.core.domain.TeamMember.class);
    verify(memberRepo, org.mockito.Mockito.times(2)).save(cap.capture());
    Map<String, String> roleByUser =
        cap.getAllValues().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.platform.core.domain.TeamMember::getUserId,
                    com.platform.core.domain.TeamMember::getRole));
    assertThat(roleByUser).containsEntry("alice@acme.com", "ORG_ADMIN");
    assertThat(roleByUser).containsEntry("bob", "VIEWER");
  }

  @Test
  void provisionMembersStillGrantsViewersWhenOwnerLookupFails() {
    when(projectRepo.findByOrganizationId(orgId)).thenReturn(java.util.List.of(savedProject));
    when(savedProject.getId()).thenReturn(projId);
    when(adoUserRepo.findByProjectIdOrderByDisplayName(projId))
        .thenReturn(java.util.List.of(adoUser("alice@acme.com", "alice")));
    // PAT lacks the Profile scope → owner resolution throws; provisioning must not fail.
    when(azureOrgService.resolveOwnerEmail(credId))
        .thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Azure DevOps rejected the PAT"));
    when(memberRepo.findByUserId(any())).thenReturn(java.util.List.of());

    AdoBootstrapService.ProvisionMembersResult r = service.provisionMembers(orgId, credId);

    assertThat(r.membersSeen()).isEqualTo(1);
    assertThat(r.grantsCreated()).isEqualTo(1);
    assertThat(r.ownerEmail()).isNull();
    ArgumentCaptor<com.platform.core.domain.TeamMember> cap =
        ArgumentCaptor.forClass(com.platform.core.domain.TeamMember.class);
    verify(memberRepo).save(cap.capture());
    assertThat(cap.getValue().getRole()).isEqualTo("VIEWER");
  }

  @Test
  void provisionMembersIsIdempotentAndNeverDowngrades() {
    when(projectRepo.findByOrganizationId(orgId)).thenReturn(java.util.List.of(savedProject));
    when(savedProject.getId()).thenReturn(projId);
    when(adoUserRepo.findByProjectIdOrderByDisplayName(projId))
        .thenReturn(
            java.util.List.of(adoUser("alice@acme.com", "alice"), adoUser("bob@acme.com", "bob")));
    when(azureOrgService.resolveOwnerEmail(credId)).thenReturn("alice@acme.com");
    // owner already ORG_ADMIN; bob already has an (elevated) org grant → leave both untouched
    when(memberRepo.findByUserId("alice@acme.com"))
        .thenReturn(
            java.util.List.of(
                new com.platform.core.domain.TeamMember(
                    "alice@acme.com",
                    null,
                    com.platform.core.domain.TeamMember.Role.ORG_ADMIN,
                    "x")));
    when(memberRepo.findByUserId("bob@acme.com"))
        .thenReturn(
            java.util.List.of(
                new com.platform.core.domain.TeamMember(
                    "bob@acme.com",
                    null,
                    com.platform.core.domain.TeamMember.Role.TEAM_ADMIN,
                    "x")));

    AdoBootstrapService.ProvisionMembersResult r = service.provisionMembers(orgId, credId);

    assertThat(r.grantsCreated()).isZero();
    verify(memberRepo, never()).save(any());
  }

  @Test
  void provisionMembersGrantsOwnerAdminEvenWhenNotAProjectMember() {
    when(projectRepo.findByOrganizationId(orgId)).thenReturn(java.util.List.of(savedProject));
    when(savedProject.getId()).thenReturn(projId);
    when(adoUserRepo.findByProjectIdOrderByDisplayName(projId)).thenReturn(java.util.List.of());
    when(azureOrgService.resolveOwnerEmail(credId)).thenReturn("owner@acme.com");
    when(memberRepo.findByUserId("owner@acme.com")).thenReturn(java.util.List.of());

    AdoBootstrapService.ProvisionMembersResult r = service.provisionMembers(orgId, credId);

    assertThat(r.membersSeen()).isEqualTo(1);
    assertThat(r.grantsCreated()).isEqualTo(1);
    ArgumentCaptor<com.platform.core.domain.TeamMember> cap =
        ArgumentCaptor.forClass(com.platform.core.domain.TeamMember.class);
    verify(memberRepo).save(cap.capture());
    assertThat(cap.getValue().getRole()).isEqualTo("ORG_ADMIN");
    assertThat(cap.getValue().getTeamId()).isNull();
  }
}
