package com.platform.agent.hub.graph;

import com.platform.common.integration.IntegrationType;
import com.platform.common.model.*;
import com.platform.common.agent.RequirementContext;
import com.platform.common.agent.TestGenMode;
import com.platform.core.domain.*;
import com.platform.core.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Knowledge-graph traversal for the Agent Hub.
 *
 * Converts platform-core JPA entities into platform-common model records
 * (RequirementRecord, RequirementLink, etc.) that can be packaged into
 * a ContextBundle without leaking persistence types into platform-common.
 */
@Service
@Transactional(readOnly = true)
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

    private final PlatformRequirementRepository requirementRepo;
    private final PlatformTestCaseRepository    testCaseRepo;
    private final PlatformTraceabilityEdgeRepository edgeRepo;
    private final SotReleaseRepository          releaseRepo;
    private final ProjectIntegrationConfigRepository configRepo;

    public GraphService(PlatformRequirementRepository requirementRepo,
                        PlatformTestCaseRepository testCaseRepo,
                        PlatformTraceabilityEdgeRepository edgeRepo,
                        SotReleaseRepository releaseRepo,
                        ProjectIntegrationConfigRepository configRepo) {
        this.requirementRepo = requirementRepo;
        this.testCaseRepo    = testCaseRepo;
        this.edgeRepo        = edgeRepo;
        this.releaseRepo     = releaseRepo;
        this.configRepo      = configRepo;
    }

    // -------------------------------------------------------------------------
    // Graph traversal
    // -------------------------------------------------------------------------

    /**
     * Returns the full ancestor chain from this requirement up to the root Epic.
     * The list is ordered from immediate parent → root.
     */
    public List<RequirementRecord> getAncestors(UUID projectId, UUID requirementId) {
        List<RequirementRecord> chain = new ArrayList<>();
        UUID current = requirementId;
        Set<UUID> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            UUID parentId = requirementRepo.findById(current)
                    .map(PlatformRequirement::getParentId)
                    .orElse(null);
            // Null parent = root; cyclic parent = already visited — stop before adding either.
            if (parentId == null || visited.contains(parentId)) break;
            requirementRepo.findById(parentId)
                    .ifPresent(r -> chain.add(toRecord(r, projectId)));
            current = parentId;
        }
        return chain;
    }

    /** Direct children (subtasks / stories under an epic). */
    public List<RequirementRecord> getChildren(UUID projectId, UUID requirementId) {
        return requirementRepo.findByProjectIdAndParentId(projectId, requirementId)
                .stream()
                .map(r -> toRecord(r, projectId))
                .collect(Collectors.toList());
    }

    /** Cross-requirement links via LINKED_TO edges. */
    public List<RequirementLink> getLinks(UUID projectId, UUID requirementId) {
        List<PlatformTraceabilityEdge> edges =
                edgeRepo.findByProjectIdAndFromIdAndFromTier(projectId, requirementId, "REQUIREMENT");
        return edges.stream()
                .filter(e -> EdgeType.LINKED_TO.name().equals(e.getEdgeType()))
                .flatMap(e -> requirementRepo.findById(e.getToId()).stream()
                        .map(r -> new RequirementLink(
                                r.getId(), r.getExternalId(), r.getTitle(),
                                EdgeType.LINKED_TO,
                                e.getLinkSubtype() != null
                                        ? LinkSubtype.valueOf(e.getLinkSubtype()) : null)))
                .collect(Collectors.toList());
    }

    /** Test cases that cover this requirement (COVERED_BY edges). */
    public List<PlatformTestCase> getTestCases(UUID projectId, UUID requirementId) {
        return testCaseRepo.findCoveringTestCases(projectId, requirementId);
    }

    /**
     * Candidate test cases from requirements linked to this one via CLONED_FROM or RELATES_TO.
     * Used to detect REUSE_FROM_RELATED test-gen mode.
     */
    public List<PlatformTestCase> getReusableCandidates(UUID projectId, UUID requirementId) {
        List<PlatformTraceabilityEdge> linkedEdges =
                edgeRepo.findByProjectIdAndFromIdAndFromTier(projectId, requirementId, "REQUIREMENT")
                        .stream()
                        .filter(e -> EdgeType.LINKED_TO.name().equals(e.getEdgeType()))
                        .filter(e -> e.getLinkSubtype() != null &&
                                (LinkSubtype.CLONED_FROM.name().equals(e.getLinkSubtype()) ||
                                 LinkSubtype.RELATES_TO.name().equals(e.getLinkSubtype())))
                        .collect(Collectors.toList());

        return linkedEdges.stream()
                .flatMap(e -> testCaseRepo.findCoveringTestCases(projectId, e.getToId()).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /** True if any test case covering this requirement has automation. */
    public boolean hasAutomation(UUID projectId, UUID requirementId) {
        return getTestCases(projectId, requirementId).stream()
                .anyMatch(PlatformTestCase::isHasAutomation);
    }

    /** Expands the full subtree rooted at requirementId (BFS). Includes the root itself. */
    public List<RequirementRecord> expandHierarchy(UUID projectId, UUID requirementId) {
        List<RequirementRecord> result = new ArrayList<>();
        Queue<UUID> queue = new ArrayDeque<>();
        queue.add(requirementId);
        Set<UUID> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!visited.add(current)) continue;
            requirementRepo.findById(current).ifPresent(r -> {
                result.add(toRecord(r, projectId));
                queue.addAll(requirementRepo.findByProjectIdAndParentId(projectId, r.getId())
                        .stream().map(PlatformRequirement::getId).toList());
            });
        }
        return result;
    }

    /** Release names that include this requirement. */
    public List<String> getReleasesFor(UUID projectId, UUID requirementId) {
        return edgeRepo.findByProjectIdAndFromIdAndFromTier(projectId, requirementId, "REQUIREMENT")
                .stream()
                .filter(e -> EdgeType.SCOPED_TO.name().equals(e.getEdgeType()))
                .flatMap(e -> releaseRepo.findById(e.getToId()).stream().map(SotRelease::getName))
                .collect(Collectors.toList());
    }

    /** True if other requirements (not this one) also have COVERED_BY edges to the same test cases. */
    public boolean hasCoverageFromOtherRequirements(UUID projectId, UUID requirementId) {
        List<PlatformTestCase> tcs = getTestCases(projectId, requirementId);
        if (tcs.isEmpty()) return false;
        for (PlatformTestCase tc : tcs) {
            List<PlatformTraceabilityEdge> coverageEdges =
                    edgeRepo.findCoverageEdgesForRequirement(projectId, tc.getId());
            boolean otherExists = coverageEdges.stream()
                    .anyMatch(e -> !requirementId.equals(e.getToId()));
            if (otherExists) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // RequirementContext factory
    // -------------------------------------------------------------------------

    /**
     * Builds a complete RequirementContext for the given requirement, including
     * graph neighbourhood (ancestors, children, links, release scope) and
     * resolving the TestGenMode.
     */
    public RequirementContext buildRequirementContext(UUID projectId, UUID requirementId) {
        Optional<PlatformRequirement> reqOpt = requirementRepo.findById(requirementId);
        if (reqOpt.isEmpty()) {
            log.warn("buildRequirementContext: requirement {} not found", requirementId);
            return null;
        }
        PlatformRequirement req = reqOpt.get();

        RequirementRecord target   = toRecord(req, projectId);
        List<RequirementRecord> ancestors = getAncestors(projectId, requirementId);
        List<RequirementRecord> children  = getChildren(projectId, requirementId);
        List<RequirementLink>   links     = getLinks(projectId, requirementId);
        List<String>            releases  = getReleasesFor(projectId, requirementId);
        TestGenMode             mode      = resolveTestGenMode(projectId, requirementId);

        return new RequirementContext(target, ancestors, children, links, releases, mode);
    }

    // -------------------------------------------------------------------------
    // TestGenMode resolution
    // -------------------------------------------------------------------------

    /**
     * Determines the most appropriate generation strategy for a requirement:
     * 1. No existing TCs → CREATE_ALL
     * 2. Candidates from related reqs cover ≥ 80% of ACs → REUSE_FROM_RELATED
     * 3. Existing TCs are NEEDS_UPDATE → UPDATE_CHANGED
     * 4. Some ACs are uncovered → CREATE_FOR_NEW_ACS
     * 5. All covered and current → NO_ACTION
     */
    public TestGenMode resolveTestGenMode(UUID projectId, UUID requirementId) {
        List<PlatformTestCase> existingTcs = getTestCases(projectId, requirementId);

        if (existingTcs.isEmpty()) {
            List<PlatformTestCase> reusable = getReusableCandidates(projectId, requirementId);
            if (!reusable.isEmpty()) {
                // Heuristic: if reusable candidates ≥ 3, offer REUSE_FROM_RELATED
                if (reusable.size() >= 3) return TestGenMode.REUSE_FROM_RELATED;
            }
            return TestGenMode.CREATE_ALL;
        }

        boolean anyNeedsUpdate = existingTcs.stream()
                .anyMatch(tc -> "NEEDS_UPDATE".equals(tc.getCoverageStatus()));
        if (anyNeedsUpdate) return TestGenMode.UPDATE_CHANGED;

        boolean allActive = existingTcs.stream()
                .allMatch(tc -> "ACTIVE".equals(tc.getCoverageStatus()));
        if (allActive) return TestGenMode.NO_ACTION;

        return TestGenMode.CREATE_FOR_NEW_ACS;
    }

    // -------------------------------------------------------------------------
    // Edge upsert helpers (called by RequirementSyncService)
    // -------------------------------------------------------------------------

    @Transactional
    public void upsertEdge(UUID projectId,
                            UUID fromId, String fromTier,
                            UUID toId,   String toTier,
                            EdgeType edgeType,
                            LinkSubtype subtype) {
        edgeRepo.findByProjectIdAndFromIdAndToIdAndEdgeType(
                        projectId, fromId, toId, edgeType.name())
                .orElseGet(() -> {
                    PlatformTraceabilityEdge edge = new PlatformTraceabilityEdge(
                            projectId, fromId, fromTier, toId, toTier, edgeType.name());
                    if (subtype != null) edge.withSubtype(subtype.name());
                    return edgeRepo.save(edge);
                });
    }

    // -------------------------------------------------------------------------
    // Entity → record mapping
    // -------------------------------------------------------------------------

    private RequirementRecord toRecord(PlatformRequirement r, UUID projectId) {
        IntegrationType source = resolveSource(r.getIntegrationConfigId(), projectId);
        return new RequirementRecord(
                r.getId(),
                r.getExternalId(),
                source,
                r.getTitle(),
                r.getDescription(),
                List.of(),        // acceptance criteria extracted separately
                toIssueType(r.getIssueType()),
                r.getStatus(),
                r.getPriority(),
                List.of(),
                r.getParentId(),
                null,             // path — available via ltree but not mapped to entity field
                r.getDepth(),
                r.getSyncedAt()
        );
    }

    private RequirementRecord.IssueType toIssueType(String raw) {
        if (raw == null) return RequirementRecord.IssueType.STORY;
        try { return RequirementRecord.IssueType.valueOf(raw); }
        catch (IllegalArgumentException e) { return RequirementRecord.IssueType.STORY; }
    }

    private IntegrationType resolveSource(UUID configId, UUID projectId) {
        if (configId == null) return IntegrationType.GITHUB;
        return configRepo.findById(configId)
                .map(c -> {
                    try { return IntegrationType.valueOf(c.getIntegrationType()); }
                    catch (Exception e) { return IntegrationType.GITHUB; }
                })
                .orElse(IntegrationType.GITHUB);
    }
}
