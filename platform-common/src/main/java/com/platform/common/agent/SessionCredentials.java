package com.platform.common.agent;

import com.platform.common.integration.IntegrationType;

import java.time.Instant;
import java.util.Map;

/**
 * Scoped, short-lived credentials passed from Hub to Node.
 * Never persisted — assembled per session from the secrets store.
 * Keys are IntegrationType names (e.g. "GITHUB", "JIRA_CLOUD").
 */
public record SessionCredentials(
        Map<String, String> tokens,     // IntegrationType.name() → bearer/PAT token
        Instant expiresAt
) {
    public SessionCredentials {
        tokens = tokens == null ? Map.of() : Map.copyOf(tokens);
    }

    public String token(IntegrationType type) {
        return tokens.get(type.name());
    }

    public boolean hasToken(IntegrationType type) {
        return tokens.containsKey(type.name());
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    @Override
    public String toString() {
        return "SessionCredentials[count=" + tokens.size() +
               ", expired=" + isExpired() + "]";
    }
}
