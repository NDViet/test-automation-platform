package com.platform.agent.node;

/**
 * Compresses a raw tool result to ≤200 tokens using a cheap model.
 * Implementations should be fast and fault-tolerant — any error falls back to truncation.
 */
public interface StepSummarizer {

    /**
     * Returns a compressed summary of the tool result.
     * Falls back to the first 500 characters of rawResult on error.
     */
    String summarize(String toolName, String rawResult);
}
