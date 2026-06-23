package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestSuite;
import com.platform.core.repository.TestSuiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves a test suite to its concrete test-case set so suites can be reused when
 * building runs. STATIC suites read {@code test_suite_members}; SMART suites resolve a
 * saved filter (Area/Team via linked requirements + Iteration + status + tags) live, so
 * newly-matching cases join automatically. Also manages static membership.
 */
@Service
public class SuiteResolverService {

    private final TestSuiteRepository suiteRepo;
    private final NamedParameterJdbcTemplate njdbc;

    public SuiteResolverService(TestSuiteRepository suiteRepo, NamedParameterJdbcTemplate njdbc) {
        this.suiteRepo = suiteRepo;
        this.njdbc = njdbc;
    }

    private static final String LINK = """
            (r.id = tc.source_requirement_id
             OR r.id::text IN (SELECT jsonb_array_elements_text(tc.linked_requirement_ids)))""";

    /** Resolved case ids for one suite (static members or smart filter). */
    @Transactional(readOnly = true)
    public List<UUID> resolve(UUID projectId, TestSuite suite) {
        if (!suite.isSmart()) {
            return njdbc.query(
                    "SELECT test_case_id FROM test_suite_members WHERE suite_id = :sid",
                    new MapSqlParameterSource("sid", suite.getId()),
                    (rs, i) -> UUID.fromString(rs.getString(1)));
        }

        List<String> tags = splitTags(suite.getFilterTags());
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("pid", projectId)
                .addValue("status", suite.getFilterStatus())
                .addValue("area", suite.getAreaPath())
                .addValue("team", suite.getTeamId())
                .addValue("iter", suite.getFilterIteration())
                .addValue("tags", tags.isEmpty() ? null : tags)
                .addValue("hasTags", !tags.isEmpty());

        String sql = """
                SELECT DISTINCT tc.id
                FROM platform_test_cases tc
                WHERE tc.project_id = :pid
                  AND (:status::text IS NULL OR tc.status = :status)
                  AND (:area::text IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        WHERE r.project_id = tc.project_id AND %1$s AND r.area_path = :area))
                  AND (:team::uuid IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        JOIN ado_teams t ON t.id = :team::uuid
                        WHERE r.project_id = tc.project_id AND %1$s
                          AND t.default_area_path IS NOT NULL
                          AND starts_with(r.area_path, t.default_area_path)))
                  AND (:iter::text IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        WHERE r.project_id = tc.project_id AND %1$s AND r.iteration_path = :iter))
                  AND (:hasTags = false OR EXISTS (
                        SELECT 1 FROM test_case_tags g WHERE g.test_case_id = tc.id AND g.name IN (:tags)))
                """.formatted(LINK);
        return njdbc.query(sql, p, (rs, i) -> UUID.fromString(rs.getString(1)));
    }

    /** Union of resolved case ids across several suites (verified to belong to the project). */
    @Transactional(readOnly = true)
    public List<UUID> resolveMany(UUID projectId, List<UUID> suiteIds) {
        Set<UUID> union = new LinkedHashSet<>();
        for (UUID sid : suiteIds) {
            TestSuite suite = suiteRepo.findByProjectIdAndId(projectId, sid).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suite not found: " + sid));
            union.addAll(resolve(projectId, suite));
        }
        return new ArrayList<>(union);
    }

    @Transactional(readOnly = true)
    public int resolvedCount(UUID projectId, TestSuite suite) {
        return resolve(projectId, suite).size();
    }

    /** Resolved cases as lightweight DTOs (for previewing a suite's contents). */
    @Transactional(readOnly = true)
    public List<SelectableTestCaseDto> resolveDtos(UUID projectId, TestSuite suite) {
        List<UUID> ids = resolve(projectId, suite);
        if (ids.isEmpty()) return List.of();
        String sql = """
                SELECT tc.id::text AS id, tc.external_id, tc.title, tc.priority, tc.status,
                       COALESCE((
                           SELECT array_agg(DISTINCT r.external_id)
                           FROM platform_requirements r
                           WHERE r.project_id = tc.project_id AND %1$s AND r.external_id IS NOT NULL
                       ), ARRAY[]::text[]) AS req_exts
                FROM platform_test_cases tc
                WHERE tc.id IN (:ids)
                ORDER BY tc.title
                """.formatted(LINK);
        return njdbc.query(sql, new MapSqlParameterSource("ids", ids), (rs, i) -> {
            String[] exts = (String[]) rs.getArray("req_exts").getArray();
            return new SelectableTestCaseDto(rs.getString("id"), rs.getString("external_id"),
                    rs.getString("title"), rs.getString("priority"), rs.getString("status"),
                    exts != null ? List.of(exts) : List.of());
        });
    }

    /** Replaces the static membership of a suite. Rejected for SMART suites. */
    @Transactional
    public void replaceMembers(UUID projectId, UUID suiteId, List<UUID> caseIds) {
        TestSuite suite = suiteRepo.findByProjectIdAndId(projectId, suiteId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Suite not found: " + suiteId));
        if (suite.isSmart()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Suite is SMART (filter-driven); its members are resolved automatically");
        }
        njdbc.update("DELETE FROM test_suite_members WHERE suite_id = :sid",
                new MapSqlParameterSource("sid", suiteId));
        for (UUID cid : new LinkedHashSet<>(caseIds)) {
            njdbc.update("""
                    INSERT INTO test_suite_members (suite_id, test_case_id)
                    SELECT :sid, :cid WHERE EXISTS (
                        SELECT 1 FROM platform_test_cases WHERE id = :cid AND project_id = :pid)
                    ON CONFLICT DO NOTHING""",
                    new MapSqlParameterSource().addValue("sid", suiteId).addValue("cid", cid).addValue("pid", projectId));
        }
    }

    /** STATIC suite ids a case is a member of. */
    @Transactional(readOnly = true)
    public List<UUID> suiteIdsForCase(UUID projectId, UUID caseId) {
        return njdbc.query("""
                SELECT m.suite_id FROM test_suite_members m
                JOIN test_suites s ON s.id = m.suite_id
                WHERE m.test_case_id = :cid AND s.project_id = :pid
                """, new MapSqlParameterSource().addValue("cid", caseId).addValue("pid", projectId),
                (rs, i) -> UUID.fromString(rs.getString(1)));
    }

    /** Replaces the (static) suite membership of a single case. SMART suites are rejected. */
    @Transactional
    public void setCaseSuites(UUID projectId, UUID caseId, List<UUID> suiteIds) {
        Integer n = njdbc.queryForObject(
                "SELECT count(*) FROM platform_test_cases WHERE id = :cid AND project_id = :pid",
                new MapSqlParameterSource().addValue("cid", caseId).addValue("pid", projectId), Integer.class);
        if (n == null || n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test case not found: " + caseId);
        }
        njdbc.update("DELETE FROM test_suite_members WHERE test_case_id = :cid",
                new MapSqlParameterSource("cid", caseId));
        for (UUID sid : new LinkedHashSet<>(suiteIds)) {
            TestSuite suite = suiteRepo.findByProjectIdAndId(projectId, sid).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suite not found: " + sid));
            if (suite.isSmart()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Suite '" + suite.getName() + "' is SMART (filter-driven); cannot add cases manually");
            }
            njdbc.update("""
                    INSERT INTO test_suite_members (suite_id, test_case_id) VALUES (:sid, :cid)
                    ON CONFLICT DO NOTHING""",
                    new MapSqlParameterSource().addValue("sid", sid).addValue("cid", caseId));
        }
    }

    private static List<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
