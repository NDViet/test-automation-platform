package com.platform.ingestion.api;

public record IngestResponse(
        String runId,
        String status,
        int testCount,
        String processingUrl
) {}
