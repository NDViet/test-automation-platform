package com.platform.core.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.QualityGate;
import com.platform.core.repository.QualityGateRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QualityGateServiceTest {

  @Mock QualityGateRepository repo;
  QualityGateService service;

  private final UUID pid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new QualityGateService(repo);
  }

  @Test
  void getReturnsTransientDefaultsWhenNoneConfigured() {
    when(repo.findByProjectId(pid)).thenReturn(Optional.empty());

    QualityGate g = service.get(pid);

    assertThat(g.isEnabled()).isTrue();
    assertThat(g.getMinPassRate()).isEqualTo(0.90);
    verify(repo, never()).save(any());
  }

  @Test
  void upsertCreatesWhenNoneExists() {
    when(repo.findByProjectId(pid)).thenReturn(Optional.empty());
    when(repo.save(any(QualityGate.class))).thenAnswer(i -> i.getArgument(0));

    QualityGate g = service.upsert(pid, true, 0.85, 70, 3, 1, true);

    assertThat(g.getMinPassRate()).isEqualTo(0.85);
    assertThat(g.getMaxOpenBlockers()).isEqualTo(3);
    assertThat(g.isRequireSuitesExecuted()).isTrue();
    verify(repo).save(g);
  }

  @Test
  void upsertUpdatesExisting() {
    QualityGate existing = new QualityGate(pid);
    when(repo.findByProjectId(pid)).thenReturn(Optional.of(existing));
    when(repo.save(any(QualityGate.class))).thenAnswer(i -> i.getArgument(0));

    QualityGate g = service.upsert(pid, false, 0.5, 10, 5, 2, false);

    assertThat(g).isSameAs(existing);
    assertThat(g.isEnabled()).isFalse();
    assertThat(g.getMinCoveragePct()).isEqualTo(10);
  }
}
