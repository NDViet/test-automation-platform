package com.platform.agent.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.AiGenerationRun;
import com.platform.core.domain.ImpactAnalysis;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.AiGenerationRunRepository;
import com.platform.core.repository.ImpactAnalysisRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PrContextServiceTest {

  @Mock AgentWorkflowRepository workflowRepo;
  @Mock AiGenerationRunRepository runRepo;
  @Mock ImpactAnalysisRepository impactRepo;

  PrContextService service;
  private final UUID projectId = UUID.randomUUID();
  private final UUID workflowId = UUID.randomUUID();
  private final String reqA = UUID.randomUUID().toString();
  private final String reqB = UUID.randomUUID().toString();
  private final String reqOther = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    service = new PrContextService(workflowRepo, runRepo, impactRepo, new ObjectMapper());
    lenient()
        .when(workflowRepo.findById(workflowId))
        .thenReturn(Optional.of(new AgentWorkflow(projectId, "MANUAL", null, Map.of())));
  }

  private AiGenerationRun runWithRequirements(String requirementIdsJson) {
    AiGenerationRun run =
        new AiGenerationRun(workflowId, projectId, "[]", null, null, null, null, 3);
    run.recordRequirementSet(requirementIdsJson);
    return run;
  }

  private ImpactAnalysis analysis(String name, List<String> reqIds, int prNumber) {
    ImpactAnalysis a = new ImpactAnalysis();
    org.springframework.test.util.ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
    a.setProjectId(projectId);
    a.setName(name);
    a.setLinkedRequirementIds(reqIds);
    a.setLinkedPrs(List.of(Map.of("number", prNumber, "title", "PR " + prNumber)));
    a.setSummary("summary " + name);
    return a;
  }

  @Test
  void returnsOnlyAnalysesIntersectingWorkflowRequirements() {
    when(runRepo.findByWorkflowId(workflowId))
        .thenReturn(Optional.of(runWithRequirements("[\"" + reqA + "\",\"" + reqB + "\"]")));
    ImpactAnalysis matching = analysis("matching", List.of(reqB), 42);
    ImpactAnalysis unrelated = analysis("unrelated", List.of(reqOther), 99);
    when(impactRepo.findByProjectIdOrderByCreatedAtDesc(projectId))
        .thenReturn(List.of(matching, unrelated));

    List<PrContextService.PrContextItem> items = service.forWorkflow(projectId, workflowId);

    assertThat(items).hasSize(1);
    assertThat(items.get(0).name()).isEqualTo("matching");
    assertThat(items.get(0).prs()).hasSize(1);
    assertThat(items.get(0).summary()).isEqualTo("summary matching");
  }

  @Test
  void emptyRequirementSetReturnsAllAnalysesWithPrs() {
    when(runRepo.findByWorkflowId(workflowId)).thenReturn(Optional.of(runWithRequirements("[]")));
    when(impactRepo.findByProjectIdOrderByCreatedAtDesc(projectId))
        .thenReturn(List.of(analysis("a", List.of(reqA), 1), analysis("b", List.of(reqOther), 2)));

    List<PrContextService.PrContextItem> items = service.forWorkflow(projectId, workflowId);

    assertThat(items).hasSize(2);
  }

  @Test
  void excludesAnalysesWithoutPrs() {
    when(runRepo.findByWorkflowId(workflowId)).thenReturn(Optional.of(runWithRequirements("[]")));
    ImpactAnalysis noPrs = analysis("noprs", List.of(reqA), 1);
    noPrs.setLinkedPrs(List.of());
    when(impactRepo.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(noPrs));

    assertThat(service.forWorkflow(projectId, workflowId)).isEmpty();
  }

  @Test
  void unknownWorkflowIs404() {
    when(workflowRepo.findById(workflowId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.forWorkflow(projectId, workflowId))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void foreignProjectIs404() {
    UUID otherProject = UUID.randomUUID();

    assertThatThrownBy(() -> service.forWorkflow(otherProject, workflowId))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
