package com.platform.common.agent;

import java.math.BigDecimal;

/**
 * Token consumption for one LLM call or an aggregated session.
 * Cache fields align with Anthropic's usage response schema.
 */
public record TokenUsage(
        int inputFresh,           // tokens billed at full input price
        int inputCacheWrite,      // tokens written to prompt cache (1.25× input price)
        int inputCacheRead,       // tokens read from prompt cache (0.10× input price)
        int outputTokens,
        BigDecimal effectiveCostCents
) {
    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0, 0, BigDecimal.ZERO);
    }

    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(
                this.inputFresh + other.inputFresh,
                this.inputCacheWrite + other.inputCacheWrite,
                this.inputCacheRead + other.inputCacheRead,
                this.outputTokens + other.outputTokens,
                this.effectiveCostCents.add(other.effectiveCostCents));
    }

    public int totalInputTokens() { return inputFresh + inputCacheWrite + inputCacheRead; }
}
