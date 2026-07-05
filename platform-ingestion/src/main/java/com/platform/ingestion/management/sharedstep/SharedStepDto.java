package com.platform.ingestion.management.sharedstep;

import com.platform.core.domain.SharedStep;

/** A reusable shared step as returned to the portal. */
public record SharedStepDto(
    String id, String action, String expectedResult, String notes, String createdAt) {

  public static SharedStepDto from(SharedStep s) {
    return new SharedStepDto(
        s.getId() != null ? s.getId().toString() : null,
        s.getAction(),
        s.getExpectedResult(),
        s.getNotes(),
        s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
  }
}
