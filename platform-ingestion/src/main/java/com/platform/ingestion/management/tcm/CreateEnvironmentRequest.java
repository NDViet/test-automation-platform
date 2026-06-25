package com.platform.ingestion.management.tcm;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateEnvironmentRequest(
    @NotBlank String name,
    String description,
    Map<String, String> properties // optional name=value environment properties
    ) {}
