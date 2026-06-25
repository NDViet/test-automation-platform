package com.platform.agent.hub.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.agent.hub.polling.AzureBoardsPollClient;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.AdoArea;
import com.platform.core.domain.AdoIteration;
import com.platform.core.domain.AdoTeam;
import com.platform.core.domain.AdoUser;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.domain.Team;
import com.platform.core.repository.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs the Azure DevOps organizational structure for a project — teams (with their owned area
 * paths + member counts), the area-path and iteration trees, and a user directory (ADO team members
 * ∪ people referenced on work items). Foundation for quality dashboards.
 */
@Service
public class AzureOrgSyncService {

  private static final Logger log = LoggerFactory.getLogger(AzureOrgSyncService.class);
  private static final int TREE_DEPTH = 12;

  private final ProjectIntegrationConfigRepository configRepo;
  private final AzureBoardsPollClient client;
  private final AdoTeamRepository teamRepo;
  private final AdoAreaRepository areaRepo;
  private final AdoIterationRepository iterationRepo;
  private final AdoUserRepository userRepo;
  private final PlatformRequirementRepository requirementRepo;
  private final TeamRepository platformTeamRepo;

  public AzureOrgSyncService(
      ProjectIntegrationConfigRepository configRepo,
      AzureBoardsPollClient client,
      AdoTeamRepository teamRepo,
      AdoAreaRepository areaRepo,
      AdoIterationRepository iterationRepo,
      AdoUserRepository userRepo,
      PlatformRequirementRepository requirementRepo,
      TeamRepository platformTeamRepo) {
    this.configRepo = configRepo;
    this.client = client;
    this.teamRepo = teamRepo;
    this.areaRepo = areaRepo;
    this.iterationRepo = iterationRepo;
    this.userRepo = userRepo;
    this.requirementRepo = requirementRepo;
    this.platformTeamRepo = platformTeamRepo;
  }

  public record SyncResult(int teams, int areas, int iterations, int users) {}

  @Transactional
  public SyncResult syncProject(UUID projectId) {
    List<ProjectIntegrationConfig> configs =
        configRepo.findAllByProjectIdAndIntegrationType(
            projectId, IntegrationType.AZURE_DEVOPS_BOARDS.name());
    if (configs.isEmpty()) {
      throw new IllegalArgumentException(
          "No Azure DevOps Boards integration config for project " + projectId);
    }
    String project = null;
    for (ProjectIntegrationConfig c : configs) {
      project = firstNonBlank(c.param("project"), c.param("project_key"));
      if (project != null) break;
    }
    if (project == null) throw new IllegalStateException("ADO config has no 'project' param");

    AzureBoardsPollClient.Ado ado = client.connect(projectId);
    if (ado == null)
      throw new IllegalStateException(
          "No resolvable AZURE_DEVOPS_BOARDS credential for project " + projectId);

    // people collected from team membership (uniqueName -> details)
    Map<String, AdoUser> memberUsers = new LinkedHashMap<>();

    int teams = syncTeams(projectId, project, ado, memberUsers);
    int areas = syncAreas(projectId, project, ado);
    int iterations = syncIterations(projectId, project, ado);
    int users = syncUsers(projectId, ado, memberUsers);

    log.info(
        "ADO structure sync for project {}: {} teams, {} areas, {} iterations, {} users",
        projectId,
        teams,
        areas,
        iterations,
        users);
    return new SyncResult(teams, areas, iterations, users);
  }

  // ── Teams ─────────────────────────────────────────────────────────────────────

