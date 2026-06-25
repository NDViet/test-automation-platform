package com.platform.ingestion.management.tcm;

import jakarta.validation.constraints.NotBlank;

public record UpdateExecutionRequest(
    @NotBlank String status, String actualResult, String notes, String executedBy) {}
