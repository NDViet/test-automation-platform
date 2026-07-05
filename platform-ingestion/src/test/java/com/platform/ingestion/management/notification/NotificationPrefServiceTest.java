package com.platform.ingestion.management.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.NotificationPref;
import com.platform.core.repository.NotificationPrefRepository;
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
class NotificationPrefServiceTest {

  @Mock NotificationPrefRepository repo;
  NotificationPrefService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new NotificationPrefService(repo, new ObjectMapper());
  }

  @Test
  void createPersistsChannelAndEvents() {
    when(repo.save(any(NotificationPref.class))).thenAnswer(i -> i.getArgument(0));

    NotificationPrefDto dto =
        service.create(
            pid, new NotificationPrefForm("slack", "https://hook", List.of("RUN_COMPLETED"), true));

    assertThat(dto.channel()).isEqualTo("SLACK");
    assertThat(dto.events()).containsExactly("RUN_COMPLETED");
    assertThat(dto.enabled()).isTrue();
    verify(repo).save(any(NotificationPref.class));
  }

  @Test
  void createRejectsBlankDestination() {
    assertThatThrownBy(
            () -> service.create(pid, new NotificationPrefForm("SLACK", "  ", List.of(), true)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void getScopedThrowsWhenMissing() {
    when(repo.findByIdAndProjectId(id, pid)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(pid, id)).isInstanceOf(ResponseStatusException.class);
  }
}
