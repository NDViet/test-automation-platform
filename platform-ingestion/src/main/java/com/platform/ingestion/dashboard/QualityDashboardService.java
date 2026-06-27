package com.platform.ingestion.dashboard;

import com.platform.common.integration.IntegrationType;
import com.platform.core.service.CredentialResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Aggregations for the project Quality dashboards, computed over the denormalized work-item
 * dimensions (status / priority / severity / area / iteration / assignee) and the flagged
 * quality-engineer directory ({@code ado_users.quality_role}).
 */
@Service
public class QualityDashboardService {

  private static final String OPEN_PRED = "status NOT IN ('DONE','REJECTED','CLOSED')";

  private final JdbcTemplate jdbc;
  private final CredentialResolver credentialResolver;

  public QualityDashboardService(JdbcTemplate jdbc, CredentialResolver credentialResolver) {
    this.jdbc = jdbc;
    this.credentialResolver = credentialResolver;
  }

  public record LabelValue(String label, long value) {}

  public record IterationStat(String label, long total, long open, long done) {}

  public record EngineerStat(
      String name,
      String role,
      String email,
      long defectsCreated,
      List<LabelValue> createdByStatus,
      long defectsResolved,
      long openDefects,
      long otherTotal,
      List<LabelValue> otherByStatus,
      long resolvedActual,
      long participated,
      long reopened) {}

  public record ActivityEvent(
      String externalId,
      String title,
      String issueType,
      String eventType,
      String fromValue,
      String toValue,
      String toCategory,
      String revisedAt,
      String sourceUrl) {}

  public record WorkItemRef(
      String id,
      String externalId,
      String title,
      String issueType,
      String status,
      String priority,
      String areaPath,
      String iterationPath,
      String sourceUrl) {}

  public record Overview(
      long totalDefects,
      long openDefects,
      long doneDefects,
      long blockedDefects,
      long createdLast30,
      long resolvedLast30,
      long qualityEngineers,
      long historyEvents,
      List<LabelValue> byStatus,
      List<LabelValue> byPriority,
      List<LabelValue> bySeverity,
      List<LabelValue> byArea,
      List<IterationStat> byIteration) {}

  // ── Identity keying ──────────────────────────────────────────────────────────
  // A person is matched by email (unique) AND display name, because names collide and ADO stores
  // them in different formats ("Viet Nguyen" for membership/connectionData vs "Nguyen, Viet" on
  // work items). Each predicate binds two params in order: (email, name). The email is matched
  // against the stable column (event actor_unique / the *.uniqueName companion captured at
  // ingestion); the name keeps legacy rows working until a re-poll backfills the email. Pass an
  // empty string for a missing email so it simply matches nothing.
  private static String em(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  /** Matches a work_item_events row to this person (bind: email, name). */
  private static final String EVENT_ACTOR_PRED = "(actor_unique = ? OR actor_name = ?)";

  /** Matches a platform_requirements row's creator (bind: email, name). */
  private static final String CREATED_BY_PRED =
      "(raw_upstream->>'System.CreatedBy.uniqueName' = ? OR raw_upstream->>'System.CreatedBy' = ?)";

  /** Matches a platform_requirements row's current assignee (bind: email, name). */
  private static final String ASSIGNED_PRED =
      "(raw_upstream->>'System.AssignedTo.uniqueName' = ? OR assigned_to = ?)";

  public Overview overview(UUID projectId) {
    long total = countDefects(projectId, null);
    long open = countDefects(projectId, OPEN_PRED);
    long done = countDefects(projectId, "status = 'DONE'");
    long blocked = countDefects(projectId, "status = 'BLOCKED'");

    long created30 =
        orZero(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM platform_requirements
                WHERE project_id = ? AND issue_type = 'DEFECT'
                  AND (raw_upstream->>'System.CreatedDate') IS NOT NULL
                  AND (raw_upstream->>'System.CreatedDate')::timestamptz >= now() - interval '30 days'
                """,
                Long.class,
                projectId));
    long resolved30 =
        orZero(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM platform_requirements
                WHERE project_id = ? AND issue_type = 'DEFECT' AND status = 'DONE'
                  AND (raw_upstream->>'Microsoft.VSTS.Common.ClosedDate') IS NOT NULL
                  AND (raw_upstream->>'Microsoft.VSTS.Common.ClosedDate')::timestamptz >= now() - interval '30 days'
                """,
                Long.class,
                projectId));
    long qes =
        orZero(
            jdbc.queryForObject(
                "SELECT count(*) FROM ado_users WHERE project_id = ? AND quality_role IS NOT NULL",
                Long.class,
                projectId));
    long historyEvents =
        orZero(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_item_events WHERE project_id = ?",
                Long.class,
                projectId));

