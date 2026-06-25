package com.platform.agent.hub.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.platform.agent.contract.AgentGridFixtures;
import com.platform.agent.hub.graph.GraphService;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.common.agent.*;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.*;
import com.platform.core.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

/**
 * Unit tests for DefaultContextAssembler. Verifies the Hub→Node ContextBundle assembly contract: -
 * requirementContext populated when trigger has an entityExternalId - prDiff pre-fetched for GitHub
 * webhook triggers - executionContext always populated from live data
 */
@ExtendWith(MockitoExtension.class)
class DefaultContextAssemblerTest {

  @Mock private ProjectRepository projectRepo;
  @Mock private TestExecutionRepository executionRepo;
  @Mock private TestCaseResultRepository testCaseResultRepo;
  @Mock private FlakinessScoreRepository flakinessRepo;
  @Mock private AgentWorkflowRepository workflowRepo;
  @Mock private PlatformRequirementRepository requirementRepo;
  @Mock private com.platform.common.storage.BlobStore blobStore;
  @Mock private GraphService graphService;
  @Mock private GitHubApiClient gitHubApiClient;

  private DefaultContextAssembler assembler;

  private final UUID projectId = AgentGridFixtures.PROJECT_ID;
  private final UUID workflowId = AgentGridFixtures.WORKFLOW_ID;

