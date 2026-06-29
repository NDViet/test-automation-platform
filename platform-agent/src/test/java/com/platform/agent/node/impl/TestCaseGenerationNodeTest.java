package com.platform.agent.node.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.api.AiPromptTemplateService;
import com.platform.agent.contract.AgentGridFixtures;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.common.agent.AgentTaskType;
import com.platform.common.agent.ArtifactManifest;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.NodeResult;
import com.platform.common.agent.NodeType;
import com.platform.common.agent.TokenUsage;
import com.platform.common.storage.BlobStore;
import com.platform.core.domain.AiGenerationRun;
import com.platform.core.domain.AiSkill;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.repository.AiGenerationFileRepository;
import com.platform.core.repository.AiGenerationRunRepository;
import com.platform.core.repository.AiSkillRepository;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseStepRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCaseGenerationNodeTest {

  @Mock AgentOrchestrator orchestrator;
  @Mock PlatformRequirementRepository requirementRepo;
  @Mock PlatformTestCaseRepository testCaseRepo;
  @Mock TestCaseStepRepository stepRepo;
  @Mock AiGenerationRunRepository runRepo;
  @Mock AiPromptTemplateService promptTemplateService;
  @Mock AiSkillRepository skillRepo;
  @Mock AiGenerationFileRepository fileRepo;
  @Mock BlobStore blobStore;

  TestCaseGenerationNode node;
  ContextBundle bundle;

  @BeforeEach
  void setUp() {
    node =
        new TestCaseGenerationNode(
            orchestrator,
            requirementRepo,
            testCaseRepo,
            stepRepo,
            new ObjectMapper(),
            runRepo,
            promptTemplateService,
            skillRepo,
            fileRepo,
            blobStore);
    bundle =
        AgentGridFixtures.bundle(
            AgentGridFixtures.manualTrigger(AgentGridFixtures.PROJECT_ID.toString()));
  }

  private NodeResult completed(String summary) {
    return NodeResult.completed(
        bundle.sessionId(),
        bundle.workflowId(),
        NodeType.TEST_GENERATION,
        AgentTaskType.GENERATE_TEST_CASES,
        ArtifactManifest.empty(),
        summary,
        TokenUsage.zero());
  }

  private AgentNode captureShim() {
    ArgumentCaptor<AgentNode> shim = ArgumentCaptor.forClass(AgentNode.class);
    verify(orchestrator).run(any(), shim.capture());
    return shim.getValue();
  }

  @Test
  void newFlowAssemblesSkillsFreeTextDefaultsAndPersistsUsedPrompts() {
    UUID skillId = UUID.randomUUID();
    AiGenerationRun run =
        new AiGenerationRun(
            bundle.workflowId(),
            bundle.projectId(),
            "[\"" + skillId + "\"]",
            "focus on login flow",
            "[]",
            null,
            null,
            3);
    when(runRepo.findByWorkflowId(bundle.workflowId())).thenReturn(Optional.of(run));
    when(requirementRepo.findByProjectIdOrderByUpdatedAtDesc(bundle.projectId()))
        .thenReturn(List.of()); // free-text-only generation
    when(promptTemplateService.resolveDefault(bundle.projectId(), "SYSTEM"))
        .thenReturn("SYS DEFAULT");
    when(promptTemplateService.resolveDefault(bundle.projectId(), "USER"))
        .thenReturn("USR DEFAULT");
    when(skillRepo.findById(skillId))
        .thenReturn(
            Optional.of(
                new AiSkill(bundle.projectId(), "API", null, "COVER 4XX/5XX", true, "bob")));
    when(orchestrator.run(any(), any())).thenReturn(completed("[]"));
    when(runRepo.save(any(AiGenerationRun.class))).thenAnswer(i -> i.getArgument(0));

    node.execute(bundle);

    String sys = captureShim().systemPrompt(bundle);
    assertThat(sys).contains("SYS DEFAULT");
    assertThat(sys).contains("COVER 4XX/5XX");
    assertThat(sys).contains("focus on login flow");
    assertThat(sys).contains("USR DEFAULT");
    assertThat(sys).containsIgnoringCase("JSON array"); // mandatory output contract retained

    verify(runRepo).save(run);
    assertThat(run.getSystemPromptUsed()).contains("COVER 4XX/5XX");
    assertThat(run.getUserPromptUsed()).contains("focus on login flow");
  }

  @Test
  void legacyFlowWithoutRunUsesHardcodedPromptAndDoesNotPersistRun() {
    when(runRepo.findByWorkflowId(bundle.workflowId())).thenReturn(Optional.empty());
    PlatformRequirement req =
        new PlatformRequirement(bundle.projectId(), null, "EXT-1", "Login page", "desc", "STORY");
    when(requirementRepo.findByProjectIdOrderByUpdatedAtDesc(bundle.projectId()))
        .thenReturn(List.of(req));
    lenient().when(testCaseRepo.findByProjectId(bundle.projectId())).thenReturn(List.of());
    when(orchestrator.run(any(), any())).thenReturn(completed("[]"));

    node.execute(bundle);

    String sys = captureShim().systemPrompt(bundle);
    assertThat(sys).contains("TestCaseGenerationNode — a QA expert");
    assertThat(sys).contains("Login page"); // requirement embedded
    assertThat(sys).containsIgnoringCase("Output format");

    verify(runRepo, never()).save(any());
    verify(promptTemplateService, never()).resolveDefault(any(), eq("SYSTEM"));
  }
}
