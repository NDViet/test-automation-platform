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
import com.platform.core.domain.GeneratedTestCaseProposal;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.repository.AiGenerationFileRepository;
import com.platform.core.repository.AiGenerationRunRepository;
import com.platform.core.repository.AiSkillRepository;
import com.platform.core.repository.GeneratedTestCaseProposalRepository;
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
  @Mock GeneratedTestCaseProposalRepository proposalRepo;

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
            blobStore,
            proposalRepo);
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

  @Test
  void refineProposalUpdatesInPlaceAndAdvancesCheckpoint() {
    UUID wf = bundle.workflowId();
    AiGenerationRun run =
        new AiGenerationRun(wf, bundle.projectId(), "[]", null, "[]", null, null, 3);
    when(runRepo.findByWorkflowId(wf)).thenReturn(Optional.of(run));
    when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    GeneratedTestCaseProposal proposal =
        new GeneratedTestCaseProposal(
            wf, bundle.projectId(), 0, "Old title", "old", "p", "e", "HIGH", "req", "[]");
    when(proposalRepo.save(any())).thenAnswer(i -> i.getArgument(0));

    String revisedJson =
        "{\"title\":\"New clearer title\",\"description\":\"better\",\"priority\":\"low\","
            + "\"steps\":[{\"action\":\"do X\",\"expectedResult\":\"Y\",\"notes\":null}]}";
    NodeResult resumed =
        NodeResult.completed(
            bundle.sessionId(),
            wf,
            NodeType.TEST_GENERATION,
            AgentTaskType.GENERATE_TEST_CASES,
            ArtifactManifest.empty(),
            revisedJson,
            "chk-2",
            TokenUsage.zero());
    when(orchestrator.resume(any(), eq("chk-1"), any(), any())).thenReturn(resumed);

    NodeResult result = node.refineProposal(bundle, "chk-1", proposal, "make the title clearer");

    assertThat(result.needsReview()).isTrue();
    // Proposal updated in place, still PROPOSED.
    assertThat(proposal.getTitle()).isEqualTo("New clearer title");
    assertThat(proposal.getPriority()).isEqualTo("LOW"); // normalized
    assertThat(proposal.getStepsJson()).contains("do X");
    assertThat(proposal.getStatus()).isEqualTo("PROPOSED");
    verify(proposalRepo).save(proposal);
    // Conversation advanced — next refine resumes from the new checkpoint.
    assertThat(run.getReviewCheckpointId()).isEqualTo("chk-2");
    verify(testCaseRepo, never()).save(any()); // refine never touches the catalog
  }

  @Test
  void generatedCasesAreStagedAsProposalsNotDrafts() {
    when(runRepo.findByWorkflowId(bundle.workflowId())).thenReturn(Optional.empty());
    PlatformRequirement req =
        new PlatformRequirement(bundle.projectId(), null, "EXT-1", "Login page", "desc", "STORY");
    when(requirementRepo.findByProjectIdOrderByUpdatedAtDesc(bundle.projectId()))
        .thenReturn(List.of(req));
    lenient().when(testCaseRepo.findByProjectId(bundle.projectId())).thenReturn(List.of());
    String json =
        """
        [
          {"title":"Login happy path","description":"d1","preconditions":"p1",
           "priority":"HIGH","sourceRequirementId":"req-1",
           "steps":[{"action":"open login","expectedResult":"form shown","notes":null}]},
          {"title":"Login bad password","priority":"low","steps":[]}
        ]
        """;
    when(orchestrator.run(any(), any())).thenReturn(completed(json));

    NodeResult result = node.execute(bundle);

    // Parked for proposal review — nothing entered the catalog.
    assertThat(result.needsReview()).isTrue();
    verify(testCaseRepo, never()).save(any());
    verify(stepRepo, never()).save(any());

    ArgumentCaptor<GeneratedTestCaseProposal> cap =
        ArgumentCaptor.forClass(GeneratedTestCaseProposal.class);
    verify(proposalRepo, org.mockito.Mockito.times(2)).save(cap.capture());
    List<GeneratedTestCaseProposal> saved = cap.getAllValues();
    assertThat(saved).allMatch(p -> "PROPOSED".equals(p.getStatus()));
    assertThat(saved).extracting(GeneratedTestCaseProposal::getOrdinal).containsExactly(0, 1);
    assertThat(saved.get(0).getTitle()).isEqualTo("Login happy path");
    assertThat(saved.get(0).getStepsJson()).contains("open login");
    assertThat(saved.get(1).getPriority()).isEqualTo("LOW"); // normalized
  }
}
