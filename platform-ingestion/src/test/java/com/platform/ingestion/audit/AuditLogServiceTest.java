package com.platform.ingestion.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.AuditEvent;
import com.platform.core.repository.AuditEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

  @Mock AuditEventRepository repo;
  AuditLogService service;

  private final UUID pid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new AuditLogService(repo);
  }

  @Test
  void listMapsEventsAndPassesBlankFiltersAsNull() {
    AuditEvent e =
        AuditEvent.builder()
            .eventType("TEST_CASE_APPROVED")
            .projectId(pid)
            .actorUsername("bob")
            .resourceType("TEST_CASE")
            .outcome("SUCCESS")
            .build();
    when(repo.findForProject(eq(pid), isNull(), isNull(), any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(e));

    var out = service.list(pid, "  ", "", 30, 200);

    assertThat(out).hasSize(1);
    assertThat(out.get(0).eventType()).isEqualTo("TEST_CASE_APPROVED");
    assertThat(out.get(0).actorUsername()).isEqualTo("bob");
  }

  @Test
  void listCapsLimitAt500() {
    when(repo.findForProject(any(), any(), any(), any(), any())).thenReturn(List.of());

    service.list(pid, null, null, 30, 100000);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(repo).findForProject(any(), any(), any(), any(), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(500);
  }
}
