package com.platform.ingestion.management.tcm;

import com.platform.core.domain.AdoTeam;
import com.platform.core.domain.TestSuite;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.TestSuiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TestSuiteService {

    private final TestSuiteRepository suiteRepo;
    private final AdoTeamRepository teamRepo;
    private final SuiteResolverService resolver;

    public TestSuiteService(TestSuiteRepository suiteRepo, AdoTeamRepository teamRepo,
                            SuiteResolverService resolver) {
        this.suiteRepo = suiteRepo;
        this.teamRepo  = teamRepo;
        this.resolver  = resolver;
    }

    @Transactional(readOnly = true)
    public List<TestSuiteDto> list(UUID projectId) {
        return suiteRepo.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(s -> toDto(projectId, s))
                .toList();
    }

    public TestSuiteDto create(UUID projectId, CreateTestSuiteRequest req) {
        TestSuite suite = new TestSuite(projectId, req.name(), req.description(),
                parseParent(req.parentId()), req.planType());
        if (req.active() != null) suite.setActive(req.active());
        applyScope(suite, req);
        return toDto(projectId, suiteRepo.save(suite));
    }

    public TestSuiteDto update(UUID projectId, UUID suiteId, CreateTestSuiteRequest req) {
        TestSuite suite = load(projectId, suiteId);
        suite.setName(req.name());
        suite.setDescription(req.description());
        suite.setParentId(parseParent(req.parentId()));
        suite.setPlanType(req.planType());
        if (req.active() != null) suite.setActive(req.active());
        applyScope(suite, req);
        return toDto(projectId, suiteRepo.save(suite));
    }

    /** Resolved cases for a suite (static members or smart filter) — for preview/run-building. */
    @Transactional(readOnly = true)
    public List<SelectableTestCaseDto> cases(UUID projectId, UUID suiteId) {
        return resolver.resolveDtos(projectId, load(projectId, suiteId));
    }

    /** Replaces the static membership of a suite. */
    public void replaceMembers(UUID projectId, UUID suiteId, List<String> caseIds) {
        List<UUID> ids = caseIds == null ? List.of()
                : caseIds.stream().map(UUID::fromString).toList();
        resolver.replaceMembers(projectId, suiteId, ids);
    }

    public void delete(UUID projectId, UUID suiteId) {
        suiteRepo.delete(load(projectId, suiteId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void applyScope(TestSuite suite, CreateTestSuiteRequest req) {
        UUID teamId = (req.teamId() != null && !req.teamId().isBlank())
                ? UUID.fromString(req.teamId().trim()) : null;
        suite.setScopeAndMode(req.areaPath(), teamId, req.selectionMode(),
                req.filterIteration(), req.filterStatus(), req.filterTags());
    }

    private TestSuite load(UUID projectId, UUID suiteId) {
        return suiteRepo.findByProjectIdAndId(projectId, suiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Test suite not found: " + suiteId));
    }

    private TestSuiteDto toDto(UUID projectId, TestSuite s) {
        String teamName = s.getTeamId() == null ? null
                : teamRepo.findById(s.getTeamId()).map(AdoTeam::getName).orElse(null);
        int count = resolver.resolvedCount(projectId, s);
        return TestSuiteDto.from(s, teamName, count);
    }

    private UUID parseParent(String parentId) {
        if (parentId == null || parentId.isBlank()) return null;
        try {
            return UUID.fromString(parentId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parentId: " + parentId);
        }
    }
}
