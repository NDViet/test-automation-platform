package com.platform.agent.hub.polling;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.agent.hub.sync.RequirementSyncService;
import com.platform.common.integration.IntegrationType;
import com.platform.common.model.EdgeType;
import com.platform.common.model.LinkSubtype;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.PlatformTraceabilityEdge;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTraceabilityEdgeRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.service.ado.AzureBoardsPollClient;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Pull-based Azure DevOps Boards sync. On a configurable interval, WIQL-queries work items changed
 * since the last poll, batch-fetches them (fields + relations), upserts them into {@code
 * platform_requirements} via the merge-safe profile-driven path, then resolves the work-item
 * <b>hierarchy</b> (parent/depth) and <b>dependency links</b> (Related / Successor / Predecessor)
 * as {@code LINKED_TO} traceability edges — so downstream generation sees them via {@code
 * GraphService.buildRequirementContext}.
 *
 * <p>Config keys (ProjectIntegrationConfig.connectionParams):
 *
 * <ul>
 *   <li>{@code integrationMode} — {@code POLLING} (default) or {@code WEBHOOK} (disables the
 *       poller)
 *   <li>{@code pollIntervalMinutes} — default 15
 *   <li>{@code project} / {@code project_key} — the ADO project name
 *   <li>{@code area_path} — optional area-path filter
 *   <li>{@code lastPolledAt} — managed by the poller
 * </ul>
 */
@Service
public class AzureBoardsPollingService {

  private static final Logger log = LoggerFactory.getLogger(AzureBoardsPollingService.class);
  private static final String MODE_WEBHOOK = "WEBHOOK";
  private static final String TIER = "REQUIREMENT";
  private static final int DEFAULT_INTERVAL = 15;
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  private final ProjectIntegrationConfigRepository configRepo;
  private final AzureBoardsPollClient client;
  private final RequirementSyncService syncService;
  private final PlatformRequirementRepository requirementRepo;
  private final PlatformTraceabilityEdgeRepository edgeRepo;

  public AzureBoardsPollingService(
      ProjectIntegrationConfigRepository configRepo,
      AzureBoardsPollClient client,
      RequirementSyncService syncService,
      PlatformRequirementRepository requirementRepo,
      PlatformTraceabilityEdgeRepository edgeRepo) {
    this.configRepo = configRepo;
    this.client = client;
    this.syncService = syncService;
    this.requirementRepo = requirementRepo;
    this.edgeRepo = edgeRepo;
  }

  /** Runs every 60s; each config decides if its interval elapsed. */
  @Scheduled(fixedDelay = 60_000)
  public void pollAll() {
    List<ProjectIntegrationConfig> configs =
        configRepo.findByIntegrationTypeAndEnabled(
            IntegrationType.AZURE_DEVOPS_BOARDS.name(), true);
    for (ProjectIntegrationConfig config : configs) {
      if (MODE_WEBHOOK.equalsIgnoreCase(config.param("integrationMode"))) continue; // webhook-only
      if (!isDue(config)) continue;
      try {
        pollProject(config);
      } catch (Exception e) {
        log.error("ADO poll failed for project {}: {}", config.getProjectId(), e.getMessage(), e);
        config.recordSyncError();
        configRepo.save(config);
      }
    }
  }

  /** Polls all ADO configs of a project immediately — used by the on-demand sync endpoint. */
  public int syncNow(UUID projectId) {
    List<ProjectIntegrationConfig> configs =
        configRepo.findAllByProjectIdAndIntegrationType(
            projectId, IntegrationType.AZURE_DEVOPS_BOARDS.name());
    if (configs.isEmpty()) {
      throw new IllegalArgumentException(
          "No Azure DevOps Boards integration config for project " + projectId);
    }
    int total = 0;
    for (ProjectIntegrationConfig config : configs) total += pollProject(config);
    return total;
  }

  // -------------------------------------------------------------------------

  /** Per-item pull result: persisted requirement id + the hierarchy/dependency relations. */
  private record Pulled(
      String externalId, UUID requirementId, String parentExternalId, List<Rel> links) {}

  private record Rel(LinkSubtype subtype, String targetExternalId) {}

