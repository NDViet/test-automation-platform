package com.platform.ingestion.management.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.RunSchedule;
import com.platform.core.repository.RunScheduleRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** CRUD for CI run schedules. The dispatch poller (separate) consumes {@code findDueEnabled}. */
@Service
@Transactional
public class RunScheduleService {

  private final RunScheduleRepository repo;
  private final ObjectMapper om;

  public RunScheduleService(RunScheduleRepository repo, ObjectMapper om) {
    this.repo = repo;
    this.om = om;
  }

  @Transactional(readOnly = true)
  public List<RunScheduleDto> list(UUID projectId) {
    return repo.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public RunScheduleDto get(UUID projectId, UUID id) {
    return toDto(load(projectId, id));
  }

  public RunScheduleDto create(UUID projectId, RunScheduleForm req) {
    int interval = requirePositiveInterval(req.intervalMinutes());
    Instant nextRun = Instant.now().plus(interval, ChronoUnit.MINUTES);
    RunSchedule s =
        new RunSchedule(
            projectId,
            req.name(),
            req.repoFullName(),
            req.workflowId(),
            req.ref(),
            writeInputs(req.inputs()),
            interval,
            req.enabled(),
            nextRun);
    return toDto(repo.save(s));
  }

  public RunScheduleDto update(UUID projectId, UUID id, RunScheduleForm req) {
    int interval = requirePositiveInterval(req.intervalMinutes());
    RunSchedule s = load(projectId, id);
    // Preserve an existing next-run if still enabled; otherwise re-seed from now.
    Instant nextRun =
        s.getNextRunAt() != null
            ? s.getNextRunAt()
            : Instant.now().plus(interval, ChronoUnit.MINUTES);
    s.update(
        req.name(),
        req.repoFullName(),
        req.workflowId(),
        req.ref(),
        writeInputs(req.inputs()),
        interval,
        req.enabled(),
        nextRun);
    return toDto(repo.save(s));
  }

  public void delete(UUID projectId, UUID id) {
    repo.delete(load(projectId, id));
  }

  RunSchedule load(UUID projectId, UUID id) {
    return repo.findByIdAndProjectId(id, projectId)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Run schedule not found: " + id));
  }

  private static int requirePositiveInterval(int m) {
    if (m <= 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "intervalMinutes must be greater than 0");
    }
    return m;
  }

  RunScheduleDto toDto(RunSchedule s) {
    return new RunScheduleDto(
        s.getId() != null ? s.getId().toString() : null,
        s.getName(),
        s.getRepoFullName(),
        s.getWorkflowId(),
        s.getRef(),
        readInputs(s.getInputs()),
        s.getIntervalMinutes(),
        s.getNextRunAt() != null ? s.getNextRunAt().toString() : null,
        s.isEnabled(),
        s.getLastDispatchedAt() != null ? s.getLastDispatchedAt().toString() : null,
        s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
  }

  private String writeInputs(Map<String, String> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return null;
    }
    try {
      return om.writeValueAsString(inputs);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inputs");
    }
  }

  @SuppressWarnings("unchecked")
  Map<String, String> readInputs(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return om.readValue(json, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }
}
