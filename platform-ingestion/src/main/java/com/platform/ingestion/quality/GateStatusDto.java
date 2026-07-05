package com.platform.ingestion.quality;

import com.platform.core.domain.SotRelease;
import java.time.Instant;

/** A release's persisted gate verdict + override trail. */
public record GateStatusDto(
    String status, Instant evaluatedAt, String overrideReason, String overriddenBy) {

  public static GateStatusDto from(SotRelease r) {
    return new GateStatusDto(
        r.getGateStatus(),
        r.getGateEvaluatedAt(),
        r.getGateOverrideReason(),
        r.getGateOverriddenBy());
  }
}
