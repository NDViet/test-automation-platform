package com.platform.agent.hub.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.agent.hub.polling.AzureBoardsPollClient;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.domain.WorkItemEvent;
import com.platform.core.mapping.MappingProfileApplier;
import com.platform.core.mapping.MappingRules;
import com.platform.core.mapping.MappingRulesProvider;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.repository.WorkItemEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;

/**
 * Extracts work-item change history from the Azure DevOps Work Item Updates API into
 * {@code work_item_events} — capturing who made each state transition / assignment change.
 * Powers true QE-involvement metrics (resolved-by, participated, reopened, timeline).
 *
 * <p>Incremental: only calls ADO for items whose current {@code System.Rev} is ahead of the
 * last-processed {@code history_rev}, so re-runs are cheap.</p>
 */
@Service
public class WorkItemHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(WorkItemHistorySyncService.class);
    private static final Instant FAR_FUTURE = Instant.parse("3000-01-01T00:00:00Z");

    private final ProjectIntegrationConfigRepository configRepo;
    private final AzureBoardsPollClient client;
    private final PlatformRequirementRepository requirementRepo;
    private final WorkItemEventRepository eventRepo;
    private final MappingRulesProvider rulesProvider;
    private final MappingProfileApplier applier;

    /** Projects with an in-flight history sync (guards against concurrent/duplicate runs). */
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wi-history-sync"); t.setDaemon(true); return t;
    });

    public WorkItemHistorySyncService(ProjectIntegrationConfigRepository configRepo,
                                      AzureBoardsPollClient client,
                                      PlatformRequirementRepository requirementRepo,
                                      WorkItemEventRepository eventRepo,
                                      MappingRulesProvider rulesProvider,
                                      MappingProfileApplier applier) {
        this.configRepo      = configRepo;
        this.client          = client;
        this.requirementRepo = requirementRepo;
        this.eventRepo       = eventRepo;
        this.rulesProvider   = rulesProvider;
        this.applier         = applier;
    }

    public boolean isRunning(UUID projectId) { return running.contains(projectId); }

    /**
     * Launches the history sync in the background (it makes one ADO call per work item, so a
     * full first run takes minutes). Returns {@code false} if a sync is already in flight.
     */
    public boolean startSync(UUID projectId, List<String> issueTypes) {
        if (!running.add(projectId)) return false;          // already running
        executor.submit(() -> {
            try { runSync(projectId, issueTypes); }
            catch (Exception e) { log.error("history sync failed for project {}: {}", projectId, e.getMessage(), e); }
            finally { running.remove(projectId); }
        });
        return true;
    }

    /** The worker. NOT one big transaction — each item commits on its own so progress is durable. */
    private void runSync(UUID projectId, List<String> issueTypes) {
        List<ProjectIntegrationConfig> configs =
                configRepo.findAllByProjectIdAndIntegrationType(projectId, IntegrationType.AZURE_DEVOPS_BOARDS.name());
        if (configs.isEmpty()) { log.warn("history sync: no ADO config for project {}", projectId); return; }
        String project = null;
        for (ProjectIntegrationConfig c : configs) {
            project = firstNonBlank(c.param("project"), c.param("project_key"));
            if (project != null) break;
        }
        if (project == null) { log.warn("history sync: ADO config has no 'project' param for {}", projectId); return; }

        AzureBoardsPollClient.Ado ado = client.connect(projectId);
        if (ado == null) { log.warn("history sync: no resolvable credential for {}", projectId); return; }

        MappingRules rules = rulesProvider.effectiveForProject(projectId);
        List<PlatformRequirement> items = requirementRepo.findByProjectIdAndIssueTypeIn(projectId, issueTypes);

        int fetched = 0, added = 0;
        for (PlatformRequirement r : items) {
            String externalId = r.getExternalId();
            if (externalId == null || !externalId.matches("\\d+")) continue;
            Integer currentRev = parseInt(str(r.getRawUpstream(), "System.Rev"));
            if (currentRev != null && currentRev.equals(r.getHistoryRev())) continue;   // unchanged → skip ADO call

            try {
                JsonNode updates = client.getWorkItemUpdates(ado, project, externalId);   // ADO call (no tx held)
                added += ingest(projectId, externalId, r.getIssueType(), updates, rules);  // per-event saves commit individually
                int maxRev = 0;
                for (JsonNode u : updates.path("value")) maxRev = Math.max(maxRev, u.path("rev").asInt(u.path("id").asInt(0)));
                // Watermark = latest revision seen (equals System.Rev), so future runs can skip unchanged items.
                r.setHistoryRev(maxRev > 0 ? maxRev : currentRev);
                requirementRepo.save(r);
                fetched++;
            } catch (Exception e) {
                log.warn("history fetch failed for work item {}: {}", externalId, e.getMessage());
            }
        }
        log.info("Work-item history sync for project {}: {} considered, {} fetched, {} events added",
                projectId, items.size(), fetched, added);
    }

    /** Parses one work item's updates payload into state-change / assignment events. */
    private int ingest(UUID projectId, String externalId, String issueType, JsonNode updates, MappingRules rules) {
        int added = 0;
        for (JsonNode u : updates.path("value")) {
            int rev = u.path("rev").asInt(u.path("id").asInt(0));
            if (rev <= 0) continue;
            JsonNode by = u.path("revisedBy");
            String actorName   = blankToNull(by.path("displayName").asText(""));
            String actorUnique = blankToNull(by.path("uniqueName").asText(""));
            JsonNode fields    = u.path("fields");
            Instant revisedAt  = parseInstant(u.path("revisedDate").asText(null));
            // ADO sets the latest revision's revisedDate to a 9999 sentinel — fall back to ChangedDate.
            if (revisedAt == null || revisedAt.isAfter(FAR_FUTURE)) {
                revisedAt = parseInstant(fields.path("System.ChangedDate").path("newValue").asText(null));
            }

            // State transition
            JsonNode state = fields.path("System.State");
            if (!state.isMissingNode()) {
                String from = blankToNull(state.path("oldValue").asText(""));
                String to   = blankToNull(state.path("newValue").asText(""));
                if (to != null && !to.equals(from)) {
                    added += save(new WorkItemEvent(projectId, externalId, issueType, rev, "STATE_CHANGE",
                            "System.State", from, to,
                            from == null ? null : applier.status(rules, from).value(),
                            applier.status(rules, to).value(),
                            actorName, actorUnique, revisedAt));
                }
            }
            // Assignment change
            JsonNode assign = fields.path("System.AssignedTo");
            if (!assign.isMissingNode()) {
                String from = identity(assign.path("oldValue"));
                String to   = identity(assign.path("newValue"));
                if (!java.util.Objects.equals(from, to)) {
                    added += save(new WorkItemEvent(projectId, externalId, issueType, rev, "ASSIGNMENT",
                            "System.AssignedTo", from, to, null, null, actorName, actorUnique, revisedAt));
                }
            }
        }
        return added;
    }

    private int save(WorkItemEvent e) {
        if (eventRepo.existsByProjectIdAndExternalIdAndRevAndEventTypeAndField(
                e.getProjectId(), e.getExternalId(), e.getRev(), e.getEventType(), e.getField())) {
            return 0;
        }
        eventRepo.save(e);
        return 1;
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    private static String identity(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isObject()) return blankToNull(node.path("displayName").asText(""));
        return blankToNull(node.asText(""));
    }

    private static String str(Map<String, Object> raw, String key) {
        if (raw == null) return null;
        Object v = raw.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); } catch (Exception e) {
            try { return java.time.OffsetDateTime.parse(s).toInstant(); } catch (Exception ex) { return null; }
        }
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }
}
