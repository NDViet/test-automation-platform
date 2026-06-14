package com.platform.ingestion.dashboard;

import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.service.CredentialResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Productivity / cycle-time monitoring. Cycle time here is the <b>running</b> age of work that
 * has started but isn't completed: {@code now − firstInProgressTransition} (from work-item
 * history). Tickets whose running cycle time exceeds the leadership threshold (default 24h,
 * configurable per project) are flagged "needs action", rolled up by Area.
 */
@Service
public class ProductivityService {

    private static final double DEFAULT_THRESHOLD_HOURS = 24.0;
    private static final String THRESHOLD_KEY = "cycleTimeThresholdHours";
    // Active = started and not completed. An item moved back to OPEN/backlog is NOT active WIP.
    private static final String ACTIVE = "r.status IN ('IN_PROGRESS','BLOCKED')";

    /**
     * CTE yielding {@code started(external_id, started_at)} = start of the item's CURRENT active
     * streak: the earliest active (IN_PROGRESS/BLOCKED) transition that occurs after the most
     * recent move back to a non-active state (OPEN/DONE/REJECTED). So an item parked back in
     * backlog and restarted is timed from the restart, not its first-ever touch. Two bind
     * parameters: projectId (last_inactive), projectId (started).
     */
    private static final String STARTED_CTE = """
            WITH last_inactive AS (
                SELECT external_id, max(revised_at) AS inactive_at
                FROM work_item_events
                WHERE project_id = ? AND event_type = 'STATE_CHANGE'
                  AND to_category IN ('OPEN','DONE','REJECTED') AND revised_at <= now()
                GROUP BY external_id
            ),
            started AS (
                SELECT e.external_id, min(e.revised_at) AS started_at
                FROM work_item_events e
                LEFT JOIN last_inactive li ON li.external_id = e.external_id
                WHERE e.project_id = ? AND e.event_type = 'STATE_CHANGE'
                  AND e.to_category IN ('IN_PROGRESS','BLOCKED') AND e.revised_at <= now()
                  AND (li.inactive_at IS NULL OR e.revised_at > li.inactive_at)
                GROUP BY e.external_id
            )
            """;

    private final JdbcTemplate jdbc;
    private final ProjectIntegrationConfigRepository configRepo;
    private final CredentialResolver credentialResolver;

    public ProductivityService(JdbcTemplate jdbc, ProjectIntegrationConfigRepository configRepo,
                               CredentialResolver credentialResolver) {
        this.jdbc = jdbc;
        this.configRepo = configRepo;
        this.credentialResolver = credentialResolver;
    }

    public record AreaProductivity(String area, long wip, long overThreshold,
                                   Double avgHours, Double maxHours) {}
    public record Overview(double thresholdHours, long totalWip, long totalOver, long areasAffected,
                           List<AreaProductivity> areas) {}
    public record OverThresholdItem(String id, String externalId, String title, String issueType,
                                    String status, String assignedTo, String areaPath,
                                    Double cycleHours, String startedAt, String sourceUrl) {}

    public record LeadAreaStat(String area, long completed, Double avgHours, Double maxHours) {}
    public record LeadOverview(long totalCompleted, Double avgHours, Double maxHours, List<LeadAreaStat> areas) {}
    public record LeadItem(String id, String externalId, String title, String issueType,
                           String assignedTo, String areaPath, Double leadHours,
                           String createdDate, String completedAt, String sourceUrl) {}

