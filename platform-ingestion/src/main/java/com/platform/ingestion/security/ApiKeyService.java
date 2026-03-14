package com.platform.ingestion.security;

import com.platform.core.domain.ApiKey;
import com.platform.core.domain.AuditEvent;
import com.platform.core.repository.ApiKeyRepository;
import com.platform.core.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Manages API key lifecycle: generation, storage, usage tracking, and revocation.
 *
 * <p>Raw keys are <strong>never</strong> stored. Only the SHA-256 hash is persisted.
 * The caller must record the raw key at creation time — it cannot be retrieved later.</p>
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final int KEY_BYTES = 32; // 256-bit raw key → base64url encoded

    private final ApiKeyRepository keyRepo;
    private final AuditEventRepository auditRepo;

    public ApiKeyService(ApiKeyRepository keyRepo, AuditEventRepository auditRepo) {
        this.keyRepo   = keyRepo;
        this.auditRepo = auditRepo;
    }

    /**
     * Generates a new API key for the given team.
     *
     * @param name     human-readable label (e.g. "payments-team CI")
     * @param teamId   team that owns the key
     * @param ttlDays  key validity in days; {@code null} for no expiry
     * @return the raw key (shown once — caller must record it)
     */
    @Transactional
    public ApiKeyCreationResult create(String name, UUID teamId, Integer ttlDays) {
        byte[] bytes = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(bytes);
        String rawKey = "plat_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        String hash   = sha256hex(rawKey);
        String prefix = rawKey.substring(0, Math.min(12, rawKey.length()));

        Instant expiresAt = ttlDays != null
                ? Instant.now().plus(ttlDays, ChronoUnit.DAYS)
                : null;

        ApiKey key = ApiKey.builder()
                .name(name)
                .keyHash(hash)
                .keyPrefix(prefix)
                .teamId(teamId)
                .expiresAt(expiresAt)
                .build();

        ApiKey saved = keyRepo.save(key);

        auditRepo.save(AuditEvent.builder()
                .eventType("API_KEY_CREATED")
                .actorKeyId(saved.getId())
                .actorKeyPrefix(prefix)
                .teamId(teamId)
                .resourceType("API_KEY")
                .resourceId(saved.getId() != null ? saved.getId().toString() : null)
                .details("{\"name\":\"" + name + "\",\"expiresAt\":\"" + expiresAt + "\"}")
                .outcome("SUCCESS")
                .build());

        log.info("[Security] API key created prefix={} team={}", prefix, teamId);
        return new ApiKeyCreationResult(saved.getId(), prefix, rawKey, expiresAt);
    }

    /**
     * Revokes an API key so it can no longer authenticate requests.
     */
    @Transactional
    public void revoke(UUID keyId, UUID actorKeyId) {
        ApiKey key = keyRepo.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + keyId));
        key.revoke();
        keyRepo.save(key);

        auditRepo.save(AuditEvent.builder()
                .eventType("API_KEY_REVOKED")
                .actorKeyId(actorKeyId)
                .teamId(key.getTeamId())
                .resourceType("API_KEY")
                .resourceId(keyId.toString())
                .outcome("SUCCESS")
                .build());

        log.info("[Security] API key revoked id={}", keyId);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> listForTeam(UUID teamId) {
        return keyRepo.findByTeamIdAndRevokedFalseOrderByCreatedAtDesc(teamId);
    }

    /**
     * Records usage timestamp. Called from the auth filter — runs in a short
     * background-friendly transaction.
     */
    @Transactional
    public void recordUsage(ApiKey key) {
        key.recordUsage();
        keyRepo.save(key);
    }

    private String sha256hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** Returned once at key creation — the raw key is never stored. */
    public record ApiKeyCreationResult(
            UUID id,
            String prefix,
            String rawKey,
            Instant expiresAt) {}
}
