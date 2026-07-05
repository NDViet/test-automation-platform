package com.platform.ingestion.management.release;

import com.platform.core.domain.AdoTeam;
import com.platform.core.domain.SotRelease;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.SotReleaseRepository;
import com.platform.ingestion.quality.ReleaseGateService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD for platform-owned releases ({@code sot_releases}). A release is standalone by default; an
 * optional COMPOSITE mapping (Iteration ∧ Area ∧ Team, + optional tag/field) auto-associates the
 * upstream requirements that make up the release scope.
 */
@Service
@Transactional
public class ReleaseService {

  private final SotReleaseRepository repo;
  private final AdoTeamRepository teamRepo;
  private final NamedParameterJdbcTemplate njdbc;
  private final ReleaseGateService gate;

  public ReleaseService(
      SotReleaseRepository repo,
      AdoTeamRepository teamRepo,
      NamedParameterJdbcTemplate njdbc,
      ReleaseGateService gate) {
    this.repo = repo;
    this.teamRepo = teamRepo;
    this.njdbc = njdbc;
    this.gate = gate;
  }

  @Transactional(readOnly = true)
  public List<ReleaseDto> list(UUID projectId) {
    return repo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
        .map(r -> toDto(projectId, r))
        .toList();
  }

  @Transactional(readOnly = true)
  public ReleaseDto get(UUID projectId, UUID id) {
    return toDto(projectId, load(projectId, id));
  }

  public ReleaseDto create(UUID projectId, CreateReleaseRequest req) {
    SotRelease r =
        new SotRelease(projectId, req.name().trim(), req.releaseType(), req.externalId());
    applyEditable(r, req);
    try {
      r = repo.save(r);
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "A release named '" + req.name() + "' already exists in this project");
    }
    return toDto(projectId, r);
  }

  public ReleaseDto update(UUID projectId, UUID id, CreateReleaseRequest req) {
    SotRelease r = load(projectId, id);
    // Enforce the quality gate on the transition INTO released — the one irreversible step.
    String newState = req.state() == null ? null : req.state().trim().toUpperCase();
    if ("RELEASED".equals(newState) && !"RELEASED".equals(r.getState())) {
      enforceGate(projectId, r);
    }
    r.setName(req.name().trim());
    r.setReleaseType(req.releaseType());
    r.setExternalId(req.externalId());
    applyEditable(r, req);
    return toDto(projectId, repo.save(r));
  }

  /**
   * Blocks the transition to RELEASED unless the gate passes. An explicit prior OVERRIDDEN decision
   * stands (never re-evaluated away); otherwise the gate is (re-)evaluated fresh and a FAIL raises
   * a {@code 409} listing the failing criteria. A disabled gate evaluates to PASS, so this is a
   * no-op.
   */
  private void enforceGate(UUID projectId, SotRelease r) {
    if ("OVERRIDDEN".equals(r.getGateStatus())) {
      return;
    }
    ReleaseGateService.GateEvaluation eval = gate.evaluate(projectId, r.getId());
    if (!"PASS".equals(eval.status())) {
      String failing =
          eval.criteria().stream()
              .filter(c -> !c.passed())
              .map(c -> c.name() + " (" + c.actual() + " vs " + c.threshold() + ")")
              .collect(Collectors.joining("; "));
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Quality gate FAILED — cannot mark this release RELEASED: "
              + failing
              + ". Override the gate with a reason to proceed.");
    }
  }

  public void delete(UUID projectId, UUID id) {
    repo.delete(load(projectId, id));
  }

  /** Requirements auto-associated to a release via its composite mapping (0 when unmapped). */
  @Transactional(readOnly = true)
  public long mappedRequirementCount(UUID projectId, UUID id) {
    return mappedCount(projectId, load(projectId, id));
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private void applyEditable(SotRelease r, CreateReleaseRequest req) {
    if (req.state() != null && !req.state().isBlank()) r.setState(req.state().trim().toUpperCase());
    if (req.targetDate() != null && !req.targetDate().isBlank()) {
      try {
        r.setTargetDate(LocalDate.parse(req.targetDate().trim()));
      } catch (Exception e) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid targetDate (expected yyyy-MM-dd): " + req.targetDate());
      }
    } else {
      r.setTargetDate(null);
    }
    UUID teamId = parseUuid(req.mapTeamId());
    r.setMapping(
        req.mapIterationPath(),
        req.mapAreaPath(),
        teamId,
        req.mapTag(),
        req.mappingField(),
        req.mappingValue());
  }

  private static UUID parseUuid(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return UUID.fromString(s.trim());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid team id: " + s);
    }
  }

  private SotRelease load(UUID projectId, UUID id) {
    SotRelease r =
        repo.findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found: " + id));
    if (!projectId.equals(r.getProjectId())) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Release not found in project: " + id);
    }
    return r;
  }

  private ReleaseDto toDto(UUID projectId, SotRelease r) {
    String teamName =
        r.getMapTeamId() == null
            ? null
            : teamRepo.findById(r.getMapTeamId()).map(AdoTeam::getName).orElse(null);
    long mapped = mappedCount(projectId, r);
    Long runs =
        njdbc
            .getJdbcTemplate()
            .queryForObject(
                "SELECT count(*) FROM test_runs WHERE project_id = ? AND release_id = ?",
                Long.class,
                projectId,
                r.getId());
    return ReleaseDto.from(r, teamName, mapped, runs != null ? runs : 0L);
  }

  /** Requirements matching ALL set mapping dimensions. */
  long mappedCount(UUID projectId, SotRelease r) {
    if (!r.isMapped()) return 0L;
    MapSqlParameterSource p = new MapSqlParameterSource().addValue("pid", projectId);
    String where = ReleaseScope.whereClause(r, p);
    Long n =
        njdbc.queryForObject(
            "SELECT count(*) FROM platform_requirements r WHERE r.project_id = :pid AND " + where,
            p,
            Long.class);
    return n != null ? n : 0L;
  }
}