    /** Effective leadership cycle-time threshold (hours) for a project. */
    public double thresholdHours(UUID projectId) {
        return configRepo.findAllByProjectIdAndIntegrationType(projectId, IntegrationType.AZURE_DEVOPS_BOARDS.name())
                .stream()
                .map(c -> c.param(THRESHOLD_KEY))
                .filter(v -> v != null && !v.isBlank())
                .map(v -> { try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return null; } })
                .filter(v -> v != null && v > 0)
                .findFirst().orElse(DEFAULT_THRESHOLD_HOURS);
    }

    /** Persists the threshold onto the project's ADO integration config(s). */
    @Transactional
    public double setThresholdHours(UUID projectId, double hours) {
        double v = Math.max(0.5, hours);
        List<ProjectIntegrationConfig> configs = configRepo.findAllByProjectIdAndIntegrationType(
                projectId, IntegrationType.AZURE_DEVOPS_BOARDS.name());
        if (configs.isEmpty()) throw new IllegalStateException("No Azure DevOps Boards integration config for project");
        for (ProjectIntegrationConfig c : configs) {
            Map<String, String> params = new LinkedHashMap<>(
                    c.getConnectionParams() != null ? c.getConnectionParams() : Map.of());
            params.put(THRESHOLD_KEY, trimNumber(v));
            c.setConnectionParams(params);
            configRepo.save(c);
        }
        return v;
    }

    public Overview byArea(UUID projectId) {
        double threshold = thresholdHours(projectId);
        List<AreaProductivity> areas = jdbc.query(STARTED_CTE + """
                SELECT COALESCE(r.area_path, '(no area)') AS area,
                       count(*) AS wip,
                       count(*) FILTER (WHERE EXTRACT(EPOCH FROM (now() - s.started_at)) / 3600.0 > ?) AS over_thr,
                       round(avg(EXTRACT(EPOCH FROM (now() - s.started_at)) / 3600.0)::numeric, 1) AS avg_h,
                       round(max(EXTRACT(EPOCH FROM (now() - s.started_at)) / 3600.0)::numeric, 1) AS max_h
                FROM platform_requirements r
                JOIN started s ON s.external_id = r.external_id
                WHERE r.project_id = ? AND %s
                GROUP BY area
                ORDER BY over_thr DESC, wip DESC
                """.formatted(ACTIVE),
                (rs, i) -> new AreaProductivity(rs.getString("area"), rs.getLong("wip"), rs.getLong("over_thr"),
                        toDouble(rs.getBigDecimal("avg_h")), toDouble(rs.getBigDecimal("max_h"))),
                projectId, projectId, threshold, projectId);

        long totalWip = areas.stream().mapToLong(AreaProductivity::wip).sum();
        long totalOver = areas.stream().mapToLong(AreaProductivity::overThreshold).sum();
        long affected = areas.stream().filter(a -> a.overThreshold() > 0).count();
        return new Overview(threshold, totalWip, totalOver, affected, areas);
    }

    /**
     * Started-but-not-completed tickets (optionally within one area), worst first.
     * @param overOnly when true, only those past the cycle-time threshold.
     */
    public List<OverThresholdItem> wipItems(UUID projectId, String area, boolean overOnly) {
        double threshold = thresholdHours(projectId);
        String base = adoWorkItemBase(projectId);
        List<Object> args = new java.util.ArrayList<>();
        args.add(projectId); args.add(projectId); args.add(projectId);   // CTE (x2), then main WHERE
        StringBuilder sql = new StringBuilder(STARTED_CTE + """
                SELECT r.id, r.external_id, r.title, r.issue_type, r.status, r.assigned_to, r.area_path,
                       round((EXTRACT(EPOCH FROM (now() - s.started_at)) / 3600.0)::numeric, 1) AS cycle_h,
                       s.started_at
                FROM platform_requirements r
                JOIN started s ON s.external_id = r.external_id
                WHERE r.project_id = ? AND %s
                """.formatted(ACTIVE));
        if (overOnly) { sql.append(" AND EXTRACT(EPOCH FROM (now() - s.started_at)) / 3600.0 > ?"); args.add(threshold); }
        appendAreaFilter(sql, args, area);
        sql.append(" ORDER BY cycle_h DESC LIMIT 500");

        return jdbc.query(sql.toString(), (rs, i) -> {
            String ext = rs.getString("external_id");
            return new OverThresholdItem(rs.getString("id"), ext, rs.getString("title"),
                    rs.getString("issue_type"), rs.getString("status"), rs.getString("assigned_to"),
                    rs.getString("area_path"), toDouble(rs.getBigDecimal("cycle_h")),
                    rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant().toString(),
                    sourceUrl(base, ext));
        }, args.toArray());
    }

    // ── Lead time (created → completed) ───────────────────────────────────────────

    /** Lead time = completion date − creation date, for completed items, rolled up by area. */
    public LeadOverview leadByArea(UUID projectId) {
        List<LeadAreaStat> areas = jdbc.query("""
                WITH done AS (
                    SELECT external_id, max(revised_at) AS done_at
                    FROM work_item_events
                    WHERE project_id = ? AND event_type = 'STATE_CHANGE' AND to_category = 'DONE'
                      AND revised_at <= now()
                    GROUP BY external_id
                )
                SELECT COALESCE(r.area_path, '(no area)') AS area,
                       count(*) AS completed,
                       round(avg(EXTRACT(EPOCH FROM (d.done_at - r.created_date)) / 3600.0)::numeric, 1) AS avg_h,
                       round(max(EXTRACT(EPOCH FROM (d.done_at - r.created_date)) / 3600.0)::numeric, 1) AS max_h
                FROM platform_requirements r
                JOIN done d ON d.external_id = r.external_id
                WHERE r.project_id = ? AND r.status = 'DONE'
                  AND r.created_date IS NOT NULL AND d.done_at >= r.created_date
                GROUP BY area
                ORDER BY completed DESC
                """,
                (rs, i) -> new LeadAreaStat(rs.getString("area"), rs.getLong("completed"),
                        toDouble(rs.getBigDecimal("avg_h")), toDouble(rs.getBigDecimal("max_h"))),
                projectId, projectId);

        Map<String, Object> totals = jdbc.queryForMap("""
                WITH done AS (
                    SELECT external_id, max(revised_at) AS done_at
                    FROM work_item_events
                    WHERE project_id = ? AND event_type = 'STATE_CHANGE' AND to_category = 'DONE'
                      AND revised_at <= now()
                    GROUP BY external_id
                )
                SELECT count(*) AS completed,
                       round(avg(EXTRACT(EPOCH FROM (d.done_at - r.created_date)) / 3600.0)::numeric, 1) AS avg_h,
                       round(max(EXTRACT(EPOCH FROM (d.done_at - r.created_date)) / 3600.0)::numeric, 1) AS max_h
                FROM platform_requirements r
                JOIN done d ON d.external_id = r.external_id
                WHERE r.project_id = ? AND r.status = 'DONE'
                  AND r.created_date IS NOT NULL AND d.done_at >= r.created_date
                """, projectId, projectId);
        return new LeadOverview(((Number) totals.get("completed")).longValue(),
                toDouble((java.math.BigDecimal) totals.get("avg_h")),
                toDouble((java.math.BigDecimal) totals.get("max_h")), areas);
    }

    /** Completed items with their lead time (optionally within one area), longest first. */
    public List<LeadItem> leadItems(UUID projectId, String area) {
        String base = adoWorkItemBase(projectId);
        List<Object> args = new java.util.ArrayList<>();
        args.add(projectId); args.add(projectId);
        StringBuilder sql = new StringBuilder("""
                WITH done AS (
                    SELECT external_id, max(revised_at) AS done_at
                    FROM work_item_events
                    WHERE project_id = ? AND event_type = 'STATE_CHANGE' AND to_category = 'DONE'
                      AND revised_at <= now()
                    GROUP BY external_id
                )
                SELECT r.id, r.external_id, r.title, r.issue_type, r.assigned_to, r.area_path,
                       round((EXTRACT(EPOCH FROM (d.done_at - r.created_date)) / 3600.0)::numeric, 1) AS lead_h,
                       r.created_date, d.done_at
                FROM platform_requirements r
                JOIN done d ON d.external_id = r.external_id
                WHERE r.project_id = ? AND r.status = 'DONE'
                  AND r.created_date IS NOT NULL AND d.done_at >= r.created_date
                """);
        appendAreaFilter(sql, args, area);
        sql.append(" ORDER BY lead_h DESC LIMIT 500");

        return jdbc.query(sql.toString(), (rs, i) -> {
            String ext = rs.getString("external_id");
            return new LeadItem(rs.getString("id"), ext, rs.getString("title"), rs.getString("issue_type"),
                    rs.getString("assigned_to"), rs.getString("area_path"), toDouble(rs.getBigDecimal("lead_h")),
                    rs.getTimestamp("created_date") == null ? null : rs.getTimestamp("created_date").toInstant().toString(),
                    rs.getTimestamp("done_at") == null ? null : rs.getTimestamp("done_at").toInstant().toString(),
                    sourceUrl(base, ext));
        }, args.toArray());
    }

    /** Appends an optional area filter (matches the '(no area)' bucket to NULL). */
    private void appendAreaFilter(StringBuilder sql, List<Object> args, String area) {
        if ("(no area)".equals(area)) {
            sql.append(" AND r.area_path IS NULL");
        } else if (area != null && !area.isBlank()) {
            sql.append(" AND r.area_path = ?");
            args.add(area);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    private String adoWorkItemBase(UUID projectId) {
        return credentialResolver.resolve(projectId, IntegrationType.AZURE_DEVOPS_BOARDS.name())
                .map(cred -> {
                    String org = cred.param("organization");
                    if (org == null || org.isBlank()) return null;
                    String host = (cred.baseUrl() != null && !cred.baseUrl().isBlank())
                            ? (cred.baseUrl().endsWith("/") ? cred.baseUrl().substring(0, cred.baseUrl().length() - 1) : cred.baseUrl())
                            : "https://dev.azure.com/" + org.trim();
                    return host + "/_workitems/edit/";
                })
                .orElse(null);
    }

    private static String sourceUrl(String base, String externalId) {
        if (base == null || externalId == null || !externalId.matches("\\d+")) return null;
        return base + externalId;
    }

    private static Double toDouble(java.math.BigDecimal v) { return v == null ? null : v.doubleValue(); }

    private static String trimNumber(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
