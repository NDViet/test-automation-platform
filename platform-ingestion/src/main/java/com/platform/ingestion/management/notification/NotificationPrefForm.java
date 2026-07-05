package com.platform.ingestion.management.notification;

import java.util.List;

/** Create/update payload for a notification route. */
public record NotificationPrefForm(
    String channel, String destination, List<String> events, boolean enabled) {}