    List<LabelValue> byStatus = labelValues(projectId, "status");
    List<LabelValue> byPriority = labelValues(projectId, "COALESCE(priority,'(none)')");
    List<LabelValue> bySeverity =
        jdbc.query(
            """
            SELECT COALESCE(NULLIF(regexp_replace(raw_upstream->>'Microsoft.VSTS.Common.Severity','^\\d+\\s*-\\s*',''),''),'(unset)') AS label,
                   count(*) AS value
            FROM platform_requirements
            WHERE project_id = ? AND issue_type = 'DEFECT'
            GROUP BY label ORDER BY value DESC
            """,
            (rs, i) -> new LabelValue(rs.getString("label"), rs.getLong("value")),
            projectId);
    List<LabelValue> byArea =
        jdbc.query(
            """
            SELECT COALESCE(area_path,'(none)') AS label, count(*) AS value
            FROM platform_requirements
            WHERE project_id = ? AND issue_type = 'DEFECT'
            GROUP BY label ORDER BY value DESC LIMIT 10
            """,
            (rs, i) -> new LabelValue(rs.getString("label"), rs.getLong("value")),
            projectId);

    List<IterationStat> byIteration =
        jdbc.query(
            """
            SELECT r.iteration_path AS label,
                   count(*) AS total,
                   count(*) FILTER (WHERE r.status NOT IN ('DONE','REJECTED','CLOSED')) AS open_cnt,
                   count(*) FILTER (WHERE r.status = 'DONE') AS done_cnt,
                   min(i.start_date) AS sort_date
            FROM platform_requirements r
            LEFT JOIN ado_iterations i ON i.project_id = r.project_id AND i.path = r.iteration_path
            WHERE r.project_id = ? AND r.issue_type = 'DEFECT' AND r.iteration_path IS NOT NULL
            GROUP BY r.iteration_path
            ORDER BY sort_date NULLS LAST, total DESC
            LIMIT 12
            """,
            (rs, i) ->
                new IterationStat(
                    shortIteration(rs.getString("label")),
                    rs.getLong("total"),
                    rs.getLong("open_cnt"),
                    rs.getLong("done_cnt")),
            projectId);

