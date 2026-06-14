package com.platform.ingestion.query;

import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.service.CredentialResolver;
import com.platform.ingestion.query.dto.PagedRequirementsDto;
import com.platform.ingestion.query.dto.RequirementDto;
import com.platform.ingestion.query.dto.RequirementRelationsDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final CredentialResolver credentialResolver;

    public RequirementQueryController(PlatformRequirementRepository requirementRepo,
                                      CredentialResolver credentialResolver) {
        this.requirementRepo    = requirementRepo;
        this.credentialResolver = credentialResolver;
    }

    /**
     * Web "open original" base URL for a project's ADO work items, or null if the project
     * has no resolvable Azure Boards credential. Org-only form
     * ({@code https://dev.azure.com/{org}/_workitems/edit/}) — ADO redirects to the right
     * project, so we avoid encoding the (possibly spaced) project name. Honors a custom
     * base URL (e.g. on-prem Azure DevOps Server) when the credential defines one.
     */
    private String adoWorkItemBase(UUID projectId) {
        return credentialResolver.resolve(projectId, IntegrationType.AZURE_DEVOPS_BOARDS.name())
                .map(cred -> {
                    String org = cred.param("organization");
                    if (org == null || org.isBlank()) return null;
                    String host = (cred.baseUrl() != null && !cred.baseUrl().isBlank())
                            ? trimSlash(cred.baseUrl())
                            : "https://dev.azure.com/" + org.trim();
                    return host + "/_workitems/edit/";
                })
                .orElse(null);
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
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
        String base = adoWorkItemBase(projectId);
        return items.stream().map(r -> RequirementDto.from(r, base)).toList();
    }

    /** Server-side paginated, combinable filter+search (for large requirement sets). */
    @GetMapping("/page")
    public PagedRequirementsDto page(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(new Sort.Order(Sort.Direction.DESC, "createdDate").nullsLast()));
        Page<PlatformRequirement> p = requirementRepo.searchPage(
                projectId, norm(status, true), norm(issueType, true), norm(search, false), pageable);
        String base = adoWorkItemBase(projectId);
        return new PagedRequirementsDto(
                p.getContent().stream().map(r -> RequirementDto.from(r, base)).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /** Blank → "" sentinel (means "no filter"); typed varchar avoids null-bind type issues. */
    private static String norm(String s, boolean upper) {
        if (s == null || s.isBlank()) return "";
        String t = s.trim();
        return upper ? t.toUpperCase() : t;
    }

    @GetMapping("/{reqId}")
    public ResponseEntity<RequirementDto> get(@PathVariable UUID projectId, @PathVariable UUID reqId) {
        return requirementRepo.findById(reqId)
                .filter(r -> r.getProjectId().equals(projectId))
                .map(r -> ResponseEntity.ok(RequirementDto.from(r, adoWorkItemBase(projectId))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Parent + direct children of a requirement (work-item hierarchy) for the detail panel. */
    @GetMapping("/{reqId}/relations")
    public ResponseEntity<RequirementRelationsDto> relations(@PathVariable UUID projectId,
                                                             @PathVariable UUID reqId) {
        return requirementRepo.findById(reqId)
                .filter(r -> r.getProjectId().equals(projectId))
                .map(r -> {
                    String base = adoWorkItemBase(projectId);
                    RequirementRelationsDto.Ref parent = r.getParentId() == null ? null
                            : requirementRepo.findById(r.getParentId())
                                .map(p -> RequirementRelationsDto.Ref.from(p, base))
                                .orElse(null);
                    List<RequirementRelationsDto.Ref> children =
                            requirementRepo.findByProjectIdAndParentId(projectId, reqId).stream()
                                    .sorted(java.util.Comparator.comparing(
                                            c -> c.getExternalId() == null ? "" : c.getExternalId()))
                                    .map(c -> RequirementRelationsDto.Ref.from(c, base))
                                    .toList();
                    return ResponseEntity.ok(new RequirementRelationsDto(parent, children));
                })
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
