package com.platform.ingestion.management.notification;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-project notification routes. Configuring delivery (channels/destinations) is project config,
 * so the whole controller requires MANAGE_PROJECT.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/notification-prefs")
@RequireCapability(value = Capability.MANAGE_PROJECT, scope = "projectId")
@Tag(name = "Notification Preferences")
public class NotificationPrefController {

  private final NotificationPrefService service;

  public NotificationPrefController(NotificationPrefService service) {
    this.service = service;
  }

  @GetMapping
  public List<NotificationPrefDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @GetMapping("/{id}")
  public NotificationPrefDto get(@PathVariable UUID projectId, @PathVariable UUID id) {
    return service.get(projectId, id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public NotificationPrefDto create(
      @PathVariable UUID projectId, @RequestBody NotificationPrefForm req) {
    return service.create(projectId, req);
  }

  @PutMapping("/{id}")
  public NotificationPrefDto update(
      @PathVariable UUID projectId, @PathVariable UUID id, @RequestBody NotificationPrefForm req) {
    return service.update(projectId, id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID projectId, @PathVariable UUID id) {
    service.delete(projectId, id);
  }
}
