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
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import com.platform.llm.LlmChatModelProvider;
import com.platform.llm.LlmSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  @Mock CredentialResolver credentialResolver;
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
            credentialResolver,
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

  @Test
  void fetchesPrDiffUsingTokenFromCredentialCascadeForGitHubTabRepos() {
    UUID id = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    ImpactAnalysis analysis = new ImpactAnalysis();
    Map<String, Object> pr = new LinkedHashMap<>();
    pr.put("repoFullName", "acme/app");
    pr.put("prNumber", 7);
    pr.put("prTitle", "Add feature");
    analysis.setLinkedPrs(List.of(pr));

    when(impactRepo.findById(id)).thenReturn(Optional.of(analysis));
    // No Integrations-tab CODEBASE config and no enabled GITHUB config → no token from configs.
    when(configRepo.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
            projectId, "GITHUB", "CODEBASE", true))
        .thenReturn(List.of());
    when(configRepo.findByProjectId(projectId)).thenReturn(List.of());
    // Token resolves via the Org→Project→Team GitHub credential cascade (GitHub-tab repos).
    when(credentialResolver.resolve(projectId, "GITHUB"))
        .thenReturn(
            Optional.of(
                new ResolvedCredential("GITHUB", null, Map.of(), Map.of("pat", "tok"), null)));
    when(gitHubApiClient.getPrFiles("acme", "app", 7, "tok")).thenReturn("[]");
    when(testCaseRepo.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
    lenient().when(settings.model(anyString(), anyString())).thenReturn("claude-sonnet-4-6");
    when(provider.chatModel(anyString())).thenReturn(null); // stop after diff collection

    service.runAnalysis(id, projectId);

    // The PR diff was fetched with the cascade-resolved token (previously null → empty diff).
    verify(gitHubApiClient).getPrFiles("acme", "app", 7, "tok");
  }

  @Test
  void extractMarkdownDiffsKeepsMarkdownTruncatesAndSkipsNonMarkdown() {
    Map<String, Object> md = new LinkedHashMap<>();
    md.put("filename", "docs/spec.md");
    md.put("patch", "+ # Spec\n" + "X".repeat(50));
    Map<String, Object> java = new LinkedHashMap<>();
    java.put("filename", "src/App.java");
    java.put("patch", "+ public class App {}");

    List<String> diffs = ImpactAnalysisService.extractMarkdownDiffs(List.of(md, java), 20, 1000);

    assertThat(diffs).hasSize(1);
    assertThat(diffs.get(0)).contains("docs/spec.md").contains("# Spec").contains("truncated");
    assertThat(String.join("", diffs)).doesNotContain("public class App");
  }

  @Test
  void buildUserMessageIncludesPrBodyMarkdownDiffsAndCustomPrompt() {
    ImpactAnalysisService.PrDiffContext pr =
        new ImpactAnalysisService.PrDiffContext(
            "acme/app",
            7,
            "Add checkout",
            "Adds the checkout flow.",
            List.of("src/Checkout.java", "docs/spec.md"),
            List.of("docs/spec.md\n+ # Checkout Spec"));

    String msg = service.buildUserMessage(List.of(pr), List.of(), List.of(), "Focus on refunds.");

    assertThat(msg).contains("Adds the checkout flow."); // PR body
    assertThat(msg).contains("src/Checkout.java"); // non-md filename still listed
    assertThat(msg).contains("# Checkout Spec"); // md diff content included
    assertThat(msg).contains("Focus on refunds."); // custom instructions
  }
}
