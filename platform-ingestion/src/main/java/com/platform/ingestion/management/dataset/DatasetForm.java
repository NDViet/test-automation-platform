package com.platform.ingestion.management.dataset;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** Create/update payload for a dataset. */
public record DatasetForm(@NotBlank String name, String env, Map<String, String> rows) {}
