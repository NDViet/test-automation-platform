package com.platform.common.integration;

import java.time.Instant;

/**
 * Health status of one integration connection, returned by {@link IntegrationAdapter#checkHealth}
 * and stored denormalized in {@code integration_configs.health}.
 */
public record IntegrationHealth(
    Status status, String message, Instant checkedAt, int consecutiveErrors) {
  public enum Status {
    HEALTHY,
    DEGRADED,
    DOWN,
    UNCONFIGURED
  }

  public static IntegrationHealth healthy() {
    return new IntegrationHealth(Status.HEALTHY, null, Instant.now(), 0);
  }

  public static IntegrationHealth down(String message, int consecutiveErrors) {
    return new IntegrationHealth(Status.DOWN, message, Instant.now(), consecutiveErrors);
  }

  public boolean isOperational() {
    return status == Status.HEALTHY || status == Status.DEGRADED;
  }
}
