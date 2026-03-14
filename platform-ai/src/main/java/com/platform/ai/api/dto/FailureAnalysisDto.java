package com.platform.ai.api.dto;

import com.platform.core.domain.FailureAnalysis;

import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO for a single failure analysis result.
 */
public record FailureAnalysisDto(
        UUID id,
        String testId,
        UUID projectId,
        UUID testCaseResultId,
        String category,
        double confidence,
        String rootCause,
        String detailedAnalysis,
        String suggestedFix,
        boolean flakyCandidate,
        String affectedComponent,
        String modelVersion,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        Instant analysedAt
) {
    public static FailureAnalysisDto from(FailureAnalysis a) {
        return new FailureAnalysisDto(
                a.getId(),
                a.getTestId(),
                a.getProjectId(),
                a.getTestCaseResultId(),
                a.getCategory(),
                a.getConfidence(),
                a.getRootCause(),
                a.getDetailedAnalysis(),
                a.getSuggestedFix(),
                a.isFlakyCandidate(),
                a.getAffectedComponent(),
                a.getModelVersion(),
                a.getInputTokens(),
                a.getOutputTokens(),
                a.getTotalTokens(),
                a.getAnalysedAt());
    }
}
