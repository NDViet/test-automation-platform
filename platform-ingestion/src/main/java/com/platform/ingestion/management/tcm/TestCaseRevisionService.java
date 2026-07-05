package com.platform.ingestion.management.tcm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseRevision;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseRevisionRepository;
import com.platform.core.repository.TestCaseStepRepository;
import com.platform.core.repository.TestCaseTagRepository;
import com.platform.security.web.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures a JSON snapshot of a test case (title/status/steps/tags) as a versioned revision on each
 * save, and lists a case's history. The snapshot is the post-save state, so the newest revision
 * mirrors the current case.
 */
@Service
public class TestCaseRevisionService {

  private final PlatformTestCaseRepository testCaseRepo;
  private final TestCaseStepRepository stepRepo;
  private final TestCaseTagRepository tagRepo;
  private final TestCaseRevisionRepository revisionRepo;
  private final ObjectMapper om;

  public TestCaseRevisionService(
      PlatformTestCaseRepository testCaseRepo,
      TestCaseStepRepository stepRepo,
      TestCaseTagRepository tagRepo,
      TestCaseRevisionRepository revisionRepo,
      ObjectMapper om) {
    this.testCaseRepo = testCaseRepo;
    this.stepRepo = stepRepo;
    this.tagRepo = tagRepo;
    this.revisionRepo = revisionRepo;
    this.om = om;
  }

  /**
   * Snapshots the current state of a case as the next revision. Best-effort — never blocks the
   * save.
   */
  @Transactional
  public void snapshot(UUID projectId, UUID tcId, String changeKind) {
    PlatformTestCase tc = testCaseRepo.findById(tcId).orElse(null);
    if (tc == null) {
      return;
    }
    Map<String, Object> snap = new LinkedHashMap<>();
    snap.put("title", tc.getTitle());
    snap.put("status", tc.getStatus());
    snap.put("priority", tc.getPriority());
    snap.put("description", tc.getDescription());
    snap.put("preconditions", tc.getPreconditions());
    snap.put("expectedResult", tc.getExpectedResult());
    snap.put(
        "steps",
        stepRepo.findByTestCaseIdOrderByStepNumberAsc(tcId).stream()
            .map(
                s -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("stepNumber", s.getStepNumber());
                  m.put("action", s.getAction());
                  m.put("expectedResult", s.getExpectedResult());
                  m.put("notes", s.getNotes());
                  return m;
                })
            .toList());
    snap.put(
        "tags",
        tagRepo.findByTestCaseIdOrderByNameAsc(tcId).stream().map(t -> t.getName()).toList());

    String json;
    try {
      json = om.writeValueAsString(snap);
    } catch (Exception e) {
      return; // don't let a serialization hiccup fail the caller's transaction
    }

    int version = revisionRepo.countByTestCaseId(tcId) + 1;
    String changedBy = CurrentUser.username();
    revisionRepo.save(new TestCaseRevision(tcId, projectId, version, json, changedBy, changeKind));
  }

  @Transactional(readOnly = true)
  public List<TestCaseRevisionDto> list(UUID tcId) {
    return revisionRepo.findByTestCaseIdOrderByVersionDesc(tcId).stream()
        .map(TestCaseRevisionDto::from)
        .toList();
  }
}