    return new Overview(
        total,
        open,
        done,
        blocked,
        created30,
        resolved30,
        qes,
        historyEvents,
        byStatus,
        byPriority,
        bySeverity,
        byArea,
        byIteration);
  }

  /** Recent change events made by a QE (state transitions / assignments) — activity timeline. */
  public List<ActivityEvent> activity(UUID projectId, String person, String email, int limit) {
    int safe = Math.min(Math.max(limit, 1), 200);
    String base = adoWorkItemBase(projectId);
    return jdbc.query(
        "SELECT e.external_id, e.event_type, e.from_value, e.to_value, e.to_category,"
            + " CASE WHEN e.revised_at > now() THEN NULL ELSE e.revised_at END AS revised_at,"
            + " r.title, r.issue_type"
            + " FROM work_item_events e"
            + " LEFT JOIN platform_requirements r"
            + "   ON r.project_id = e.project_id AND r.external_id = e.external_id"
            + " WHERE e.project_id = ? AND "
            + EVENT_ACTOR_PRED
            + " ORDER BY (CASE WHEN e.revised_at > now() THEN NULL ELSE e.revised_at END) DESC"
            + " NULLS LAST"
            + " LIMIT "
            + safe,
        (rs, i) ->
            new ActivityEvent(
                rs.getString("external_id"),
                rs.getString("title"),
                rs.getString("issue_type"),
                rs.getString("event_type"),
                rs.getString("from_value"),
                rs.getString("to_value"),
                rs.getString("to_category"),
                rs.getTimestamp("revised_at") == null
                    ? null
                    : rs.getTimestamp("revised_at").toInstant().toString(),
                sourceUrl(base, rs.getString("external_id"))),
        projectId,
        em(email),
        person);
  }

  /**
   * Distinct work items behind a history involvement metric: kind = resolved | participated |
   * reopened.
   */
  public List<WorkItemRef> involvementItems(
      UUID projectId, String person, String email, String kind) {
    String pred;
    switch (kind == null ? "" : kind.toLowerCase()) {
      case "resolved" ->
          pred = "event_type = 'STATE_CHANGE' AND to_category = 'DONE' AND " + EVENT_ACTOR_PRED;
      case "reopened" ->
          pred =
              "event_type = 'STATE_CHANGE' AND from_category = 'DONE' "
                  + "AND to_category IS NOT NULL AND to_category <> 'DONE' AND "
                  + EVENT_ACTOR_PRED;
      // "participated": any event by the person; ASSIGNMENT to_value is a display name (name
      // match).
      default ->
          pred = "(" + EVENT_ACTOR_PRED + " OR (event_type = 'ASSIGNMENT' AND to_value = ?))";
    }
    boolean participated = !"resolved".equalsIgnoreCase(kind) && !"reopened".equalsIgnoreCase(kind);
    String sql =
        "SELECT DISTINCT r.id, r.external_id, r.title, r.issue_type, r.status, r.priority, "
            + "r.area_path, r.iteration_path "
            + "FROM work_item_events e JOIN platform_requirements r "
            + "  ON r.project_id = e.project_id AND r.external_id = e.external_id "
            + "WHERE e.project_id = ? AND "
            + pred
            + " ORDER BY r.external_id";
    String base = adoWorkItemBase(projectId);
    Object[] args =
        participated
            ? new Object[] {projectId, em(email), person, person}
            : new Object[] {projectId, em(email), person};
    return jdbc.query(
        sql,
        (rs, i) -> {
          String ext = rs.getString("external_id");
          return new WorkItemRef(
              rs.getString("id"),
              ext,
              rs.getString("title"),
              rs.getString("issue_type"),
              rs.getString("status"),
              rs.getString("priority"),
              rs.getString("area_path"),
              rs.getString("iteration_path"),
              sourceUrl(base, ext));
        },
        args);
  }

  public List<EngineerStat> engineers(UUID projectId) {
    List<java.util.Map<String, Object>> qes =
        jdbc.queryForList(
            "SELECT display_name, quality_role, email FROM ado_users "
                + "WHERE project_id = ? AND quality_role IS NOT NULL AND display_name IS NOT NULL "
                + "ORDER BY display_name",
            projectId);

    List<EngineerStat> out = new java.util.ArrayList<>();
    for (var qe : qes) {
      String name = (String) qe.get("display_name");
      String email = em((String) qe.get("email")); // "" when absent → matches nothing
      // Defects the QE authored (created), regardless of who it's assigned to now.
      long created = count(projectId, "issue_type = 'DEFECT' AND " + CREATED_BY_PRED, email, name);
      // Status split of those created defects (e.g. Done = fixed, Open = still pending).
      List<LabelValue> createdByStatus =
          jdbc.query(
              "SELECT status AS label, count(*) AS value FROM platform_requirements "
                  + "WHERE project_id = ? AND issue_type = 'DEFECT' AND "
                  + CREATED_BY_PRED
                  + " GROUP BY status ORDER BY value DESC",
              (rs, i) -> new LabelValue(rs.getString("label"), rs.getLong("value")),
              projectId,
              email,
              name);
      // Defects done with the QE as the last/current assignee.
      long resolved =
          countAssigned(projectId, "issue_type = 'DEFECT' AND status = 'DONE'", email, name);
      long openDef =
          countAssigned(projectId, "issue_type = 'DEFECT' AND " + OPEN_PRED, email, name);
      long other = countAssigned(projectId, "issue_type <> 'DEFECT'", email, name);
      // Status breakdown of their non-defect work.
      List<LabelValue> otherByStatus =
          jdbc.query(
              "SELECT status AS label, count(*) AS value FROM platform_requirements "
                  + "WHERE project_id = ? AND "
                  + ASSIGNED_PRED
                  + " AND issue_type <> 'DEFECT' "
                  + "GROUP BY status ORDER BY value DESC",
              (rs, i) -> new LabelValue(rs.getString("label"), rs.getLong("value")),
              projectId,
              email,
              name);
      // History-derived involvement (0 until work-item history is synced).
      long resolvedActual =
          orZero(
              jdbc.queryForObject(
                  "SELECT count(DISTINCT external_id) FROM work_item_events WHERE project_id = ?"
                      + " AND event_type = 'STATE_CHANGE' AND to_category = 'DONE' AND "
                      + EVENT_ACTOR_PRED,
                  Long.class,
                  projectId,
                  email,
                  name));
      // "participated": any event by the person; ASSIGNMENT to_value is a display name (name
      // match).
      long participated =
          orZero(
              jdbc.queryForObject(
                  "SELECT count(DISTINCT external_id) FROM work_item_events WHERE project_id = ?"
                      + " AND ("
                      + EVENT_ACTOR_PRED
                      + " OR (event_type = 'ASSIGNMENT' AND to_value = ?))",
                  Long.class,
                  projectId,
                  email,
                  name,
                  name));
      long reopened =
          orZero(
              jdbc.queryForObject(
                  "SELECT count(DISTINCT external_id) FROM work_item_events WHERE project_id = ?"
                      + " AND event_type = 'STATE_CHANGE' AND from_category = 'DONE' AND"
                      + " to_category IS NOT NULL AND to_category <> 'DONE' AND "
                      + EVENT_ACTOR_PRED,
                  Long.class,
                  projectId,
                  email,
                  name));

      out.add(
          new EngineerStat(
              name,
              (String) qe.get("quality_role"),
              (String) qe.get("email"),
              created,
              createdByStatus,
              resolved,
              openDef,
              other,
              otherByStatus,
              resolvedActual,
              participated,
              reopened));
    }
    out.sort(
        (a, b) ->
            Long.compare(
                b.defectsCreated() + b.defectsResolved() + b.openDefects() + b.otherTotal(),
                a.defectsCreated() + a.defectsResolved() + a.openDefects() + a.otherTotal()));
    return out;
  }

  /** {@code pred} embeds {@link #CREATED_BY_PRED} (binds email, name). */
  private long count(UUID projectId, String pred, String email, String name) {
    return orZero(
        jdbc.queryForObject(
            "SELECT count(*) FROM platform_requirements WHERE project_id = ? AND " + pred,
            Long.class,
            projectId,
            email,
            name));
  }

  private long countAssigned(UUID projectId, String pred, String email, String name) {
    return orZero(
        jdbc.queryForObject(
            "SELECT count(*) FROM platform_requirements WHERE project_id = ? AND "
                + ASSIGNED_PRED
                + " AND "
                + pred,
            Long.class,
            projectId,
            email,
            name));
  }

  /**
   * The work items behind an engineer's KPI cell.
   *
   * @param attribution {@code creator} matches {@code System.CreatedBy}; anything else matches the
   *     last/current assignee ({@code assigned_to}).
   * @param type {@code defect} | {@code other} (non-defect) | else any type
   * @param status {@code open} (not DONE/REJECTED/CLOSED) | {@code done} | an exact status value |
   *     else any status
   */
  public List<WorkItemRef> workItems(
      UUID projectId, String person, String email, String attribution, String type, String status) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT id, external_id, title, issue_type, status, priority, area_path, iteration_path"
                + " FROM platform_requirements WHERE project_id = ? AND ");
    List<Object> args = new java.util.ArrayList<>();
    args.add(projectId);

    sql.append("creator".equalsIgnoreCase(attribution) ? CREATED_BY_PRED : ASSIGNED_PRED);
    args.add(em(email));
    args.add(person);

    if ("defect".equalsIgnoreCase(type)) sql.append(" AND issue_type = 'DEFECT'");
    else if ("other".equalsIgnoreCase(type)) sql.append(" AND issue_type <> 'DEFECT'");

    if ("open".equalsIgnoreCase(status)) sql.append(" AND ").append(OPEN_PRED);
    else if ("done".equalsIgnoreCase(status)) sql.append(" AND status = 'DONE'");
    else if (status != null && !status.isBlank() && !"any".equalsIgnoreCase(status)) {
      sql.append(" AND status = ?");
      args.add(status);
    }
    sql.append(" ORDER BY updated_at DESC LIMIT 500");

    String base = adoWorkItemBase(projectId);
    return jdbc.query(
        sql.toString(),
        (rs, i) -> {
          String ext = rs.getString("external_id");
          return new WorkItemRef(
              rs.getString("id"),
              ext,
              rs.getString("title"),
              rs.getString("issue_type"),
              rs.getString("status"),
              rs.getString("priority"),
              rs.getString("area_path"),
              rs.getString("iteration_path"),
              sourceUrl(base, ext));
        },
        args.toArray());
  }

  /** ADO web base ("https://dev.azure.com/{org}/_workitems/edit/") for the project, or null. */
  private String adoWorkItemBase(UUID projectId) {
    return credentialResolver
        .resolve(projectId, IntegrationType.AZURE_DEVOPS_BOARDS.name())
        .map(
            cred -> {
              String org = cred.param("organization");
              if (org == null || org.isBlank()) return null;
              String host =
                  (cred.baseUrl() != null && !cred.baseUrl().isBlank())
                      ? (cred.baseUrl().endsWith("/")
                          ? cred.baseUrl().substring(0, cred.baseUrl().length() - 1)
                          : cred.baseUrl())
                      : "https://dev.azure.com/" + org.trim();
              return host + "/_workitems/edit/";
            })
        .orElse(null);
  }

  private static String sourceUrl(String base, String externalId) {
    if (base == null || externalId == null || !externalId.matches("\\d+")) return null;
    return base + externalId;
  }

  // ── helpers ─────────────────────────────────────────────────────────────────────

  private long countDefects(UUID projectId, String extraPred) {
    String sql =
        "SELECT count(*) FROM platform_requirements WHERE project_id = ? AND issue_type = 'DEFECT'"
            + (extraPred != null ? " AND " + extraPred : "");
    return orZero(jdbc.queryForObject(sql, Long.class, projectId));
  }

  private List<LabelValue> labelValues(UUID projectId, String dim) {
    return jdbc.query(
        "SELECT "
            + dim
            + " AS label, count(*) AS value FROM platform_requirements "
            + "WHERE project_id = ? AND issue_type = 'DEFECT' GROUP BY label ORDER BY value DESC",
        (rs, i) -> new LabelValue(rs.getString("label"), rs.getLong("value")),
        projectId);
  }

  /** "Product House\\2026 Q2\\Sprint 5" → "2026 Q2 / Sprint 5" (drop the project root). */
  private static String shortIteration(String path) {
    if (path == null) return "(none)";
    String[] parts = path.split("\\\\");
    if (parts.length <= 1) return path;
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i < parts.length; i++) {
      if (i > 1) sb.append(" / ");
      sb.append(parts[i]);
    }
    return sb.toString();
  }

  private static long orZero(Long v) {
    return v == null ? 0 : v;
  }
}
