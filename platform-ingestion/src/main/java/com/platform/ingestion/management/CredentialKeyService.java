package com.platform.ingestion.management;

import com.platform.core.domain.CredKeySetting;
import com.platform.core.repository.CredKeySettingRepository;
import com.platform.core.service.CredentialCipher;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages a passphrase-derived credential encryption key for blank/self-hosted platforms that don't
 * supply {@code PLATFORM_CRED_KEY} via the environment.
 *
 * <p>The AES-256 key is derived from an admin-chosen passphrase with PBKDF2-HMAC-SHA256 over a
 * random salt (direct derivation: the same passphrase + stored salt always reproduces the key). We
 * persist only the salt, the iteration count, and a verifier (a known constant encrypted with the
 * derived key) — never the key or passphrase. The verifier lets us confirm a re-entered passphrase
 * on unlock after a restart.
 *
 * <p><b>Environment always wins:</b> when {@code PLATFORM_CRED_KEY} is set, the key is already
 * active and init/unlock are rejected. Otherwise the platform boots locked and an admin must
 * initialize (first run) or unlock (after a restart) before credentials can be used. The key lives
 * only in the process memory of whichever service is unlocked.
 */
@Service
public class CredentialKeyService {

  private static final Logger log = LoggerFactory.getLogger(CredentialKeyService.class);

  static final int ITERATIONS = 600_000; // PBKDF2-HMAC-SHA256
  static final int KEY_BITS = 256;
  static final int SALT_BYTES = 16;
  static final int MIN_PASSPHRASE = 12;
  static final String VERIFIER_PLAINTEXT = "cred-key-verifier-v1";

  private final CredentialCipher cipher;
  private final CredKeySettingRepository repo;
  private final SecureRandom random = new SecureRandom();

  public CredentialKeyService(CredentialCipher cipher, CredKeySettingRepository repo) {
    this.cipher = cipher;
    this.repo = repo;
  }

  /**
   * @param envProvided key came from the environment (passphrase flow disabled)
   * @param initialized a passphrase has been set up (a settings row exists)
   * @param unlocked a usable key is currently loaded (credentials work)
   */
  public record KeyStatus(boolean envProvided, boolean initialized, boolean unlocked) {}

  @Transactional(readOnly = true)
  public KeyStatus status() {
    return new KeyStatus(
        cipher.isEnvProvided(),
        repo.findFirstByOrderByCreatedAtAsc().isPresent(),
        cipher.isConfigured());
  }

  /**
   * First-run setup: derive the key from a new passphrase, persist salt + verifier, and activate.
   */
  @Transactional
  public KeyStatus initialize(String passphrase) {
    if (cipher.isEnvProvided()) {
      throw error(
          HttpStatus.CONFLICT,
          "Key is provided via PLATFORM_CRED_KEY; passphrase setup is disabled");
    }
    if (repo.findFirstByOrderByCreatedAtAsc().isPresent()) {
      throw error(HttpStatus.CONFLICT, "Credential key is already initialized; use unlock instead");
    }
    validatePassphrase(passphrase);

    byte[] salt = new byte[SALT_BYTES];
    random.nextBytes(salt);
    SecretKeySpec key = derive(passphrase, salt, ITERATIONS);
    String verifier = cipher.encryptWithKey(key, VERIFIER_PLAINTEXT);

    repo.save(new CredKeySetting(Base64.getEncoder().encodeToString(salt), ITERATIONS, verifier));
    cipher.applyKey(key.getEncoded());
    log.info("[CredentialKey] initialized from passphrase ({} PBKDF2 iterations)", ITERATIONS);
    return status();
  }

  /** After a restart: re-derive the key from the passphrase, verify it, and activate. */
  @Transactional(readOnly = true)
  public KeyStatus unlock(String passphrase) {
    if (cipher.isConfigured()) {
      return status(); // already unlocked (env or a prior unlock)
    }
    CredKeySetting s =
        repo.findFirstByOrderByCreatedAtAsc()
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.CONFLICT,
                        "Credential key is not initialized yet; set a passphrase first"));
    if (passphrase == null || passphrase.isBlank()) {
      throw error(HttpStatus.BAD_REQUEST, "passphrase is required");
    }

    SecretKeySpec key =
        derive(passphrase, Base64.getDecoder().decode(s.getSalt()), s.getIterations());
    try {
      if (!VERIFIER_PLAINTEXT.equals(cipher.decryptWithKey(key, s.getVerifier()))) {
        throw error(HttpStatus.UNAUTHORIZED, "Incorrect passphrase");
      }
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      // GCM tag mismatch on the wrong key lands here
      throw error(HttpStatus.UNAUTHORIZED, "Incorrect passphrase");
    }
    cipher.applyKey(key.getEncoded());
    log.info("[CredentialKey] unlocked from passphrase");
    return status();
  }

  private void validatePassphrase(String passphrase) {
    if (passphrase == null || passphrase.strip().length() < MIN_PASSPHRASE) {
      throw error(
          HttpStatus.BAD_REQUEST, "Passphrase must be at least " + MIN_PASSPHRASE + " characters");
    }
  }

  static SecretKeySpec derive(String passphrase, byte[] salt, int iterations) {
    try {
      KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS);
      SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to derive credential key", e);
    }
  }

  private static ResponseStatusException error(HttpStatus status, String msg) {
    return new ResponseStatusException(status, msg);
  }
}
