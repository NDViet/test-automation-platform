package com.platform.analytics.api.dto;

import com.platform.core.domain.FlakinessScore;

import java.time.Instant;
import java.util.UUID;

public record FlakinessDto(
        UUID id,
        String testId,
        UUID projectId,
        double score,
        String classification,
        int totalRuns,
        int failureCount,
        double failureRate,
        Instant lastFailedAt,
        Instant lastPassedAt,
        Instant computedAt
) {
    public static FlakinessDto from(FlakinessScore s) {
        return new FlakinessDto(
                s.getId(),
                s.getTestId(),
                s.getProjectId(),
                s.getScore().doubleValue(),
                s.getClassification().name(),
                s.getTotalRuns(),
                s.getFailureCount(),
                s.getFailureRate().doubleValue(),
                s.getLastFailedAt(),
                s.getLastPassedAt(),
                s.getComputedAt()
        );
    }
}
