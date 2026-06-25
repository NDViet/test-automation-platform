package com.platform.agent.hub.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.platform.agent.contract.AgentGridFixtures;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.SotReleaseRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for RequirementChangeProcessor. Verifies SHA-256 hash change detection and TC status
 * mutation contract.
 */
@ExtendWith(MockitoExtension.class)
class RequirementChangeProcessorTest {

  @Mock private PlatformRequirementRepository requirementRepo;
  @Mock private PlatformTestCaseRepository testCaseRepo;
  @Mock private SotReleaseRepository releaseRepo;
  @Mock private GraphService graphService;

  private RequirementChangeProcessor processor;

  private final UUID projectId = AgentGridFixtures.PROJECT_ID;
  private final UUID requirementId = AgentGridFixtures.REQUIREMENT_ID;

  @BeforeEach
  void setUp() {
    processor =
        new RequirementChangeProcessor(requirementRepo, testCaseRepo, releaseRepo, graphService);
  }

  @Test
  void computeImpact_requirementNotFound_returnsNull() {
    when(requirementRepo.findById(requirementId)).thenReturn(Optional.empty());

    var result = processor.computeImpact(projectId, requirementId, "Title", "Desc");
    assertThat(result).isNull();
    verify(testCaseRepo, never()).save(any());
  }

  @Test
  void computeImpact_noHashChange_returnsNull() {
    // Simulate: existing hash matches what we'd compute for the same title+description
    PlatformRequirement req = makeReqWithHash("My Title", "My Desc");
    when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));

    // Same content → same hash → no impact
    var result = processor.computeImpact(projectId, requirementId, "My Title", "My Desc");
    assertThat(result).isNull();
    verify(testCaseRepo, never()).save(any());
  }

  @Test
  void computeImpact_titleChanged_marksTcsNeedsUpdate() {
    PlatformRequirement req = makeReqWithHash("Old Title", "Same Desc");
    when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));

    UUID tc1Id = UUID.randomUUID();
    UUID tc2Id = UUID.randomUUID();
    PlatformTestCase tc1 = makeTc(tc1Id, "ACTIVE");
    PlatformTestCase tc2 = makeTc(tc2Id, "ACTIVE");
    when(graphService.getTestCases(projectId, requirementId)).thenReturn(List.of(tc1, tc2));
    when(testCaseRepo.findById(tc1Id)).thenReturn(Optional.of(tc1));
    when(testCaseRepo.findById(tc2Id)).thenReturn(Optional.of(tc2));
    when(testCaseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(graphService.getReleasesFor(projectId, requirementId)).thenReturn(List.of("v2.0"));

    var impact = processor.computeImpact(projectId, requirementId, "New Title", "Same Desc");

    assertThat(impact).isNotNull();
    assertThat(impact.hasImpact()).isTrue();
    assertThat(impact.testCasesToUpdate()).hasSize(2);
    assertThat(impact.affectedReleases()).containsExactly("v2.0");
    assertThat(tc1.getCoverageStatus()).isEqualTo("NEEDS_UPDATE");
    assertThat(tc2.getCoverageStatus()).isEqualTo("NEEDS_UPDATE");
    verify(testCaseRepo, times(2)).save(any(PlatformTestCase.class));
  }

  @Test
  void computeImpact_descriptionChanged_marksActiveTcs() {
    PlatformRequirement req = makeReqWithHash("Same Title", "Old Desc");
    when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));

    UUID tcId = UUID.randomUUID();
    PlatformTestCase tc = makeTc(tcId, "ACTIVE");
    when(graphService.getTestCases(projectId, requirementId)).thenReturn(List.of(tc));
    when(testCaseRepo.findById(tcId)).thenReturn(Optional.of(tc));
    when(testCaseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(graphService.getReleasesFor(projectId, requirementId)).thenReturn(List.of());

    var impact = processor.computeImpact(projectId, requirementId, "Same Title", "New Desc");

    assertThat(impact).isNotNull();
    assertThat(impact.testCasesToUpdate()).hasSize(1);
  }

  @Test
  void computeImpact_needsUpdateTcsGoToObsoleteCandidates() {
    PlatformRequirement req = makeReqWithHash("Old", "Old");
    when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));

    UUID obsoleteId = UUID.randomUUID();
    PlatformTestCase obsoleteTc = makeTc(obsoleteId, "NEEDS_UPDATE");
    when(graphService.getTestCases(projectId, requirementId)).thenReturn(List.of(obsoleteTc));
    when(graphService.getReleasesFor(projectId, requirementId)).thenReturn(List.of());

    var impact = processor.computeImpact(projectId, requirementId, "New", "New");

    assertThat(impact).isNotNull();
    assertThat(impact.testCasesToUpdate()).isEmpty(); // NEEDS_UPDATE not in toUpdate
    assertThat(impact.obsoleteCandidates()).containsExactly(obsoleteId);
    verify(testCaseRepo, never()).save(any());
  }

  @Test
  void computeImpact_noTestCases_returnsEmptyImpact() {
    PlatformRequirement req = makeReqWithHash("Old", "Old");
    when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));
    when(graphService.getTestCases(projectId, requirementId)).thenReturn(List.of());
    when(graphService.getReleasesFor(projectId, requirementId)).thenReturn(List.of());

    var impact = processor.computeImpact(projectId, requirementId, "Changed", "Changed too");

    assertThat(impact).isNotNull();
    assertThat(impact.hasImpact()).isFalse();
    assertThat(impact.testCasesToUpdate()).isEmpty();
    assertThat(impact.obsoleteCandidates()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /** Creates a requirement whose version_hash matches what SHA-256(title+\0+desc) would produce. */
  private PlatformRequirement makeReqWithHash(String title, String description) {
    PlatformRequirement req =
        new PlatformRequirement(projectId, null, "EXT-1", title, description, "STORY");
    setField(req, "id", requirementId);
    // Compute the same hash the processor uses
    String hash = sha256(title + "\0" + description);
    setField(req, "versionHash", hash);
    return req;
  }

  private PlatformTestCase makeTc(UUID id, String status) {
    PlatformTestCase tc = new PlatformTestCase(projectId, "Test case", List.of(), "AGENT", null);
    setField(tc, "id", id);
    setField(tc, "coverageStatus", status);
    return tc;
  }

  private String sha256(String input) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void setField(Object obj, String fieldName, Object value) {
    try {
      java.lang.reflect.Field f = findField(obj.getClass(), fieldName);
      f.setAccessible(true);
      f.set(obj, value);
    } catch (Exception e) {
      throw new RuntimeException("Cannot set field " + fieldName, e);
    }
  }

  private java.lang.reflect.Field findField(Class<?> clazz, String name) {
    Class<?> c = clazz;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        c = c.getSuperclass();
      }
    }
    throw new RuntimeException("Field not found: " + name);
  }
}
