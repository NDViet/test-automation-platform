package com.platform.ingestion.management.tcm;

import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseStep;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseStepRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class TestCaseManagementService {

    private final PlatformTestCaseRepository testCaseRepo;
    private final TestCaseStepRepository stepRepo;
    private final NamedParameterJdbcTemplate njdbc;
    private final SuiteResolverService suiteResolver;
    private final com.platform.core.repository.TestSuiteRepository suiteRepo;

    public TestCaseManagementService(PlatformTestCaseRepository testCaseRepo,
                                     TestCaseStepRepository stepRepo,
                                     NamedParameterJdbcTemplate njdbc,
                                     SuiteResolverService suiteResolver,
                                     com.platform.core.repository.TestSuiteRepository suiteRepo) {
        this.testCaseRepo  = testCaseRepo;
        this.stepRepo      = stepRepo;
        this.njdbc         = njdbc;
        this.suiteResolver = suiteResolver;
        this.suiteRepo     = suiteRepo;
    }

    /** STATIC suite ids the case belongs to. */
    @Transactional(readOnly = true)
    public List<String> caseSuites(UUID projectId, UUID caseId) {
        return suiteResolver.suiteIdsForCase(projectId, caseId).stream().map(UUID::toString).toList();
    }

    /** Replaces the case's (static) suite memberships. */
    public void setCaseSuites(UUID projectId, UUID caseId, List<String> suiteIds) {
        List<UUID> ids = suiteIds == null ? List.of() : suiteIds.stream().map(UUID::fromString).toList();
        suiteResolver.setCaseSuites(projectId, caseId, ids);
    }

    /**
     * Scope-filtered, searchable picker for run creation. Narrows test cases by the
     * monitoring scope (area / iteration / team — resolved through each case's LINKED
     * REQUIREMENTS) and a free-text query that matches the test-case title, the
     * test-case external id, or any linked requirement's external id.
     *
     * <p>Necessary because the flat case list does not scale: this lets a user pick the
     * relevant subset for a Sprint/Area/Team run without scrolling thousands of cases.</p>
     */
    @Transactional(readOnly = true)
    public List<SelectableTestCaseDto> selectable(UUID projectId, String status, String area,
                                                  String iteration, String teamId, String q) {
        // A case's requirement set = source_requirement_id ∪ linked_requirement_ids (jsonb).
        String linkPredicate = """
                (r.id = tc.source_requirement_id
                 OR r.id::text IN (SELECT jsonb_array_elements_text(tc.linked_requirement_ids)))""";

        String sql = """
                SELECT tc.id::text AS id, tc.external_id, tc.title, tc.priority, tc.status,
                       COALESCE((
                           SELECT array_agg(DISTINCT r.external_id)
                           FROM platform_requirements r
                           WHERE r.project_id = tc.project_id AND %1$s AND r.external_id IS NOT NULL
                       ), ARRAY[]::text[]) AS req_exts
                FROM platform_test_cases tc
                WHERE tc.project_id = :pid
                  AND (:status::text IS NULL OR tc.status = :status)
                  AND (:area::text IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        WHERE r.project_id = tc.project_id AND %1$s AND r.area_path = :area))
                  AND (:iter::text IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        WHERE r.project_id = tc.project_id AND %1$s AND r.iteration_path = :iter))
                  AND (:team::uuid IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        JOIN ado_teams t ON t.id = :team::uuid
                        WHERE r.project_id = tc.project_id AND %1$s
                          AND t.default_area_path IS NOT NULL
                          AND starts_with(r.area_path, t.default_area_path)))
                  AND (:q::text IS NULL
                        OR tc.title ILIKE :qlike
                        OR tc.external_id ILIKE :qlike
                        OR EXISTS (
                            SELECT 1 FROM platform_requirements r
                            WHERE r.project_id = tc.project_id AND %1$s AND r.external_id ILIKE :qlike))
                ORDER BY tc.created_at DESC
                LIMIT 300
                """.formatted(linkPredicate);

        String qTrim = (q != null && !q.isBlank()) ? q.trim() : null;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("pid", projectId)
                .addValue("status", (status != null && !status.isBlank()) ? status : null)
                .addValue("area", (area != null && !area.isBlank()) ? area : null)
                .addValue("iter", (iteration != null && !iteration.isBlank()) ? iteration : null)
                .addValue("team", (teamId != null && !teamId.isBlank()) ? teamId : null)
                .addValue("q", qTrim)
                .addValue("qlike", qTrim != null ? "%" + qTrim + "%" : null);

        return njdbc.query(sql, p, (rs, i) -> {
            String[] exts = (String[]) rs.getArray("req_exts").getArray();
            return new SelectableTestCaseDto(
                    rs.getString("id"), rs.getString("external_id"), rs.getString("title"),
                    rs.getString("priority"), rs.getString("status"),
                    exts != null ? List.of(exts) : List.of());
        });
    }

    @Transactional(readOnly = true)
    public List<ManagedTestCaseDto> list(UUID projectId, String status, String suiteId, String search) {
        return list(projectId, status, suiteId, search, null, null, null);
    }

    /** As above, additionally scoped by ADO Area / Team / Iteration (via each case's linked requirements). */
    @Transactional(readOnly = true)
    public List<ManagedTestCaseDto> list(UUID projectId, String status, String suiteId, String search,
                                         String area, String teamId, String iteration) {
        List<PlatformTestCase> cases;

        if (search != null && !search.isBlank()) {
            cases = testCaseRepo.searchByProjectId(projectId, search.trim());
            if (status != null) {
                String s = status;
                cases = cases.stream().filter(tc -> s.equals(tc.getStatus())).toList();
            }
        } else if (status != null) {
            cases = testCaseRepo.findByProjectIdAndStatus(projectId, status);
        } else {
            cases = testCaseRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
        }

        // Filter to a suite's resolved cases (static membership or smart filter).
        if (suiteId != null && !suiteId.isBlank()) {
            Set<UUID> members = suiteRepo.findByProjectIdAndId(projectId, UUID.fromString(suiteId))
                    .map(s -> new java.util.HashSet<>(suiteResolver.resolve(projectId, s)))
                    .orElseGet(java.util.HashSet::new);
            cases = cases.stream().filter(tc -> members.contains(tc.getId())).toList();
        }

        // Scope by Area / Team / Iteration (resolved through linked requirements) when requested.
        Set<UUID> scoped = scopedCaseIds(projectId, area, teamId, iteration);
        if (scoped != null) {
            cases = cases.stream().filter(tc -> scoped.contains(tc.getId())).toList();
        }

        return cases.stream()
                .map(tc -> ManagedTestCaseDto.from(tc, loadSteps(tc.getId())))
                .toList();
    }

    /**
     * IDs of test cases whose linked requirements fall in the given Area and/or the
     * given Team's owned area subtree. Returns {@code null} when neither is requested
     * (no scoping), else the (possibly empty) matching set.
     */
    private Set<UUID> scopedCaseIds(UUID projectId, String area, String teamId, String iteration) {
        boolean hasArea = area != null && !area.isBlank();
        boolean hasTeam = teamId != null && !teamId.isBlank();
        boolean hasIter = iteration != null && !iteration.isBlank();
        if (!hasArea && !hasTeam && !hasIter) return null;

        String linkPredicate = """
                (r.id = tc.source_requirement_id
                 OR r.id::text IN (SELECT jsonb_array_elements_text(tc.linked_requirement_ids)))""";
        String sql = """
                SELECT tc.id::text AS id
                FROM platform_test_cases tc
                WHERE tc.project_id = :pid
                  AND (:area::text IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        WHERE r.project_id = tc.project_id AND %1$s AND r.area_path = :area))
                  AND (:iter::text IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        WHERE r.project_id = tc.project_id AND %1$s AND r.iteration_path = :iter))
                  AND (:team::uuid IS NULL OR EXISTS (
                        SELECT 1 FROM platform_requirements r
                        JOIN ado_teams t ON t.id = :team::uuid
                        WHERE r.project_id = tc.project_id AND %1$s
                          AND t.default_area_path IS NOT NULL
                          AND starts_with(r.area_path, t.default_area_path)))
                """.formatted(linkPredicate);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("pid", projectId)
                .addValue("area", hasArea ? area : null)
                .addValue("iter", hasIter ? iteration : null)
                .addValue("team", hasTeam ? teamId : null);
        return new HashSet<>(njdbc.query(sql, p, (rs, i) -> UUID.fromString(rs.getString("id"))));
    }

    @Transactional(readOnly = true)
    public ManagedTestCaseDto get(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto create(UUID projectId, CreateTestCaseRequest req) {
        PlatformTestCase tc = new PlatformTestCase(projectId, req.title(), req.acRefs(), "HUMAN", null);
        if (req.description() != null)        tc.setDescription(req.description());
        if (req.preconditions() != null)      tc.setPreconditions(req.preconditions());
        if (req.expectedResult() != null)     tc.setExpectedResult(req.expectedResult());
        if (req.priority() != null)           tc.setPriority(req.priority());
        if (req.suiteId() != null)            tc.setSuiteId(UUID.fromString(req.suiteId()));
        if (req.sourceRequirementId() != null) {
            UUID reqId = UUID.fromString(req.sourceRequirementId());
            tc.setSourceRequirementId(reqId);
            tc.linkRequirement(reqId);
        }
        if (req.linkedRequirementIds() != null) {
            for (String rid : req.linkedRequirementIds()) {
                try { tc.linkRequirement(UUID.fromString(rid)); } catch (IllegalArgumentException ignored) {}
            }
        }
        tc = testCaseRepo.save(tc);

        List<TestCaseStep> steps = saveSteps(tc.getId(), req.steps());
        return ManagedTestCaseDto.from(tc, steps);
    }

    public ManagedTestCaseDto update(UUID projectId, UUID tcId, UpdateTestCaseRequest req) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        if (req.title() != null)              tc.setTitle(req.title());
        if (req.description() != null)        tc.setDescription(req.description());
        if (req.preconditions() != null)      tc.setPreconditions(req.preconditions());
        if (req.expectedResult() != null)     tc.setExpectedResult(req.expectedResult());
        if (req.priority() != null)           tc.setPriority(req.priority());
        if (req.suiteId() != null)            tc.setSuiteId(UUID.fromString(req.suiteId()));
        if (req.linkedRequirementIds() != null) {
            // Full-replacement mode: the link set AND its primary (source) are authoritative.
            replaceLinks(tc, req.linkedRequirementIds());
            reconcileSource(tc, req.sourceRequirementId());
        } else if (req.sourceRequirementId() != null) {
            tc.setSourceRequirementId(UUID.fromString(req.sourceRequirementId()));
        }
        if (req.acRefs() != null)             tc.setAcRefs(req.acRefs());
        tc = testCaseRepo.save(tc);

        List<TestCaseStep> steps;
        if (req.steps() != null) {
            stepRepo.deleteAllByTestCaseId(tcId);
            steps = saveSteps(tc.getId(), req.steps());
        } else {
            steps = loadSteps(tcId);
        }
        return ManagedTestCaseDto.from(tc, steps);
    }

    public void delete(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        stepRepo.deleteAllByTestCaseId(tcId);
        testCaseRepo.delete(tc);
    }

    public ManagedTestCaseDto replaceSteps(UUID projectId, UUID tcId,
                                           List<CreateTestCaseRequest.StepRequest> steps) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        stepRepo.deleteAllByTestCaseId(tcId);
        List<TestCaseStep> saved = saveSteps(tcId, steps);
        return ManagedTestCaseDto.from(tc, saved);
    }

    public ManagedTestCaseDto submitForReview(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.submitForReview();
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto approve(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.approve();
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto reject(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.reject();
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto linkRequirement(UUID projectId, UUID tcId, UUID requirementId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.linkRequirement(requirementId);
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto unlinkRequirement(UUID projectId, UUID tcId, UUID requirementId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.unlinkRequirement(requirementId);
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto applyAnalysisSuggestion(UUID projectId, UUID tcId,
                                                        ApplySuggestionRequest req) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        if (req.title() != null)       tc.setTitle(req.title());
        if (req.description() != null) tc.setDescription(req.description());
        if (req.expectedResult() != null) tc.setExpectedResult(req.expectedResult());
        tc.applyAnalysisSuggestion(UUID.fromString(req.analysisId()));
        tc = testCaseRepo.save(tc);

        if (req.steps() != null && !req.steps().isEmpty()) {
            stepRepo.deleteAllByTestCaseId(tcId);
            saveSteps(tcId, req.steps());
        }
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto triggerAutomationGeneration(UUID projectId, UUID tcId, String githubConfigId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        if (githubConfigId != null) {
            tc.setAutomationGithubConfigId(UUID.fromString(githubConfigId));
        }
        tc.markAutomationGenerating();
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    // --- helpers ---

    /** Replaces the whole linked-requirement set (empty list clears all; invalid ids skipped). */
    private void replaceLinks(PlatformTestCase tc, List<String> requirementIds) {
        new ArrayList<>(tc.getLinkedRequirementIds() != null ? tc.getLinkedRequirementIds() : List.<String>of())
                .forEach(id -> {
                    try { tc.unlinkRequirement(UUID.fromString(id)); } catch (IllegalArgumentException ignored) {}
                });
        for (String rid : requirementIds) {
            try { tc.linkRequirement(UUID.fromString(rid)); } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Keeps {@code sourceRequirementId} (the primary) consistent with the linked set:
     * set it to the requested primary when that is part of the set, otherwise clear it
     * (e.g. when all links were removed, or the primary was unset). Avoids a stale source
     * reappearing on the next edit.
     */
    private void reconcileSource(PlatformTestCase tc, String requestedSourceId) {
        Set<String> linked = new HashSet<>(
                tc.getLinkedRequirementIds() != null ? tc.getLinkedRequirementIds() : List.of());
        if (requestedSourceId != null && linked.contains(requestedSourceId)) {
            tc.setSourceRequirementId(UUID.fromString(requestedSourceId));
        } else {
            tc.setSourceRequirementId(null);
        }
    }

    private PlatformTestCase loadAndVerify(UUID projectId, UUID tcId) {
        PlatformTestCase tc = testCaseRepo.findById(tcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Test case not found: " + tcId));
        if (!tc.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Test case not found for project: " + projectId);
        }
        return tc;
    }

    private List<TestCaseStep> loadSteps(UUID tcId) {
        return stepRepo.findByTestCaseIdOrderByStepNumberAsc(tcId);
    }

    private List<TestCaseStep> saveSteps(UUID tcId, List<CreateTestCaseRequest.StepRequest> stepRequests) {
        if (stepRequests == null || stepRequests.isEmpty()) {
            return List.of();
        }
        List<TestCaseStep> steps = new ArrayList<>();
        for (int i = 0; i < stepRequests.size(); i++) {
            CreateTestCaseRequest.StepRequest sr = stepRequests.get(i);
            steps.add(new TestCaseStep(tcId, i + 1, sr.action(), sr.expectedResult(), sr.notes()));
        }
        return stepRepo.saveAll(steps);
    }
}
