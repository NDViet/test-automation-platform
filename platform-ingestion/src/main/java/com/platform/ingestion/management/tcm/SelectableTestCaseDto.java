package com.platform.ingestion.management.tcm;

import java.util.List;

/** Lightweight test-case row for the run-creation picker (scope-filtered, searchable). */
public record SelectableTestCaseDto(
        String id,
        String externalId,
        String title,
        String priority,
        String status,
        List<String> requirementExternalIds   // linked requirement ADO ids (for display/search context)
) {}
