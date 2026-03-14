package com.platform.ai.classification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Deserialised JSON response from Claude's failure classification prompt.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeAnalysisResult(
        String category,
        double confidence,
        String rootCause,
        String detailedAnalysis,
        String suggestedFix,
        @JsonProperty("isFlakyCandidate") boolean flakyCandidate,
        String affectedComponent
) {}
