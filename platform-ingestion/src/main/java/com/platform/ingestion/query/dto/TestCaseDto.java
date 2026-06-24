package com.platform.ingestion.query.dto;
import com.platform.common.enums.TestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record TestCaseDto(
        UUID id, String testId, String displayName, String className,
        String methodName, List<String> tags, TestStatus status,
        Long durationMs, String failureMessage, String stackTrace,
        int retryCount, Instant createdAt,
        boolean hasTrace, boolean hasScreenshot, boolean hasVideo,
        String specFile, String browser) {

    public static TestCaseDto from(com.platform.core.domain.TestCaseResult r) {
        return new TestCaseDto(
                r.getId(), r.getTestId(), r.getDisplayName(),
                r.getClassName(), r.getMethodName(), r.getTags(),
                r.getStatus(), r.getDurationMs(),
                r.getFailureMessage(), r.getStackTrace(),
                r.getRetryCount(), r.getCreatedAt(),
                r.getTraceStorePath() != null,
                r.isHasScreenshot(), r.isHasVideo(),
                r.getSpecFile(), r.getBrowser());
    }
}
