package com.platform.core.service;

import com.platform.core.domain.IntegrationCredential;

import java.util.Map;

/**
 * The effective credential for a (project, integrationType), after deep-merging
 * ORG → TEAM → PROJECT scopes. Secrets are decrypted and held in memory only.
 *
 * @param integrationType  the integration type (matches {@code IntegrationType} enum name)
 * @param baseUrl          most-specific non-blank base URL across scopes
 * @param connectionParams merged non-secret params (PROJECT wins over TEAM wins over ORG)
 * @param secret           decrypted secret fields from the most-specific scope that defines them
 * @param secretScope      which scope supplied the secret (ORG/TEAM/PROJECT), or null if none
 */
public record ResolvedCredential(
        String integrationType,
        String baseUrl,
        Map<String, String> connectionParams,
        Map<String, String> secret,
        IntegrationCredential.Scope secretScope) {

    /** A merged connection parameter (non-secret), or null. */
    public String param(String key) {
        return connectionParams == null ? null : connectionParams.get(key);
    }

    /** A decrypted secret field (e.g. "pat", "clientSecret"), or null. */
    public String secret(String key) {
        return secret == null ? null : secret.get(key);
    }

    public boolean hasSecret() {
        return secret != null && !secret.isEmpty();
    }

    @Override
    public String toString() {
        return "ResolvedCredential[type=" + integrationType +
               ", scope=" + secretScope +
               ", hasSecret=" + hasSecret() + "]";
    }
}
