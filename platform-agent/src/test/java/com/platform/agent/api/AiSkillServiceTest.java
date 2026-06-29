package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.AiSkill;
import com.platform.core.repository.AiSkillRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AiSkillServiceTest {

  @Mock AiSkillRepository repo;

  AiSkillService service;

  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new AiSkillService(repo);
  }

  @Test
  void createPersistsProjectScopedSkill() {
    when(repo.existsByProjectIdAndName(projectId, "API testing")).thenReturn(false);
    when(repo.save(any(AiSkill.class))).thenAnswer(inv -> inv.getArgument(0));

    AiSkillDto dto =
        service.create(
            projectId,
            new AiSkillRequest("API testing", "REST heuristics", "Always test 4xx/5xx", true),
            "alice");

    assertThat(dto.name()).isEqualTo("API testing");
    assertThat(dto.instructions()).isEqualTo("Always test 4xx/5xx");
    assertThat(dto.enabled()).isTrue();
    assertThat(dto.createdBy()).isEqualTo("alice");
    verify(repo).save(any(AiSkill.class));
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(
            () -> service.create(projectId, new AiSkillRequest("  ", null, "x", true), "alice"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    verify(repo, never()).save(any());
  }

  @Test
  void createRejectsDuplicateNameInSameProject() {
    when(repo.existsByProjectIdAndName(projectId, "API testing")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    projectId, new AiSkillRequest("API testing", null, "x", true), "alice"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    verify(repo, never()).save(any());
  }

  @Test
  void listReturnsProjectScopedSkills() {
    AiSkill a = new AiSkill(projectId, "A", null, "ia", true, "bob");
    AiSkill b = new AiSkill(projectId, "B", null, "ib", false, "bob");
    when(repo.findByProjectIdOrderByNameAsc(projectId)).thenReturn(List.of(a, b));

    List<AiSkillDto> dtos = service.list(projectId);

    assertThat(dtos).extracting(AiSkillDto::name).containsExactly("A", "B");
  }

  @Test
  void getRejectsCrossProjectAccess() {
    UUID otherProject = UUID.randomUUID();
    UUID skillId = UUID.randomUUID();
    AiSkill foreign = new AiSkill(otherProject, "X", null, "i", true, "bob");
    when(repo.findById(skillId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.get(projectId, skillId))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(404));
  }

  @Test
  void updateChangesFields() {
    UUID skillId = UUID.randomUUID();
    AiSkill existing = new AiSkill(projectId, "Old", "d", "old instr", true, "bob");
    when(repo.findById(skillId)).thenReturn(Optional.of(existing));
    when(repo.existsByProjectIdAndName(projectId, "New")).thenReturn(false);
    when(repo.save(any(AiSkill.class))).thenAnswer(inv -> inv.getArgument(0));

    AiSkillDto dto =
        service.update(
            projectId, skillId, new AiSkillRequest("New", "d2", "new instr", false), "carol");

    assertThat(dto.name()).isEqualTo("New");
    assertThat(dto.instructions()).isEqualTo("new instr");
    assertThat(dto.enabled()).isFalse();
    verify(repo).save(existing);
  }

  @Test
  void deleteRejectsUnknownSkill() {
    UUID skillId = UUID.randomUUID();
    when(repo.findById(skillId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(projectId, skillId))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    verify(repo, never()).delete(any());
  }

  @Test
  void deleteRemovesOwnedSkill() {
    UUID skillId = UUID.randomUUID();
    AiSkill existing = new AiSkill(projectId, "X", null, "i", true, "bob");
    when(repo.findById(skillId)).thenReturn(Optional.of(existing));

    service.delete(projectId, skillId);

    verify(repo).delete(existing);
  }

  private static int status(Throwable e) {
    return ((ResponseStatusException) e).getStatusCode().value();
  }
}