  protected int pollProject(ProjectIntegrationConfig config) {
    UUID projectId = config.getProjectId();
    String project = firstNonBlank(config.param("project"), config.param("project_key"));
    if (project == null) {
      log.warn("ADO poll: config for project {} has no 'project' param, skipping", projectId);
      return 0;
    }
    AzureBoardsPollClient.Ado ado = client.connect(projectId);
    if (ado == null) {
      log.warn(
          "ADO poll: no resolvable AZURE_DEVOPS_BOARDS credential for project {}, skipping",
          projectId);
      return 0;
    }

    String areaPath = config.param("area_path");
    String sinceDate = toDay(config.param("lastPolledAt")); // null on first run → full pull
    Instant pollStart = Instant.now();

    List<String> ids = client.queryWorkItemIds(ado, project, areaPath, sinceDate);
    if (ids.isEmpty()) {
      markPolled(config, pollStart);
      log.info("ADO poll: no changed work items for project {} (since {})", projectId, sinceDate);
      return 0;
    }

    List<JsonNode> items = client.getWorkItems(ado, ids);

    // Pass 1 — upsert every work item (merge-safe, profile-driven) + capture relations.
    List<Pulled> pulled = new ArrayList<>();
    for (JsonNode wi : items) {
      JsonNode fields = wi.path("fields");
      String externalId = wi.path("id").asText();
      String title = text(fields, "System.Title", "(no title)");
      String description = text(fields, "System.Description", "");
      String type = text(fields, "System.WorkItemType", "STORY");
      String state = text(fields, "System.State", null);
      Map<String, Object> raw = fieldsToMap(fields);
      int rev = wi.path("rev").asInt(0); // current revision → history-sync watermark
      if (rev > 0) raw.put("System.Rev", String.valueOf(rev));

      var up =
          syncService.upsertRequirement(config, externalId, title, description, type, raw, state);
      String parentExt = null;
      List<Rel> links = new ArrayList<>();
      for (JsonNode rel : wi.path("relations")) {
        String relType = rel.path("rel").asText("");
        String targetId = workItemIdFromUrl(rel.path("url").asText(""));
        if (targetId == null) continue; // skip non-work-item links (files, hyperlinks)
        if ("System.LinkTypes.Hierarchy-Reverse".equals(relType)) {
          parentExt = targetId; // parent → hierarchy (parent_id/depth)
        } else {
          LinkSubtype st = mapSubtype(relType);
          if (st != null) links.add(new Rel(st, targetId));
        }
      }
      pulled.add(new Pulled(externalId, up.requirement().getId(), parentExt, links));
    }

    // index for in-batch resolution
    Map<String, UUID> idToReq = new HashMap<>();
    for (Pulled p : pulled) idToReq.put(p.externalId(), p.requirementId());

    // Pass 2 — resolve hierarchy (parent_id + depth) and dependency edges (LINKED_TO).
    for (Pulled p : pulled) {
      if (p.parentExternalId() != null) {
        requirementRepo
            .findByProjectIdAndExternalId(projectId, p.parentExternalId())
            .ifPresent(
                parent ->
                    requirementRepo
                        .findById(p.requirementId())
                        .ifPresent(
                            child -> {
                              child.setHierarchy(parent.getId(), parent.getDepth() + 1);
                              requirementRepo.save(child);
                            }));
      }
      refreshLinks(projectId, p, idToReq);
    }

    markPolled(config, pollStart);
    log.info(
        "ADO poll: synced {} work item(s) for project {} (since {})",
        pulled.size(),
        projectId,
        sinceDate);
    return pulled.size();
  }

  /** Replaces this requirement's LINKED_TO edges with the freshly pulled dependency links. */
  private void refreshLinks(UUID projectId, Pulled p, Map<String, UUID> idToReq) {
    List<PlatformTraceabilityEdge> stale =
        edgeRepo.findByProjectIdAndFromIdAndFromTier(projectId, p.requirementId(), TIER).stream()
            .filter(e -> EdgeType.LINKED_TO.name().equals(e.getEdgeType()))
            .toList();
    if (!stale.isEmpty()) edgeRepo.deleteAll(stale);

    if (p.links().isEmpty()) return;
    List<PlatformTraceabilityEdge> edges = new ArrayList<>();
    for (Rel rel : p.links()) {
      UUID targetReqId = idToReq.get(rel.targetExternalId());
      if (targetReqId == null) {
        targetReqId =
            requirementRepo
                .findByProjectIdAndExternalId(projectId, rel.targetExternalId())
                .map(PlatformRequirement::getId)
                .orElse(null);
      }
      if (targetReqId == null)
        continue; // linked item not in platform yet — will link on a later poll
      edges.add(
          new PlatformTraceabilityEdge(
                  projectId, p.requirementId(), TIER, targetReqId, TIER, EdgeType.LINKED_TO.name())
              .withSubtype(rel.subtype().name()));
    }
    if (!edges.isEmpty()) edgeRepo.saveAll(edges);
  }

