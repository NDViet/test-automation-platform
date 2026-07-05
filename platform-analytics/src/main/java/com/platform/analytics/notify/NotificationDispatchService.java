package com.platform.analytics.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.NotificationPref;
import com.platform.core.repository.NotificationPrefRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fans a quality event out to a project's enabled notification routes whose subscribed events
 * include it. Pure selection logic; the actual send (Slack/email/webhook) is delegated to {@link
 * NotificationSender}, so this is unit-testable without touching a network.
 */
@Service
public class NotificationDispatchService {

  private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

  private final NotificationPrefRepository repo;
  private final ObjectMapper om;
  private final NotificationSender sender;

  public NotificationDispatchService(
      NotificationPrefRepository repo, ObjectMapper om, NotificationSender sender) {
    this.repo = repo;
    this.om = om;
    this.sender = sender;
  }

  /** Dispatches {@code event} to every enabled route in the project that subscribes to it. */
  @Transactional(readOnly = true)
  public int dispatch(UUID projectId, String event, String subject, String body) {
    int sent = 0;
    for (NotificationPref pref : repo.findByProjectIdAndEnabledTrue(projectId)) {
      if (parseEvents(pref.getEvents()).contains(event)) {
        try {
          sender.send(pref.getChannel(), pref.getDestination(), subject, body);
          sent++;
        } catch (Exception e) {
          log.warn("[Notify] send failed ({} → {}): {}", pref.getChannel(), event, e.getMessage());
        }
      }
    }
    return sent;
  }

  @SuppressWarnings("unchecked")
  private List<String> parseEvents(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return om.readValue(json, List.class);
    } catch (Exception e) {
      return List.of();
    }
  }
}
