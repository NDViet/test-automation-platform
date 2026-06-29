package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.AiPromptTemplate;
import com.platform.core.repository.AiPromptTemplateRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AiPromptTemplateServiceTest {

  @Mock AiPromptTemplateRepository repo;
  AiPromptTemplateService service;

  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new AiPromptTemplateService(repo);
  }

  @Test
  void createRejectsUnknownKind() {
    assertThatThrownBy(
            () ->
                service.create(
                    projectId, new AiPromptTemplateRequest("BOGUS", "n", "b", false), "alice"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    verify(repo, never()).save(any());
  }

  @Test
  void createDefaultClearsPriorDefaultOfSameKind() {
    AiPromptTemplate prior =
        new AiPromptTemplate(projectId, "SYSTEM", "old default", "old body", true, "bob");
    when(repo.existsByProjectIdAndKindAndName(projectId, "SYSTEM", "new")).thenReturn(false);
    when(repo.findByProjectIdAndKindAndIsDefaultTrue(projectId, "SYSTEM"))
        .thenReturn(Optional.of(prior));
    when(repo.save(any(AiPromptTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

    service.create(projectId, new AiPromptTemplateRequest("SYSTEM", "new", "new body", true), "carol");

    // prior default is demoted (two unsaved entities share a null id, so we assert on state
    // rather than verify(save) which equals() would conflate)
    assertThat(prior.isDefault()).isFalse();
  }

  @Test
  void resolveDefaultReturnsStoredDefaultBody() {
    AiPromptTemplate def =
        new AiPromptTemplate(projectId, "SYSTEM", "d", "STORED SYSTEM PROMPT", true, "bob");
    when(repo.findByProjectIdAndKindAndIsDefaultTrue(projectId, "SYSTEM"))
        .thenReturn(Optional.of(def));

    assertThat(service.resolveDefault(projectId, "SYSTEM")).isEqualTo("STORED SYSTEM PROMPT");
  }

  @Test
  void resolveDefaultReturnsSeededFallbackWhenNone() {
    when(repo.findByProjectIdAndKindAndIsDefaultTrue(projectId, "SYSTEM"))
        .thenReturn(Optional.empty());
    when(repo.findByProjectIdAndKindAndIsDefaultTrue(projectId, "USER"))
        .thenReturn(Optional.empty());

    String system = service.resolveDefault(projectId, "SYSTEM");
    String user = service.resolveDefault(projectId, "USER");

    assertThat(system).isNotBlank();
    assertThat(user).isNotBlank();
    assertThat(system).isNotEqualTo(user); // distinct seeds per kind
  }

  @Test
  void createRejectsDuplicateName() {
    when(repo.existsByProjectIdAndKindAndName(projectId, "USER", "dup")).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.create(
                    projectId, new AiPromptTemplateRequest("USER", "dup", "b", false), "alice"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(409));
  }

  @Test
  void getRejectsCrossProjectAccess() {
    UUID id = UUID.randomUUID();
    AiPromptTemplate foreign =
        new AiPromptTemplate(UUID.randomUUID(), "USER", "x", "b", false, "bob");
    when(repo.findById(id)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.get(projectId, id))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(404));
  }

  private static int status(Throwable e) {
    return ((ResponseStatusException) e).getStatusCode().value();
  }
}
