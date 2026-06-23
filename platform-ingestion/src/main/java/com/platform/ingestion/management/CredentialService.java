package com.platform.ingestion.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.domain.IntegrationCredential.Scope;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.service.CredentialCipher;
import com.platform.ingestion.management.dto.CredentialDto;
import com.platform.ingestion.management.dto.SaveCredentialRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD + test-connection for scoped, encrypted {@link IntegrationCredential}s
 * (the centralized "Admin PAT" + per-scope overrides). Secrets are encrypted at
 * rest via {@link CredentialCipher} and never returned to clients.
 */
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private final IntegrationCredentialRepository repo;
    private final CredentialCipher cipher;
    private final CredentialHealthChecker healthChecker;
    private final ObjectMapper objectMapper;

    public CredentialService(IntegrationCredentialRepository repo,
                             CredentialCipher cipher,
                             CredentialHealthChecker healthChecker,
                             ObjectMapper objectMapper) {
        this.repo          = repo;
        this.cipher        = cipher;
        this.healthChecker = healthChecker;
        this.objectMapper  = objectMapper;
    }

    /**
     * Lists credentials at a scope. ADO-first: every scope is id-keyed —
     * ORG=organization id, PROJECT=project id, TEAM=team id — so scopeId is required.
     */
    @Transactional(readOnly = true)
    public List<CredentialDto> list(String scope, UUID scopeId) {
        Scope s = parseScope(scope);
        List<IntegrationCredential> rows =
                repo.findByScopeAndScopeId(s.name(), required(scopeId, "scopeId"));
        return rows.stream().map(CredentialDto::from).toList();
    }

    @Transactional
    public CredentialDto save(SaveCredentialRequest req, String actor) {
        Scope scope = parseScope(req.scope());
        // ADO-first: ORG is keyed by the organization id (matches CredentialResolver),
        // PROJECT by project id, TEAM by team id — all require a scopeId.
        UUID scopeId = required(req.scopeId(), "scopeId");

        IntegrationCredential existing =
                repo.findByScopeAndScopeIdAndIntegrationType(scope.name(), scopeId, req.integrationType()).orElse(null);

        String secretCiphertext = encryptIfPresent(req.secret(),
                existing == null ? null : existing.getSecretCiphertext());

        IntegrationCredential saved;
        if (existing == null) {
            IntegrationCredential c = new IntegrationCredential(
                    scope, scopeId, req.integrationType(), req.displayName(),
                    req.baseUrl(), nullToEmpty(req.connectionParams()), secretCiphertext);
            c.setCreatedBy(actor);
            if (req.enabled() != null) c.setEnabled(req.enabled());
            if (req.syncIntervalMinutes() != null) c.setSyncIntervalMinutes(req.syncIntervalMinutes());
            saved = repo.save(c);
            log.info("[Credentials] Created {} {} credential by {}", scope, req.integrationType(), actor);
        } else {
            existing.setDisplayName(req.displayName());
            existing.setBaseUrl(req.baseUrl());
            if (req.connectionParams() != null) existing.setConnectionParams(req.connectionParams());
            existing.setSecretCiphertext(secretCiphertext);
            if (req.enabled() != null) existing.setEnabled(req.enabled());
            if (req.syncIntervalMinutes() != null) existing.setSyncIntervalMinutes(req.syncIntervalMinutes());
            saved = repo.save(existing);
            log.info("[Credentials] Updated {} {} credential by {}", scope, req.integrationType(), actor);
        }
        return CredentialDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repo.existsById(id)) throw notFound(id);
        repo.deleteById(id);
    }

    @Transactional
    public CredentialDto updateSyncInterval(UUID id, int minutes) {
        IntegrationCredential c = repo.findById(id).orElseThrow(() -> notFound(id));
        c.setSyncIntervalMinutes(Math.max(0, minutes));
        return CredentialDto.from(repo.save(c));
    }

    /** Decrypts the secret, merges with non-secret params, and probes the remote system. */
    @Transactional(readOnly = true)
    public CredentialHealthChecker.Result testConnection(UUID id) {
        IntegrationCredential c = repo.findById(id).orElseThrow(() -> notFound(id));
        Map<String, String> params = new HashMap<>(nullToEmpty(c.getConnectionParams()));
        if (c.getSecretCiphertext() != null && !c.getSecretCiphertext().isBlank()) {
            params.putAll(decryptSecret(c.getSecretCiphertext()));
        }
        return healthChecker.check(c.getIntegrationType(), c.getBaseUrl(), params);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String encryptIfPresent(Map<String, String> secret, String existingCiphertext) {
        if (secret == null || secret.isEmpty()) return existingCiphertext; // keep existing on update
        if (!cipher.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Credential encryption key (PLATFORM_CRED_KEY) is not configured");
        }
        try {
            return cipher.encrypt(objectMapper.writeValueAsString(secret));
        } catch (Exception e) {
            throw badRequest("Failed to encrypt secret: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> decryptSecret(String ciphertext) {
        try {
            return objectMapper.readValue(cipher.decrypt(ciphertext), Map.class);
        } catch (Exception e) {
            log.error("[Credentials] Failed to decrypt secret: {}", e.getMessage());
            return Map.of();
        }
    }

    private Scope parseScope(String scope) {
        try {
            return Scope.valueOf(scope.toUpperCase());
        } catch (Exception e) {
            throw badRequest("Invalid scope: " + scope + " (expected ORG, TEAM or PROJECT)");
        }
    }

    private static Map<String, String> nullToEmpty(Map<String, String> m) {
        return m == null ? new HashMap<>() : m;
    }

    private static <T> T required(T value, String name) {
        if (value == null) throw badRequest(name + " is required (ORG=organization id, PROJECT=project id, TEAM=team id)");
        return value;
    }

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found: " + id);
    }
}
