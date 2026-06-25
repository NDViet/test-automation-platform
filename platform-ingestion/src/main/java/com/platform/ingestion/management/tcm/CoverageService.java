package com.platform.ingestion.management.tcm;

import com.platform.core.domain.AdoTeam;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the requirements coverage matrix and rolls it up by Area and Team so coverage progress
 * can be tracked per owner. Coverage is derived from {@code PlatformTestCase.linkedRequirementIds}
 * (+ sourceRequirementId); a requirement's Team is the ADO team whose default area path is the
 * longest prefix of its area path.
 */
@Service
public class CoverageService {

  private static final String NO_AREA = "(no area)";
  private static final String NO_TEAM = "(unassigned)";

  private final PlatformRequirementRepository requirementRepo;
  private final PlatformTestCaseRepository testCaseRepo;
  private final AdoTeamRepository teamRepo;

  public CoverageService(
      PlatformRequirementRepository requirementRepo,
      PlatformTestCaseRepository testCaseRepo,
      AdoTeamRepository teamRepo) {
    this.requirementRepo = requirementRepo;
    this.testCaseRepo = testCaseRepo;
    this.teamRepo = teamRepo;
  }

  @Transactional(readOnly = true)
  public CoverageDto coverage(UUID projectId) {
    return coverage(projectId, null, null, null);
  }

  /** Coverage scoped to the project filter (Area exact, Team subtree, Iteration exact). */
  @Transactional(readOnly = true)
  public CoverageDto coverage(UUID projectId, String area, String teamId, String iteration) {
    List<PlatformTestCase> cases = testCaseRepo.findByProjectId(projectId);
    List<AdoTeam> teams = teamRepo.findByProjectIdOrderByName(projectId);

    String teamPrefix = teamPrefix(teams, teamId);
    boolean hasArea = area != null && !area.isBlank();
    boolean hasIter = iteration != null && !iteration.isBlank();
    boolean hasTeam = teamPrefix != null;
    List<PlatformRequirement> requirements =
        requirementRepo.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
            .filter(r -> !hasArea || area.equals(r.getAreaPath()))
            .filter(r -> !hasIter || iteration.equals(r.getIterationPath()))
            .filter(
                r ->
                    !hasTeam || (r.getAreaPath() != null && r.getAreaPath().startsWith(teamPrefix)))
            .toList();

    // requirementId -> covering test cases
    Map<String, List<PlatformTestCase>> byReq = new HashMap<>();
    for (PlatformTestCase tc : cases) {
      for (String reqId : requirementIdsOf(tc)) {
        byReq.computeIfAbsent(reqId, k -> new ArrayList<>()).add(tc);
      }
    }

    List<CoverageDto.Row> rows = new ArrayList<>();
    Map<String, Acc> areaAcc = new HashMap<>();
    Map<String, Acc> teamAcc = new HashMap<>();
    int coveredAuto = 0, coveredManual = 0, uncovered = 0;

    for (PlatformRequirement req : requirements) {
      List<PlatformTestCase> linked = byReq.getOrDefault(req.getId().toString(), List.of());
      int automated = (int) linked.stream().filter(PlatformTestCase::isHasAutomation).count();
      int manual = linked.size() - automated;

      String state;
      if (linked.isEmpty()) {
        uncovered++;
        state = "gap";
      } else if (automated > 0) {
        coveredAuto++;
        state = "auto";
      } else {
        coveredManual++;
        state = "manual";
      }

      String reqArea =
          (req.getAreaPath() != null && !req.getAreaPath().isBlank()) ? req.getAreaPath() : NO_AREA;
      String reqTeam = resolveTeam(teams, req.getAreaPath());

      areaAcc.computeIfAbsent(reqArea, k -> new Acc()).add(state);
      teamAcc.computeIfAbsent(reqTeam, k -> new Acc()).add(state);

      rows.add(
          new CoverageDto.Row(
              req.getId().toString(),
              req.getExternalId(),
              req.getTitle(),
              req.getIssueType(),
              req.getStatus(),
              reqArea,
              reqTeam,
              automated,
              manual,
              lastStatus(linked)));
    }

    int total = requirements.size();
    double pct = total == 0 ? 0.0 : round1(coveredAuto * 100.0 / total);
    return new CoverageDto(
        total,
        coveredAuto,
        coveredManual,
        uncovered,
        pct,
        toGroups(areaAcc),
        toGroups(teamAcc),
        rows);
  }

  /** Default area path of the given team id (used as a subtree prefix), or null if none. */
  private String teamPrefix(List<AdoTeam> teams, String teamId) {
    if (teamId == null || teamId.isBlank()) return null;
    UUID id;
    try {
      id = UUID.fromString(teamId.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
    return teams.stream()
        .filter(t -> id.equals(t.getId()))
        .map(AdoTeam::getDefaultAreaPath)
        .filter(d -> d != null && !d.isBlank())
        .findFirst()
        .orElse(null);
  }

  /** ADO team whose default area path is the longest prefix of the requirement's area. */
  private String resolveTeam(List<AdoTeam> teams, String areaPath) {
    if (areaPath == null || areaPath.isBlank()) return NO_TEAM;
    String best = null;
    int bestLen = -1;
    for (AdoTeam t : teams) {
      String dap = t.getDefaultAreaPath();
      if (dap != null && !dap.isBlank() && areaPath.startsWith(dap) && dap.length() > bestLen) {
        best = t.getName();
        bestLen = dap.length();
      }
    }
    return best != null ? best : NO_TEAM;
  }

  private List<CoverageDto.GroupStat> toGroups(Map<String, Acc> acc) {
    List<CoverageDto.GroupStat> out = new ArrayList<>();
    acc.forEach(
        (label, a) -> {
          int covered = a.auto + a.manual;
          out.add(
              new CoverageDto.GroupStat(
                  label,
                  a.total,
                  covered,
                  a.auto,
                  a.manual,
                  a.uncovered,
                  a.total == 0 ? 0.0 : round1(covered * 100.0 / a.total),
                  a.total == 0 ? 0.0 : round1(a.auto * 100.0 / a.total)));
        });
    // Lowest coverage first (where attention is needed), then largest groups.
    out.sort(
        Comparator.comparingDouble(CoverageDto.GroupStat::coveragePct)
            .thenComparing(Comparator.comparingInt(CoverageDto.GroupStat::total).reversed()));
    return out;
  }

  private static final class Acc {
    int total, auto, manual, uncovered;

    void add(String state) {
      total++;
      switch (state) {
        case "auto" -> auto++;
        case "manual" -> manual++;
        default -> uncovered++;
      }
    }
  }

  private static double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
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
