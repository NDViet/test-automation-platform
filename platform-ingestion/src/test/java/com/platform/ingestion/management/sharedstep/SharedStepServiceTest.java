package com.platform.ingestion.management.sharedstep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.SharedStep;
import com.platform.core.repository.SharedStepRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SharedStepServiceTest {

  @Mock SharedStepRepository repo;
  SharedStepService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new SharedStepService(repo);
  }

  @Test
  void createPersistsAction() {
    when(repo.save(any(SharedStep.class))).thenAnswer(i -> i.getArgument(0));

    SharedStepDto dto = service.create(pid, new SharedStepForm("Log in", "logged in", null));

    assertThat(dto.action()).isEqualTo("Log in");
    verify(repo).save(any(SharedStep.class));
  }

  @Test
  void createRejectsBlankAction() {
    assertThatThrownBy(() -> service.create(pid, new SharedStepForm("  ", null, null)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void getScopedThrowsWhenMissing() {
    when(repo.findByIdAndProjectId(id, pid)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(pid, id)).isInstanceOf(ResponseStatusException.class);
  }
}