  private int syncTeams(
      UUID projectId,
      String project,
      AzureBoardsPollClient.Ado ado,
      Map<String, AdoUser> memberUsers) {
    JsonNode resp = client.getTeams(ado, project);
    Set<String> seen = new HashSet<>();
    int count = 0;
    for (JsonNode t : resp.path("value")) {
      String adoId = t.path("id").asText(null);
      if (adoId == null) continue;
      String name = t.path("name").asText("(unnamed team)");
      seen.add(adoId);

      AdoTeam team =
          teamRepo
              .findByProjectIdAndAdoId(projectId, adoId)
              .orElseGet(() -> new AdoTeam(projectId, adoId, name));
      team.setName(name);
      team.setDescription(blankToNull(t.path("description").asText("")));

      // area ownership (team field values)
      try {
        JsonNode tfv = client.getTeamFieldValues(ado, project, adoId);
        team.setDefaultAreaPath(blankToNull(tfv.path("defaultValue").asText("")));
        List<String> paths = new ArrayList<>();
        for (JsonNode v : tfv.path("values")) {
          String p = v.path("value").asText(null);
          if (p != null && !p.isBlank()) paths.add(p);
        }
        team.setAreaPaths(paths);
      } catch (Exception e) {
        log.debug("team field values unavailable for team {}: {}", name, e.getMessage());
      }

      // members
      try {
        JsonNode members = client.getTeamMembers(ado, project, adoId);
        int mc = 0;
        for (JsonNode m : members.path("value")) {
          JsonNode id = m.has("identity") ? m.path("identity") : m;
          String display = id.path("displayName").asText(null);
          if (display == null) continue;
          mc++;
          String unique = blankToNull(id.path("uniqueName").asText(""));
          String key = unique != null ? unique : display;
          AdoUser u = memberUsers.computeIfAbsent(key, k -> new AdoUser(projectId, k, display));
          u.setDisplayName(display);
          if (unique != null && unique.contains("@")) u.setEmail(unique);
          u.setDescriptor(blankToNull(id.path("descriptor").asText("")));
          u.setTeamMember(true);
        }
        team.setMemberCount(mc);
      } catch (Exception e) {
        log.debug("members unavailable for team {}: {}", name, e.getMessage());
      }

      team.setSyncedAt(Instant.now());
      String slug = toSlug(name);
      team.setSlug(slug);
      teamRepo.save(team);

      // Bridge: ensure a platform Team entity exists so automation adapters can use the slug.
      upsertPlatformTeam(projectId, name, slug);

      count++;
    }
    // prune teams that no longer exist upstream
    for (AdoTeam existing : teamRepo.findByProjectIdOrderByName(projectId)) {
      if (!seen.contains(existing.getAdoId())) teamRepo.delete(existing);
    }
    return count;
  }

  // ── Areas / Iterations (classification nodes) ──────────────────────────────────

  private int syncAreas(UUID projectId, String project, AzureBoardsPollClient.Ado ado) {
    JsonNode root = client.getClassificationNodes(ado, project, "areas", TREE_DEPTH);
    Set<String> seen = new HashSet<>();
    int[] count = {0};
    walkNodes(
        root,
        node -> {
          String path = normalizePath(node.path("path").asText(null));
          if (path == null) return;
          seen.add(path);
          AdoArea a =
              areaRepo
                  .findByProjectIdAndPath(projectId, path)
                  .orElseGet(() -> new AdoArea(projectId, path, node.path("name").asText(path)));
          a.setAdoId(node.path("id").asText(null));
          String areaName = node.path("name").asText(path);
          a.setName(areaName);
          a.setSlug(toSlug(areaName));
          a.setParentPath(parentOf(path));
          a.setHasChildren(node.path("hasChildren").asBoolean(false));
          a.setSyncedAt(Instant.now());
          areaRepo.save(a);
          count[0]++;
        });
    for (AdoArea existing : areaRepo.findByProjectIdOrderByPath(projectId)) {
      if (!seen.contains(existing.getPath())) areaRepo.delete(existing);
    }
    return count[0];
  }

  private int syncIterations(UUID projectId, String project, AzureBoardsPollClient.Ado ado) {
    JsonNode root = client.getClassificationNodes(ado, project, "iterations", TREE_DEPTH);
    Set<String> seen = new HashSet<>();
    int[] count = {0};
    walkNodes(
        root,
        node -> {
          String path = normalizePath(node.path("path").asText(null));
          if (path == null) return;
          seen.add(path);
          AdoIteration it =
              iterationRepo
                  .findByProjectIdAndPath(projectId, path)
                  .orElseGet(
                      () -> new AdoIteration(projectId, path, node.path("name").asText(path)));
          it.setAdoId(node.path("id").asText(null));
          it.setName(node.path("name").asText(path));
          it.setParentPath(parentOf(path));
          it.setHasChildren(node.path("hasChildren").asBoolean(false));
          JsonNode attrs = node.path("attributes");
          it.setStartDate(parseDate(attrs.path("startDate").asText(null)));
          it.setFinishDate(parseDate(attrs.path("finishDate").asText(null)));
          it.setSyncedAt(Instant.now());
          iterationRepo.save(it);
          count[0]++;
        });
    for (AdoIteration existing : iterationRepo.findByProjectIdOrderByPath(projectId)) {
      if (!seen.contains(existing.getPath())) iterationRepo.delete(existing);
    }
    return count[0];
  }

  // ── Users (members ∪ work-item people) ─────────────────────────────────────────

