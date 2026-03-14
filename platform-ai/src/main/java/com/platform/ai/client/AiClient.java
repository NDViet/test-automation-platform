package com.platform.ai.client;

/**
 * Provider-agnostic interface for AI-powered failure classification.
 *
 * <p>Implementations are selected at startup via the {@code ai.provider} property:
 * <ul>
 *   <li>{@code ai.provider=claude} (default) — Anthropic Claude via the official Java SDK</li>
 *   <li>{@code ai.provider=openai} — OpenAI GPT via the Chat Completions REST API</li>
 * </ul>
 */
public interface AiClient {

    /**
     * Classifies a test failure using the configured AI provider.
     *
     * @param systemPrompt instructions that establish the model's role and output format
     * @param userPrompt   the test failure details (message, stack trace, history)
     * @return response containing the parsed classification result and token usage counts;
     *         never {@code null} — returns an UNKNOWN result with zero tokens on error
     */
    AiAnalysisResponse analyse(String systemPrompt, String userPrompt);

    /**
     * Returns a human-readable identifier for the active provider and model,
     * e.g. {@code "claude-opus-4-6"} or {@code "openai/gpt-4o"}.
     * Stored in {@code failure_analyses.model_version}.
     */
    String providerName();
}
