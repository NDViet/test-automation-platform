package com.platform.ingestion.management.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.NotificationPref;
import com.platform.core.repository.NotificationPrefRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** CRUD for per-project notification routes. Events are stored as a JSON array of event names. */
@Service
@Transactional
public class NotificationPrefService {

  private final NotificationPrefRepository repo;
  private final ObjectMapper om;

  public NotificationPrefService(NotificationPrefRepository repo, ObjectMapper om) {
    this.repo = repo;
    this.om = om;
  }

  @Transactional(readOnly = true)
  public List<NotificationPrefDto> list(UUID projectId) {
    return repo.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public NotificationPrefDto get(UUID projectId, UUID id) {
    return toDto(load(projectId, id));
  }

  public NotificationPrefDto create(UUID projectId, NotificationPrefForm req) {
    validate(req);
    return toDto(
        repo.save(
            new NotificationPref(
                projectId,
                req.channel().trim().toUpperCase(),
                req.destination().trim(),
                writeEvents(req.events()),
                req.enabled())));
  }

  public NotificationPrefDto update(UUID projectId, UUID id, NotificationPrefForm req) {
    validate(req);
    NotificationPref p = load(projectId, id);
    p.update(
        req.channel().trim().toUpperCase(),
        req.destination().trim(),
        writeEvents(req.events()),
        req.enabled());
    return toDto(repo.save(p));
  }

  public void delete(UUID projectId, UUID id) {
    repo.delete(load(projectId, id));
  }

  private NotificationPref load(UUID projectId, UUID id) {
    return repo.findByIdAndProjectId(id, projectId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Notification pref not found: " + id));
  }

  private static void validate(NotificationPrefForm req) {
    if (req.channel() == null || req.channel().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A channel is required");
    }
    if (req.destination() == null || req.destination().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A destination is required");
    }
  }

  NotificationPrefDto toDto(NotificationPref p) {
    return new NotificationPrefDto(
        p.getId() != null ? p.getId().toString() : null,
        p.getChannel(),
        p.getDestination(),
        readEvents(p.getEvents()),
        p.isEnabled(),
        p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
  }

  private String writeEvents(List<String> events) {
    try {
      return om.writeValueAsString(events == null ? List.of() : events);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid events");
    }
  }

  @SuppressWarnings("unchecked")
  List<String> readEvents(String json) {
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
