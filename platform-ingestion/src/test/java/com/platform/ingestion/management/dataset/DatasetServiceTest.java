package com.platform.ingestion.management.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.TestDataset;
import com.platform.core.repository.TestDatasetRepository;
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
class DatasetServiceTest {

  @Mock TestDatasetRepository repo;
  DatasetService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new DatasetService(repo, new ObjectMapper());
  }

  @Test
  void createPersistsNameAndRows() {
    when(repo.save(any(TestDataset.class))).thenAnswer(i -> i.getArgument(0));

    DatasetDto dto = service.create(pid, new DatasetForm("logins", "qa", Map.of("user", "alice")));

    assertThat(dto.name()).isEqualTo("logins");
    assertThat(dto.env()).isEqualTo("qa");
    assertThat(dto.rows()).containsEntry("user", "alice");
    verify(repo).save(any(TestDataset.class));
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> service.create(pid, new DatasetForm("  ", null, Map.of())))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void getScopedThrowsWhenMissing() {
    when(repo.findByIdAndProjectId(id, pid)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(pid, id)).isInstanceOf(ResponseStatusException.class);
  }
}
