package com.platform.ai.client;

import com.platform.ai.classification.ClaudeAnalysisResult;

/**
 * Wraps an AI classification result with the token counts reported by the provider.
 * Token counts are 0 when the provider does not return usage info (e.g. error path).
 */
public record AiAnalysisResponse(
        ClaudeAnalysisResult result,
        int inputTokens,
        int outputTokens
) {
    public int totalTokens() { return inputTokens + outputTokens; }

    /** Convenience factory for error / unavailable paths. */
    public static AiAnalysisResponse ofResult(ClaudeAnalysisResult result) {
        return new AiAnalysisResponse(result, 0, 0);
    }
}
