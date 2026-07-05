package com.platform.ingestion.management.notification;

import java.util.List;

/** A notification route as returned to the portal. */
public record NotificationPrefDto(
    String id,
    String channel,
    String destination,
    List<String> events,
    boolean enabled,
    String createdAt) {}
