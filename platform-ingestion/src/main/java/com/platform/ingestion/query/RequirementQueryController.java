package com.platform.ingestion.query;

import com.platform.core.domain.PlatformRequirement;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.ingestion.query.dto.RequirementDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/requirements")
public class RequirementQueryController {

    private final PlatformRequirementRepository requirementRepo;

    public RequirementQueryController(PlatformRequirementRepository requirementRepo) {
        this.requirementRepo = requirementRepo;
    }

    @GetMapping
    public List<RequirementDto> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String search) {

        List<PlatformRequirement> items;
        if (search != null && !search.isBlank()) {
            items = requirementRepo.searchByProjectId(projectId, search.trim());
        } else if (status != null && !status.isBlank()) {
            items = requirementRepo.findByProjectIdAndStatusOrderByUpdatedAtDesc(projectId, status.toUpperCase());
        } else if (issueType != null && !issueType.isBlank()) {
            items = requirementRepo.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(projectId, issueType.toUpperCase());
        } else {
            items = requirementRepo.findByProjectIdOrderByUpdatedAtDesc(projectId);
        }
        return items.stream().map(RequirementDto::from).toList();
    }

    @GetMapping("/{reqId}")
    public ResponseEntity<RequirementDto> get(@PathVariable UUID projectId, @PathVariable UUID reqId) {
        return requirementRepo.findById(reqId)
                .filter(r -> r.getProjectId().equals(projectId))
                .map(r -> ResponseEntity.ok(RequirementDto.from(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@PathVariable UUID projectId) {
        List<PlatformRequirement> all = requirementRepo.findByProjectIdOrderByUpdatedAtDesc(projectId);
        Map<String, Long> byStatus    = all.stream().collect(Collectors.groupingBy(PlatformRequirement::getStatus, Collectors.counting()));
        Map<String, Long> byIssueType = all.stream().collect(Collectors.groupingBy(PlatformRequirement::getIssueType, Collectors.counting()));
        return Map.of(
                "total",      all.size(),
                "byStatus",   byStatus,
                "byIssueType", byIssueType
        );
    }
}
