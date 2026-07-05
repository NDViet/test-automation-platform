package com.platform.ingestion.management.sharedstep;

import com.platform.core.domain.SharedStep;
import com.platform.core.repository.SharedStepRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD for the shared-step library. Edits propagate to referencing case steps (resolved at read).
 */
@Service
@Transactional
public class SharedStepService {

  private final SharedStepRepository repo;

  public SharedStepService(SharedStepRepository repo) {
    this.repo = repo;
  }

  @Transactional(readOnly = true)
  public List<SharedStepDto> list(UUID projectId) {
    return repo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
        .map(SharedStepDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public SharedStepDto get(UUID projectId, UUID id) {
    return SharedStepDto.from(load(projectId, id));
  }

  public SharedStepDto create(UUID projectId, SharedStepForm req) {
    requireAction(req.action());
    return SharedStepDto.from(
        repo.save(
            new SharedStep(projectId, req.action().trim(), req.expectedResult(), req.notes())));
  }

  public SharedStepDto update(UUID projectId, UUID id, SharedStepForm req) {
    requireAction(req.action());
    SharedStep s = load(projectId, id);
    s.update(req.action().trim(), req.expectedResult(), req.notes());
    return SharedStepDto.from(repo.save(s));
  }

  public void delete(UUID projectId, UUID id) {
    repo.delete(load(projectId, id));
  }

  private SharedStep load(UUID projectId, UUID id) {
    return repo.findByIdAndProjectId(id, projectId)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Shared step not found: " + id));
  }

  private static void requireAction(String action) {
    if (action == null || action.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A step action is required");
    }
  }
}
