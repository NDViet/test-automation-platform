package com.platform.ingestion.management.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.RunSchedule;
import com.platform.core.repository.RunScheduleRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RunScheduleServiceTest {

  @Mock RunScheduleRepository repo;
  RunScheduleService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new RunScheduleService(repo, new ObjectMapper());
  }

  private RunScheduleForm form(int interval) {
    return new RunScheduleForm(
        "Nightly", "acme/app", 42L, "main", Map.of("suite", "smoke"), interval, true);
  }

  @Test
  void createSeedsNextRunAndSerializesInputs() {
    when(repo.save(any(RunSchedule.class))).thenAnswer(i -> i.getArgument(0));

    RunScheduleDto dto = service.create(pid, form(60));

    ArgumentCaptor<RunSchedule> saved = ArgumentCaptor.forClass(RunSchedule.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().getNextRunAt()).isNotNull();
    assertThat(saved.getValue().getWorkflowId()).isEqualTo(42L);
    assertThat(dto.inputs()).containsEntry("suite", "smoke");
  }

  @Test
  void createRejectsNonPositiveInterval() {
    assertThatThrownBy(() -> service.create(pid, form(0)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void getScopedThrowsWhenMissing() {
    when(repo.findByIdAndProjectId(id, pid)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(pid, id)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void deleteRemovesScopedSchedule() {
    RunSchedule s = new RunSchedule(pid, "S", "acme/app", 1L, "main", null, 30, true, null);
    when(repo.findByIdAndProjectId(id, pid)).thenReturn(Optional.of(s));

    service.delete(pid, id);

    verify(repo).delete(s);
  }
}
