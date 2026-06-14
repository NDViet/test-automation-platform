package com.platform.agent.node.tools;

import com.platform.common.integration.IntegrationType;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a decrypted access token for an integration from the Org→Team→Project
 * credential cascade ({@link CredentialResolver}). Used by agent nodes that call
 * external systems (e.g. GitHub PR creation) so they use the centralized,
 * encrypted, per-project credentials rather than plaintext config params.
 */
@Component
public class IntegrationTokenResolver {

    private final CredentialResolver credentialResolver;

    public IntegrationTokenResolver(CredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    /** Resolves the {@code pat}/{@code token} secret for a project + integration type. */
    public Optional<String> resolveToken(UUID projectId, IntegrationType type) {
        return credentialResolver.resolve(projectId, type.name())
                .map(this::pickToken)
                .filter(t -> t != null && !t.isBlank());
    }

    private String pickToken(ResolvedCredential cred) {
        String pat = cred.secret("pat");
        return (pat != null && !pat.isBlank()) ? pat : cred.secret("token");
    }
}
