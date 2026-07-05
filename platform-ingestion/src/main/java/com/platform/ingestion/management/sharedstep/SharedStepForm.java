package com.platform.ingestion.management.sharedstep;

import jakarta.validation.constraints.NotBlank;

/** Create/update payload for a shared step. */
public record SharedStepForm(@NotBlank String action, String expectedResult, String notes) {}
