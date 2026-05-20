package com.platform.agent.hub.graph;

import com.platform.core.domain.*;
import com.platform.core.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Derives a SotTestPlan from the requirements scoped to a release.
 * Computes coverage score and risk level, and creates SotTestPlanItems
 * for each test case that covers an in-scope requirement.
 */
@Service
public class TestPlanGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(TestPlanGeneratorService.class);

    private final SotReleaseRepository            releaseRepo;
    private final SotTestPlanRepository           planRepo;
    private final SotTestPlanItemRepository       itemRepo;
    private final SotReleaseRequirementRepository releaseReqRepo;
    private final PlatformTestCaseRepository      testCaseRepo;
    private final GraphService                    graphService;

    public TestPlanGeneratorService(SotReleaseRepository releaseRepo,
                                     SotTestPlanRepository planRepo,
                                     SotTestPlanItemRepository itemRepo,
                                     SotReleaseRequirementRepository releaseReqRepo,
                                     PlatformTestCaseRepository testCaseRepo,
                                     GraphService graphService) {
        this.releaseRepo    = releaseRepo;
        this.planRepo       = planRepo;
        this.itemRepo       = itemRepo;
        this.releaseReqRepo = releaseReqRepo;
        this.testCaseRepo   = testCaseRepo;
        this.graphService   = graphService;
    }

    /**
     * Generates (or regenerates) a test plan for the given release.
     * Idempotent: replaces existing plan items if a plan already exists.
     */
    @Transactional
    public SotTestPlan generateForRelease(UUID projectId, UUID releaseId) {
        SotRelease release = releaseRepo.findById(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("Release not found: " + releaseId));

        SotTestPlan plan = planRepo.findByProjectIdAndReleaseId(projectId, releaseId)
                .orElseGet(() -> planRepo.save(new SotTestPlan(projectId, releaseId)));

        // Remove existing items before regenerating
        itemRepo.findByPlanId(plan.getId()).forEach(itemRepo::delete);

        // Gather all requirements scoped to this release via SCOPED_TO edges
        List<UUID> requirementIds = getReleaseRequirements(projectId, releaseId);

        if (requirementIds.isEmpty()) {
            log.warn("No requirements in scope for release {}", releaseId);
            plan.updateScore(BigDecimal.ZERO, "LOW");
            return planRepo.save(plan);
        }

        // For each in-scope requirement, collect covering test cases and add plan items
        Map<UUID, List<UUID>> tcToRequirements = new LinkedHashMap<>();
        for (UUID reqId : requirementIds) {
            List<PlatformTestCase> tcs = graphService.getTestCases(projectId, reqId);
            for (PlatformTestCase tc : tcs) {
                tcToRequirements.computeIfAbsent(tc.getId(), k -> new ArrayList<>()).add(reqId);
            }
        }

        for (Map.Entry<UUID, List<UUID>> entry : tcToRequirements.entrySet()) {
            UUID tcId    = entry.getKey();
            List<UUID> reqIds = entry.getValue();
            boolean hasAutomation = testCaseRepo.findById(tcId)
                    .map(PlatformTestCase::isHasAutomation).orElse(false);

            SotTestPlanItem item = new SotTestPlanItem(
                    plan.getId(), tcId, reqIds,
                    hasAutomation ? "AUTOMATED" : "MANUAL",
                    determinePriority(reqIds.size()));
            itemRepo.save(item);
        }

        // Compute coverage score: how many in-scope requirements have at least one test case
        long coveredCount = requirementIds.stream()
                .filter(rid -> !graphService.getTestCases(projectId, rid).isEmpty())
                .count();
        BigDecimal score = BigDecimal.valueOf(coveredCount)
                .divide(BigDecimal.valueOf(requirementIds.size()), 3, RoundingMode.HALF_UP);
        String riskLevel = deriveRiskLevel(score, tcToRequirements.size(), requirementIds.size());

        plan.updateScore(score, riskLevel);
        log.info("Generated test plan for release {} (project {}): {} items, coverage={}, risk={}",
                release.getName(), projectId, tcToRequirements.size(), score, riskLevel);

        return planRepo.save(plan);
    }

    // -------------------------------------------------------------------------

    /** Returns requirement IDs scoped to this release from the sot_release_requirements join table. */
    private List<UUID> getReleaseRequirements(UUID projectId, UUID releaseId) {
        return releaseReqRepo.findRequirementIdsByReleaseId(releaseId);
    }

    private String determinePriority(int requirementCount) {
        if (requirementCount >= 3) return "MUST_RUN";
        if (requirementCount == 2) return "SHOULD_RUN";
        return "NICE_TO_HAVE";
    }

    private String deriveRiskLevel(BigDecimal coverageScore, int tcCount, int reqCount) {
        double score = coverageScore.doubleValue();
        if (score >= 0.80) return "LOW";
        if (score >= 0.60) return "MEDIUM";
        if (score >= 0.30) return "HIGH";
        return "CRITICAL";
    }
}