  private int syncUsers(
      UUID projectId, AzureBoardsPollClient.Ado ado, Map<String, AdoUser> memberUsers) {
    // 0) Always include the PAT owner — may be an org/project admin not on any team
    try {
      JsonNode me = client.getAuthenticatedUser(ado);
      String display = me.path("providerDisplayName").asText(null);
      if (display != null && !display.isBlank()) {
        String descriptor = blankToNull(me.path("subjectDescriptor").asText(""));
        boolean alreadyPresent =
            memberUsers.values().stream()
                .anyMatch(
                    u ->
                        display.equals(u.getDisplayName())
                            || (descriptor != null && descriptor.equals(u.getDescriptor())));
        if (!alreadyPresent) {
          AdoUser u = new AdoUser(projectId, display, display);
          u.setDescriptor(descriptor);
          u.setTeamMember(true);
          memberUsers.put(display, u);
          log.debug("Added PAT owner '{}' to user sync (not a member of any team)", display);
        }
      }
    } catch (Exception e) {
      log.debug("Could not fetch authenticated ADO user from connectionData: {}", e.getMessage());
    }

    // 1) upsert team members
    for (AdoUser m : memberUsers.values()) {
      AdoUser u = userRepo.findByProjectIdAndUniqueName(projectId, m.getUniqueName()).orElse(m);
      if (u != m) {
        u.setDisplayName(m.getDisplayName());
        if (m.getEmail() != null) u.setEmail(m.getEmail());
        if (m.getDescriptor() != null) u.setDescriptor(m.getDescriptor());
        u.setTeamMember(true);
      }
      u.setSyncedAt(Instant.now());
      userRepo.save(u);
    }
    // 2) derive people referenced on work items (display names only)
    Set<String> memberDisplayNames = new HashSet<>();
    memberUsers
        .values()
        .forEach(
            m -> {
              if (m.getDisplayName() != null) memberDisplayNames.add(m.getDisplayName());
            });
    for (String person : requirementRepo.findDistinctPeople(projectId)) {
      if (person == null || person.isBlank()) continue;
      if (memberDisplayNames.contains(person)) continue; // already a member
      AdoUser u =
          userRepo
              .findByProjectIdAndUniqueName(projectId, person)
              .orElseGet(() -> new AdoUser(projectId, person, person));
      u.setSeenOnWorkItems(true);
      u.setSyncedAt(Instant.now());
      userRepo.save(u);
    }
    return (int) userRepo.countByProjectId(projectId);
  }

  // ── helpers ─────────────────────────────────────────────────────────────────────

  private interface NodeVisitor {
    void visit(JsonNode node);
  }

  private void walkNodes(JsonNode node, NodeVisitor v) {
    if (node == null || node.isMissingNode() || node.isNull()) return;
    if (node.has("path")) v.visit(node);
    for (JsonNode child : node.path("children")) walkNodes(child, v);
  }

  /** Converts a classification-node path ("\Project\Area\X\Y") to AreaPath form ("Project\X\Y"). */
  private String normalizePath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return null;
    String p = rawPath.startsWith("\\") ? rawPath.substring(1) : rawPath;
    String[] parts = p.split("\\\\");
    if (parts.length >= 2
        && (parts[1].equalsIgnoreCase("Area") || parts[1].equalsIgnoreCase("Iteration"))) {
      StringBuilder sb = new StringBuilder(parts[0]);
      for (int i = 2; i < parts.length; i++) sb.append("\\").append(parts[i]);
      return sb.toString();
    }
    return p;
  }

  private String parentOf(String path) {
    int i = path.lastIndexOf('\\');
    return i > 0 ? path.substring(0, i) : null;
  }

  private LocalDate parseDate(String iso) {
    if (iso == null || iso.isBlank()) return null;
    try {
      return OffsetDateTime.parse(iso).toLocalDate();
    } catch (Exception e) {
      try {
        return LocalDate.parse(iso.substring(0, 10));
      } catch (Exception ex) {
        return null;
      }
    }
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return (b != null && !b.isBlank()) ? b : null;
  }

  /** "Frontend Squad" → "frontend-squad", "QA / Automation" → "qa-automation" */
  private static String toSlug(String name) {
    if (name == null) return "unknown";
    return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  /**
   * Ensures a platform Team entity (with slug) exists for this ADO team. Called during syncTeams so
   * automation adapters can use the slug without needing to create teams manually.
   */
  private void upsertPlatformTeam(UUID projectId, String name, String slug) {
    platformTeamRepo
        .findByProjectIdAndSlug(projectId, slug)
        .orElseGet(
            () -> {
              try {
                return platformTeamRepo.save(new Team(projectId, name, slug));
              } catch (DataIntegrityViolationException e) {
                // Race condition — another thread already created it; that's fine.
                return platformTeamRepo.findByProjectIdAndSlug(projectId, slug).orElseThrow();
              }
            });
  }
}
