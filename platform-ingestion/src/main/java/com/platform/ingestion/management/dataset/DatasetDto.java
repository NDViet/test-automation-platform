package com.platform.ingestion.management.dataset;

import java.util.Map;

/** A test-data dataset as returned to the portal. */
public record DatasetDto(
    String id, String name, String env, Map<String, String> rows, String createdAt) {}
