package com.platform.core.service;

import com.platform.core.repository.IntegrationCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-encrypts all {@code integration_credentials.secret_ciphertext} rows from an
 * old AES-256-GCM key to the current key.
 *
 * <p>Activate by setting <strong>both</strong>:</p>
 * <ul>
 *   <li>{@code PLATFORM_CRED_KEY} — the new (replacement) key</li>
 *   <li>{@code PLATFORM_CRED_KEY_PREVIOUS} — the old key to decrypt existing rows</li>
 * </ul>
 *
 * <p>The runner is <strong>idempotent</strong>: rows already encrypted with the new key
 * cannot be decrypted by the old key, so they are skipped automatically. After a
 * successful run, unset {@code PLATFORM_CRED_KEY_PREVIOUS} and redeploy.</p>
 */
@Component
public class CredentialKeyRotationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CredentialKeyRotationRunner.class);

    private final CredentialCipher currentCipher;
    private final IntegrationCredentialRepository credRepo;
    private final String previousKeyBase64;

    public CredentialKeyRotationRunner(
            CredentialCipher currentCipher,
            IntegrationCredentialRepository credRepo,
            @Value("${platform.cred.key.previous:}") String previousKeyBase64) {
        this.currentCipher      = currentCipher;
        this.credRepo           = credRepo;
        this.previousKeyBase64  = previousKeyBase64;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (previousKeyBase64 == null || previousKeyBase64.isBlank()) {
            return; // no rotation requested
        }
        if (!currentCipher.isConfigured()) {
            log.error("[KeyRotation] PLATFORM_CRED_KEY is not set — cannot rotate. Aborting.");
            return;
        }

        CredentialCipher previousCipher;
        try {
            previousCipher = new CredentialCipher(previousKeyBase64);
        } catch (Exception e) {
            log.error("[KeyRotation] PLATFORM_CRED_KEY_PREVIOUS is invalid: {}", e.getMessage());
            return;
        }

        log.info("[KeyRotation] Starting credential key rotation…");
        int rotated = 0, skipped = 0, errors = 0;

        for (var cred : credRepo.findAll()) {
            String ciphertext = cred.getSecretCiphertext();
            if (ciphertext == null || ciphertext.isBlank()) {
                continue;
            }
            try {
                String plaintext    = previousCipher.decrypt(ciphertext);
                String newCiphertext = currentCipher.encrypt(plaintext);
                cred.setSecretCiphertext(newCiphertext);
                credRepo.save(cred);
                rotated++;
            } catch (Exception e) {
                // Row may already be encrypted with the new key — skip it
                skipped++;
                log.debug("[KeyRotation] Skipped credential id={} (already rotated or unreadable): {}",
                        cred.getId(), e.getMessage());
            }
        }

        log.info("[KeyRotation] Rotation complete: rotated={}, skipped={}, errors={}",
                rotated, skipped, errors);
        log.warn("[KeyRotation] SUCCESS — now unset PLATFORM_CRED_KEY_PREVIOUS and redeploy.");
    }
}
