package com.platform.agent.hub.graph;

import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.SotRelease;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.SotReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects what changed when a requirement is re-synced and calculates the
 * downstream impact: which test cases need updating, which are obsolete,
 * which ACs need new coverage, and which releases are affected.
 */
@Service
public class RequirementChangeProcessor {

    private static final Logger log = LoggerFactory.getLogger(RequirementChangeProcessor.class);

    private final PlatformRequirementRepository requirementRepo;
    private final PlatformTestCaseRepository    testCaseRepo;
    private final SotReleaseRepository          releaseRepo;
    private final GraphService                  graphService;

    public RequirementChangeProcessor(PlatformRequirementRepository requirementRepo,
                                       PlatformTestCaseRepository testCaseRepo,
                                       SotReleaseRepository releaseRepo,
                                       GraphService graphService) {
        this.requirementRepo = requirementRepo;
        this.testCaseRepo    = testCaseRepo;
        this.releaseRepo     = releaseRepo;
        this.graphService    = graphService;
    }

    /**
     * Computes the change impact for a requirement that was just re-synced.
     * Returns null if the requirement doesn't exist or nothing changed.
     */
    @Transactional
    public ChangeImpact computeImpact(UUID projectId, UUID requirementId,
                                       String newTitle, String newDescription) {
        Optional<PlatformRequirement> opt = requirementRepo.findById(requirementId);
        if (opt.isEmpty()) return null;

        PlatformRequirement req = opt.get();
        String newHash = sha256(newTitle + "\0" + newDescription);
        String oldHash = req.getVersionHash();

        // Store current as previous before updating
        req.setVersionHash(newHash, oldHash, summarizeChange(req, newTitle, newDescription));

        if (newHash.equals(oldHash)) {
            log.debug("No change detected for requirement {}", requirementId);
            return null;
        }

        List<PlatformTestCase> existingTcs = graphService.getTestCases(projectId, requirementId);
        List<String>           affectedReleases = getAffectedReleaseNames(projectId, requirementId);

        // Any test case referencing old ACs (by ref key) should be NEEDS_UPDATE
        List<UUID> toUpdate = existingTcs.stream()
                .filter(tc -> "ACTIVE".equals(tc.getCoverageStatus()))
                .map(PlatformTestCase::getId)
                .collect(Collectors.toList());

        // Test cases already OBSOLETE or ARCHIVED are candidates for cleanup
        List<UUID> obsoleteCandidates = existingTcs.stream()
                .filter(tc -> "NEEDS_UPDATE".equals(tc.getCoverageStatus()) ||
                              "OBSOLETE".equals(tc.getCoverageStatus()))
                .map(PlatformTestCase::getId)
                .collect(Collectors.toList());

        // Mark active TCs as NEEDS_UPDATE
        toUpdate.forEach(id -> testCaseRepo.findById(id).ifPresent(tc -> {
            tc.markNeedsUpdate();
            testCaseRepo.save(tc);
        }));

        log.info("Requirement {} changed: {} TCs marked NEEDS_UPDATE, {} release(s) affected",
                requirementId, toUpdate.size(), affectedReleases.size());

        return new ChangeImpact(requirementId, toUpdate, obsoleteCandidates,
                List.of(), // newACsNeedingCoverage — populated by agent after re-extracting ACs
                affectedReleases, oldHash, newHash);
    }

    private List<String> getAffectedReleaseNames(UUID projectId, UUID requirementId) {
        return graphService.getReleasesFor(projectId, requirementId);
    }

    private String summarizeChange(PlatformRequirement req, String newTitle, String newDescription) {
        if (!Objects.equals(req.getTitle(), newTitle) &&
            !Objects.equals(req.getDescription(), newDescription)) {
            return "title and description updated";
        }
        if (!Objects.equals(req.getTitle(), newTitle)) return "title updated";
        if (!Objects.equals(req.getDescription(), newDescription)) return "description updated";
        return "metadata updated";
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString(); // fallback — ensures change is always detected
        }
    }

    // -------------------------------------------------------------------------

    public record ChangeImpact(
            UUID requirementId,
            List<UUID> testCasesToUpdate,
            List<UUID> obsoleteCandidates,
            List<String> newACsNeedingCoverage,
            List<String> affectedReleases,
            String previousHash,
            String currentHash
    ) {
        public boolean hasImpact() {
            return !testCasesToUpdate.isEmpty() || !obsoleteCandidates.isEmpty()
                    || !newACsNeedingCoverage.isEmpty();
        }
    }
}
