package com.platform.ingestion.query;

import com.platform.core.domain.AdoArea;
import com.platform.core.domain.AdoIteration;
import com.platform.core.domain.AdoTeam;
import com.platform.core.domain.AdoUser;
import com.platform.core.repository.AdoAreaRepository;
import com.platform.core.repository.AdoIterationRepository;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.AdoUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read API over the synced ADO org structure (teams / areas / iterations / users). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/ado")
public class AdoStructureQueryController {

    private final AdoTeamRepository teamRepo;
    private final AdoAreaRepository areaRepo;
    private final AdoIterationRepository iterationRepo;
    private final AdoUserRepository userRepo;

    public AdoStructureQueryController(AdoTeamRepository teamRepo, AdoAreaRepository areaRepo,
                                       AdoIterationRepository iterationRepo, AdoUserRepository userRepo) {
        this.teamRepo      = teamRepo;
        this.areaRepo      = areaRepo;
        this.iterationRepo = iterationRepo;
        this.userRepo      = userRepo;
    }

    @GetMapping("/teams")
    public List<AdoTeam> teams(@PathVariable UUID projectId) {
        return teamRepo.findByProjectIdOrderByName(projectId);
    }

    @GetMapping("/areas")
    public List<AdoArea> areas(@PathVariable UUID projectId) {
        return areaRepo.findByProjectIdOrderByPath(projectId);
    }

    @GetMapping("/iterations")
    public List<AdoIteration> iterations(@PathVariable UUID projectId) {
        return iterationRepo.findByProjectIdOrderByPath(projectId);
    }

    @GetMapping("/users")
    public List<AdoUser> users(@PathVariable UUID projectId) {
        return userRepo.findByProjectIdOrderByDisplayName(projectId);
    }

    /** Allowed quality-role flags (null/blank clears the flag). */
    private static final Set<String> QUALITY_ROLES = Set.of("QA", "QE", "SDET");

    public record QualityRoleRequest(String qualityRole) {}

    /** Flag (or clear) a user's quality role — a platform annotation preserved across re-syncs. */
    @PutMapping("/users/{userId}/quality-role")
    public AdoUser setQualityRole(@PathVariable UUID projectId, @PathVariable UUID userId,
                                  @RequestBody QualityRoleRequest req) {
        AdoUser u = userRepo.findById(userId)
                .filter(x -> x.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String role = req.qualityRole() == null ? null : req.qualityRole().trim().toUpperCase();
        if (role != null && !role.isEmpty() && !QUALITY_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid quality role: " + role + " (allowed: QA, QE, SDET)");
        }
        u.setQualityRole(role == null || role.isEmpty() ? null : role);
        return userRepo.save(u);
    }

    @GetMapping("/summary")
    public Map<String, Long> summary(@PathVariable UUID projectId) {
        return Map.of(
                "teams",        teamRepo.countByProjectId(projectId),
                "areas",        areaRepo.countByProjectId(projectId),
                "iterations",   iterationRepo.countByProjectId(projectId),
                "users",        userRepo.countByProjectId(projectId),
                "qualityUsers", userRepo.countByProjectIdAndQualityRoleIsNotNull(projectId));
    }
}
