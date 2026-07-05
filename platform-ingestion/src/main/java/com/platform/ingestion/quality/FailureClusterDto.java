package com.platform.ingestion.quality;

import com.platform.core.domain.FailureCluster;
import java.time.Instant;
import java.util.UUID;

/** Read model for a failure cluster returned to the portal. */
public record FailureClusterDto(
    UUID id,
    String source,
    String testRef,
    String title,
    String sampleMessage,
    String status,
    String defectExternalId,
    String defectUrl,
    String defectTitle,
    String defectState,
    int occurrences,
    Instant firstSeenAt,
    Instant lastSeenAt) {

  public static FailureClusterDto from(FailureCluster c) {
    return new FailureClusterDto(
        c.getId(),
        c.getSource(),
        c.getTestRef(),
        c.getTitle(),
        c.getSampleMessage(),
        c.getStatus(),
        c.getDefectExternalId(),
        c.getDefectUrl(),
        c.getDefectTitle(),
        c.getDefectState(),
        c.getOccurrences(),
        c.getFirstSeenAt(),
        c.getLastSeenAt());
  }
}