  @BeforeEach
  void setUp() {
    assembler =
        new DefaultContextAssembler(
            projectRepo,
            executionRepo,
            testCaseResultRepo,
            flakinessRepo,
            workflowRepo,
            requirementRepo,
            blobStore,
            graphService);
    assembler.setGitHubApiClient(gitHubApiClient);

    // Always return a project
    com.platform.core.domain.Organization org =
        new com.platform.core.domain.Organization("Test Org", "test-org");
    Project project = new Project(org, "Test Project", "test-project");
    setField(project, "id", projectId);
    when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));

    // Default: empty executions — lenient so individual tests can override without
    // UnnecessaryStubbing
    lenient()
        .when(executionRepo.findByProjectIdOrderByExecutedAtDesc(eq(projectId), any()))
        .thenReturn(new PageImpl<>(List.of()));
    lenient().when(executionRepo.computePassRate(eq(projectId), any())).thenReturn(null);
    lenient().when(flakinessRepo.findTopFlakyByProject(eq(projectId), any())).thenReturn(List.of());
    lenient()
        .when(executionRepo.findByProjectAndDateRange(eq(projectId), any(), any()))
        .thenReturn(List.of());
  }

  // -------------------------------------------------------------------------
  // requirementContext population
  // -------------------------------------------------------------------------

  @Test
  void assemble_noEntityExternalId_requirementContextIsNull() {
    TriggerRef trigger =
        new TriggerRef(
            TriggerRef.TriggerType.MANUAL,
            IntegrationType.JIRA_CLOUD,
            "issue",
            null,
            null,
            "user",
            Instant.now());

    ContextBundle bundle = assembler.assemble(workflowId, projectId, trigger);

    assertThat(bundle.requirementContext()).isNull();
    verifyNoInteractions(requirementRepo);
  }

  @Test
  void assemble_entityExternalIdPresent_requirementContextPopulated() {
    TriggerRef trigger = AgentGridFixtures.manualTrigger("PROJ-42");

    PlatformRequirement req = makeRequirement(AgentGridFixtures.REQUIREMENT_ID, "PROJ-42");
    when(requirementRepo.findByProjectIdAndExternalId(projectId, "PROJ-42"))
        .thenReturn(Optional.of(req));

    RequirementContext reqCtx =
        new RequirementContext(
            null, List.of(), List.of(), List.of(), List.of(), TestGenMode.CREATE_ALL);
    when(graphService.buildRequirementContext(projectId, AgentGridFixtures.REQUIREMENT_ID))
        .thenReturn(reqCtx);

    ContextBundle bundle = assembler.assemble(workflowId, projectId, trigger);

    assertThat(bundle.requirementContext()).isNotNull();
    assertThat(bundle.requirementContext()).isSameAs(reqCtx);
    verify(requirementRepo).findByProjectIdAndExternalId(projectId, "PROJ-42");
    verify(graphService).buildRequirementContext(projectId, AgentGridFixtures.REQUIREMENT_ID);
  }

  @Test
  void assemble_entityExternalIdNotInDb_requirementContextIsNull() {
    TriggerRef trigger = AgentGridFixtures.manualTrigger("UNKNOWN-99");
    when(requirementRepo.findByProjectIdAndExternalId(projectId, "UNKNOWN-99"))
        .thenReturn(Optional.empty());

    ContextBundle bundle = assembler.assemble(workflowId, projectId, trigger);
    assertThat(bundle.requirementContext()).isNull();
  }

  // -------------------------------------------------------------------------
  // prDiff pre-fetch
  // -------------------------------------------------------------------------

  @Test
  void assemble_githubWebhookWithPrUrl_preDiffFetched() throws Exception {
    TriggerRef trigger =
        new TriggerRef(
            TriggerRef.TriggerType.WEBHOOK,
            IntegrationType.GITHUB,
            "pull_request",
            "42",
            "https://github.com/acme/myrepo/pull/42",
            "bot",
            Instant.now());

    String prFilesJson = "[{\"filename\":\"src/Foo.java\"}]";
    when(gitHubApiClient.getPrFiles("acme", "myrepo", 42, "")).thenReturn(prFilesJson);

    com.platform.common.storage.BlobRef stored = AgentGridFixtures.blobRef("ab/abcdef123");
    when(blobStore.storeText(
            any(), eq(prFilesJson), eq(com.platform.common.storage.BlobRef.TYPE_JSON)))
        .thenReturn(stored);

    ContextBundle bundle = assembler.assemble(workflowId, projectId, trigger);

    assertThat(bundle.prDiff()).isNotNull();
    assertThat(bundle.prDiff().key()).isEqualTo("ab/abcdef123");
    verify(gitHubApiClient).getPrFiles("acme", "myrepo", 42, "");
  }

  @Test
  void assemble_githubWebhookNoPrUrl_preDiffNotFetched() {
    TriggerRef trigger =
        new TriggerRef(
            TriggerRef.TriggerType.WEBHOOK,
            IntegrationType.GITHUB,
            "push",
            null,
            null,
            "bot",
            Instant.now());

    ContextBundle bundle = assembler.assemble(workflowId, projectId, trigger);
    assertThat(bundle.prDiff()).isNull();
    verifyNoInteractions(gitHubApiClient);
  }

  @Test
  void assemble_githubClientNull_preDiffSkipped() {
    assembler.setGitHubApiClient(null);
    ContextBundle bundle =
        assembler.assemble(workflowId, projectId, AgentGridFixtures.webhookTrigger());
    assertThat(bundle.prDiff()).isNull();
  }

  // -------------------------------------------------------------------------
  // executionContext always present
  // -------------------------------------------------------------------------

  @Test
  void assemble_executionContextAlwaysPopulated() {
    ContextBundle bundle =
        assembler.assemble(workflowId, projectId, AgentGridFixtures.webhookTrigger());
    assertThat(bundle.executionContext()).isNotNull();
  }

  @Test
  void assemble_withPassRate_executionContextReflectsIt() {
    when(executionRepo.computePassRate(eq(projectId), any())).thenReturn(0.92);
    ContextBundle bundle =
        assembler.assemble(workflowId, projectId, AgentGridFixtures.scheduleTrigger());
    assertThat(bundle.executionContext().passRate7d()).isEqualTo(0.92);
  }

  // -------------------------------------------------------------------------
  // taskType inference from trigger
  // -------------------------------------------------------------------------

  @Test
  void assemble_webhookTrigger_inferencesAnalyzeAndCoverage() {
    ContextBundle bundle =
        assembler.assemble(workflowId, projectId, AgentGridFixtures.webhookTrigger());
    assertThat(bundle.taskTypes())
        .containsExactlyInAnyOrder(
            AgentTaskType.ANALYZE_PR_DIFF, AgentTaskType.DETECT_COVERAGE_GAPS);
  }

  @Test
  void assemble_scheduleTrigger_inferencesDigestAndFlakiness() {
    ContextBundle bundle =
        assembler.assemble(workflowId, projectId, AgentGridFixtures.scheduleTrigger());
    assertThat(bundle.taskTypes())
        .containsExactlyInAnyOrder(
            AgentTaskType.GENERATE_NIGHTLY_DIGEST, AgentTaskType.INVESTIGATE_FLAKINESS);
  }

  // -------------------------------------------------------------------------
  // invariants
  // -------------------------------------------------------------------------

  @Test
  void assemble_sessionIdIsUnique() {
    ContextBundle b1 =
        assembler.assemble(workflowId, projectId, AgentGridFixtures.scheduleTrigger());
    ContextBundle b2 =
        assembler.assemble(workflowId, projectId, AgentGridFixtures.scheduleTrigger());
    assertThat(b1.sessionId()).isNotEqualTo(b2.sessionId());
  }

  @Test
  void assemble_projectIdAndSlugAlwaysSet() {
    ContextBundle bundle =
        assembler.assemble(workflowId, projectId, AgentGridFixtures.scheduleTrigger());
    assertThat(bundle.projectId()).isEqualTo(projectId);
    assertThat(bundle.projectSlug()).isEqualTo("test-project");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private PlatformRequirement makeRequirement(UUID id, String externalId) {
    PlatformRequirement req =
        new PlatformRequirement(projectId, null, externalId, "Req title", "Desc", "STORY");
    setField(req, "id", id);
    return req;
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