  private void markPolled(ProjectIntegrationConfig config, Instant pollStart) {
    Map<String, String> params =
        new LinkedHashMap<>(
            config.getConnectionParams() != null ? config.getConnectionParams() : Map.of());
    params.put("lastPolledAt", pollStart.toString());
    config.setConnectionParams(params);
    config.recordSyncSuccess();
    configRepo.save(config);
  }

  private boolean isDue(ProjectIntegrationConfig config) {
    String last = config.param("lastPolledAt");
    if (last == null || last.isBlank()) return true;
    int minutes = DEFAULT_INTERVAL;
    String s = config.param("pollIntervalMinutes");
    if (s != null) {
      try {
        minutes = Integer.parseInt(s.trim());
      } catch (NumberFormatException ignored) {
      }
    }
    try {
      return Instant.now().isAfter(Instant.parse(last).plusSeconds(minutes * 60L));
    } catch (Exception e) {
      return true;
    }
  }

  /** lastPolledAt ISO instant → yyyy-MM-dd for the WIQL ChangedDate filter (null = full pull). */
  private String toDay(String lastPolledAt) {
    if (lastPolledAt == null || lastPolledAt.isBlank()) return null;
    try {
      return DAY.format(Instant.parse(lastPolledAt));
    } catch (Exception e) {
      return null;
    }
  }

  private static LinkSubtype mapSubtype(String rel) {
    return switch (rel) {
      case "System.LinkTypes.Related" -> LinkSubtype.RELATES_TO;
      case "System.LinkTypes.Dependency-Forward" ->
          LinkSubtype.IS_DEPENDENCY_OF; // current precedes target
      case "System.LinkTypes.Dependency-Reverse" ->
          LinkSubtype.DEPENDS_ON; // current depends on target
      case "System.LinkTypes.Duplicate-Forward", "System.LinkTypes.Duplicate-Reverse" ->
          LinkSubtype.DUPLICATES;
      default ->
          null; // hierarchy-forward (children) handled via parent_id; ignore file/hyperlink rels
    };
  }

  /** Extracts the trailing work-item id from a relation url (…/workItems/123), or null. */
  private static String workItemIdFromUrl(String url) {
    if (url == null) return null;
    int idx = url.toLowerCase().indexOf("/workitems/");
    if (idx < 0) return null;
    String tail = url.substring(idx + "/workitems/".length());
    int slash = tail.indexOf('/');
    if (slash >= 0) tail = tail.substring(0, slash);
    return tail.matches("\\d+") ? tail : null;
  }

  private Map<String, Object> fieldsToMap(JsonNode fields) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (fields == null || !fields.isObject()) return out;
    fields
        .fieldNames()
        .forEachRemaining(
            fn -> {
              String v = text(fields, fn, null);
              if (v != null) out.put(fn, v);
              // Identity fields (AssignedTo/CreatedBy/ChangedBy/...) collapse to displayName above,
              // but names collide — preserve the email (uniqueName) under a companion key so the
              // user directory can key people by their unique email, not their ambiguous name.
              JsonNode n = fields.path(fn);
              if (n.isObject() && n.hasNonNull("uniqueName")) {
                String email = n.path("uniqueName").asText("");
                if (email.contains("@")) out.put(fn + ".uniqueName", email.toLowerCase());
              }
            });
    return out;
  }

  /** Reads a field value; identity fields ({displayName,...}) collapse to displayName. */
  private static String text(JsonNode fields, String key, String defaultValue) {
    JsonNode n = fields.path(key);
    if (n.isMissingNode() || n.isNull()) return defaultValue;
    if (n.isObject()) {
      if (n.has("displayName")) return n.path("displayName").asText(defaultValue);
      if (n.has("newValue")) return n.path("newValue").asText(defaultValue);
      return defaultValue;
    }
    return n.asText(defaultValue);
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return (b != null && !b.isBlank()) ? b : null;
  }
}
