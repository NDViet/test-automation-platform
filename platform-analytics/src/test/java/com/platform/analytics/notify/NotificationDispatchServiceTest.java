package com.platform.analytics.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.NotificationPref;
import com.platform.core.repository.NotificationPrefRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

  @Mock NotificationPrefRepository repo;
  @Mock NotificationSender sender;

  NotificationDispatchService service;

  private final UUID pid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new NotificationDispatchService(repo, new ObjectMapper(), sender);
  }

  private NotificationPref pref(String channel, String dest, String eventsJson) {
    return new NotificationPref(pid, channel, dest, eventsJson, true);
  }

  @Test
  void dispatchesToRoutesSubscribedToTheEvent() {
    when(repo.findByProjectIdAndEnabledTrue(pid))
        .thenReturn(
            List.of(
                pref("SLACK", "https://hook", "[\"RUN_COMPLETED\",\"RUN_HAS_FAILURES\"]"),
                pref("EMAIL", "qa@x.io", "[\"GATE_RESULT\"]")));

    int sent = service.dispatch(pid, "RUN_COMPLETED", "Run done", "body");

    assertThat(sent).isEqualTo(1);
    verify(sender).send(eq("SLACK"), eq("https://hook"), eq("Run done"), eq("body"));
    verify(sender, never()).send(eq("EMAIL"), any(), any(), any());
  }

  @Test
  void noMatchingRouteSendsNothing() {
    when(repo.findByProjectIdAndEnabledTrue(pid))
        .thenReturn(List.of(pref("SLACK", "https://hook", "[\"GATE_RESULT\"]")));

    int sent = service.dispatch(pid, "RUN_COMPLETED", "s", "b");

    assertThat(sent).isZero();
    verify(sender, never()).send(any(), any(), any(), any());
  }
}
