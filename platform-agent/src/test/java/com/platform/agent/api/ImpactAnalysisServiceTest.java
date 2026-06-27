package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.core.domain.ImpactAnalysis;
import com.platform.core.repository.ImpactAnalysisRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.llm.LlmChatModelProvider;
import com.platform.llm.LlmSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisServiceTest {

  @Mock ImpactAnalysisRepository impactRepo;
  @Mock ProjectIntegrationConfigRepository configRepo;
  @Mock PlatformTestCaseRepository testCaseRepo;
  @Mock GitHubApiClient gitHubApiClient;
  @Mock LlmChatModelProvider provider;
  @Mock LlmSettings settings;

  ImpactAnalysisService service;

  @BeforeEach
  void setUp() {
    service =
        new ImpactAnalysisService(
            impactRepo,
            configRepo,
            testCaseRepo,
            gitHubApiClient,
            provider,
            settings,
            new ObjectMapper(),
            "http://localhost:8083");
  }

  @Test
  void marksFailedWhenLiteLlmNotConfigured() {
    UUID id = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    ImpactAnalysis analysis = new ImpactAnalysis();
    when(impactRepo.findById(id)).thenReturn(Optional.of(analysis));
    when(testCaseRepo.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
    lenient().when(settings.model(anyString(), anyString())).thenReturn("claude-sonnet-4-6");
    when(provider.chatModel(anyString())).thenReturn(null);

    service.runAnalysis(id, projectId);

    assertThat(analysis.getStatus()).isEqualTo("FAILED");
    verify(impactRepo).save(analysis);
  }
}
