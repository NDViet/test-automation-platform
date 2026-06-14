package com.platform.ingestion.management.tcm;

import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes the requirements coverage matrix: for each requirement, which curated
 * test cases cover it (automated vs manual) and the last observed result. Coverage
 * is derived from {@code PlatformTestCase.linkedRequirementIds} (+ sourceRequirementId).
 */
@Service
public class CoverageService {

    private final PlatformRequirementRepository requirementRepo;
    private final PlatformTestCaseRepository testCaseRepo;

    public CoverageService(PlatformRequirementRepository requirementRepo,
                           PlatformTestCaseRepository testCaseRepo) {
        this.requirementRepo = requirementRepo;
        this.testCaseRepo    = testCaseRepo;
    }

    @Transactional(readOnly = true)
    public CoverageDto coverage(UUID projectId) {
        List<PlatformRequirement> requirements = requirementRepo.findByProjectIdOrderByUpdatedAtDesc(projectId);
        List<PlatformTestCase> cases = testCaseRepo.findByProjectId(projectId);

        // requirementId -> covering test cases
        Map<String, List<PlatformTestCase>> byReq = new HashMap<>();
        for (PlatformTestCase tc : cases) {
            for (String reqId : requirementIdsOf(tc)) {
                byReq.computeIfAbsent(reqId, k -> new ArrayList<>()).add(tc);
            }
        }

        List<CoverageDto.Row> rows = new ArrayList<>();
        int coveredAuto = 0, coveredManual = 0, uncovered = 0;
        for (PlatformRequirement req : requirements) {
            List<PlatformTestCase> linked = byReq.getOrDefault(req.getId().toString(), List.of());
            int automated = (int) linked.stream().filter(PlatformTestCase::isHasAutomation).count();
            int manual = linked.size() - automated;

            if (linked.isEmpty())      uncovered++;
            else if (automated > 0)    coveredAuto++;
            else                       coveredManual++;

            rows.add(new CoverageDto.Row(
                    req.getId().toString(), req.getExternalId(), req.getTitle(),
                    req.getIssueType(), req.getStatus(),
                    automated, manual, lastStatus(linked)));
        }

        int total = requirements.size();
        double pct = total == 0 ? 0.0 : Math.round(coveredAuto * 1000.0 / total) / 10.0;
        return new CoverageDto(total, coveredAuto, coveredManual, uncovered, pct, rows);
    }

    private List<String> requirementIdsOf(PlatformTestCase tc) {
        List<String> ids = new ArrayList<>();
        if (tc.getLinkedRequirementIds() != null) ids.addAll(tc.getLinkedRequirementIds());
        if (tc.getSourceRequirementId() != null) {
            String s = tc.getSourceRequirementId().toString();
            if (!ids.contains(s)) ids.add(s);
        }
        return ids;
    }

    /** Most-recent observed result across linked cases, or null. */
    private String lastStatus(List<PlatformTestCase> linked) {
        String status = null;
        Instant latest = null;
        for (PlatformTestCase tc : linked) {
            if (tc.getLastResult() == null) continue;
            Instant at = tc.getLastExecutedAt();
            if (latest == null || (at != null && at.isAfter(latest))) {
                latest = at;
                status = tc.getLastResult();
            }
        }
        return status;
    }
}
