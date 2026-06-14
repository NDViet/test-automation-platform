package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestCaseTag;
import com.platform.core.repository.TestCaseTagRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Kiwi-style tags on test cases, plus project-wide tag suggestions for typeahead. */
@RestController
@Tag(name = "Test Case Management")
public class TestCaseTagController {

    public record TagRequest(String name) {}

    private final TagService service;

    public TestCaseTagController(TagService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/projects/{projectId}/test-cases/{caseId}/tags")
    public List<String> list(@PathVariable UUID projectId, @PathVariable UUID caseId) {
        return service.list(caseId);
    }

    @PostMapping("/api/v1/projects/{projectId}/test-cases/{caseId}/tags")
    public List<String> add(@PathVariable UUID projectId, @PathVariable UUID caseId,
                            @RequestBody TagRequest req) {
        return service.add(caseId, req.name());
    }

    @DeleteMapping("/api/v1/projects/{projectId}/test-cases/{caseId}/tags/{name}")
    public ResponseEntity<Void> remove(@PathVariable UUID projectId, @PathVariable UUID caseId,
                                       @PathVariable String name) {
        service.remove(caseId, name);
        return ResponseEntity.noContent().build();
    }

    /** Distinct tags across the project — drives typeahead suggestions. */
    @GetMapping("/api/v1/projects/{projectId}/tags")
    public List<String> suggestions(@PathVariable UUID projectId) {
        return service.suggestions(projectId);
    }

    @Service
    @Transactional
    static class TagService {
        private final TestCaseTagRepository repo;

        TagService(TestCaseTagRepository repo) {
            this.repo = repo;
        }

        @Transactional(readOnly = true)
        List<String> list(UUID caseId) {
            return repo.findByTestCaseIdOrderByNameAsc(caseId).stream().map(TestCaseTag::getName).toList();
        }

        List<String> add(UUID caseId, String name) {
            if (name == null || name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag name is required");
            }
            String clean = name.trim();
            if (repo.findByTestCaseIdAndName(caseId, clean).isEmpty()) {
                repo.save(new TestCaseTag(caseId, clean));
            }
            return list(caseId);
        }

        void remove(UUID caseId, String name) {
            repo.findByTestCaseIdAndName(caseId, name).ifPresent(repo::delete);
        }

        @Transactional(readOnly = true)
        List<String> suggestions(UUID projectId) {
            return repo.findDistinctNamesByProjectId(projectId);
        }
    }
}
