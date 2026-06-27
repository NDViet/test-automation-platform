package com.platform.core.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Symmetric encryption for integration secrets (PATs, API tokens, client secrets).
 *
 * <p>Uses AES-256-GCM. The master key is supplied via the {@code platform.cred.key} property (env
 * var {@code PLATFORM_CRED_KEY}) as a base64-encoded 32-byte key. Ciphertext is encoded as {@code
 * v1:base64(iv(12) || ciphertext || tag)} so the scheme can evolve without ambiguity.
 *
 * <p>If no key is configured the cipher fails fast on first use — secrets must never be written or
 * read as plaintext once this layer exists.
 */
@Component
public class CredentialCipher {

  private static final Logger log = LoggerFactory.getLogger(CredentialCipher.class);
  private static final String VERSION_PREFIX = "v1:";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12; // 96-bit nonce, recommended for GCM
  private static final int TAG_LENGTH = 128; // bits
  private static final int KEY_BYTES = 32; // AES-256

  /**
   * The active key. May be set at startup from the env var, or applied at runtime by {@link
   * #applyKey(byte[])} after a passphrase-based unlock. Volatile via {@link AtomicReference} since
   * unlock can happen on a request thread while others read it.
   */
  private final AtomicReference<SecretKeySpec> activeKey = new AtomicReference<>();

  /**
   * True when the key came from the environment (env always wins; passphrase unlock is disabled).
   */
  private final boolean envProvided;

  private final SecureRandom random = new SecureRandom();

  public CredentialCipher(@Value("${platform.cred.key:}") String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      this.envProvided = false;
      log.warn(
          "[CredentialCipher] platform.cred.key (PLATFORM_CRED_KEY) is not set. Credentials are"
              + " locked until a passphrase is supplied (POST /api/v1/security/cred-key/init or"
              + " /unlock) or the env var is configured.");
    } else {
      this.activeKey.set(new SecretKeySpec(parseBase64Key(base64Key), "AES"));
      this.envProvided = true;
    }
  }

  /** True when a usable key is currently loaded (from env or a passphrase unlock). */
  public boolean isConfigured() {
    return activeKey.get() != null;
  }

  /** True when the key was provided via the environment, so passphrase init/unlock is disabled. */
  public boolean isEnvProvided() {
    return envProvided;
  }

  /**
   * Loads a runtime key (AES-256, 32 raw bytes) derived from a passphrase. Ignored when the env key
   * is set — the environment always wins.
   */
  public void applyKey(byte[] raw) {
    if (envProvided) return;
    if (raw == null || raw.length != KEY_BYTES) {
      throw new IllegalArgumentException("credential key must be exactly " + KEY_BYTES + " bytes");
    }
    activeKey.set(new SecretKeySpec(raw, "AES"));
  }

  /** Encrypts UTF-8 plaintext with the active key, returning a {@code v1:}-prefixed token. */
  public String encrypt(String plaintext) {
    return encryptWithKey(requireKey(), plaintext);
  }

  /** Decrypts a token produced by {@link #encrypt(String)} with the active key. */
  public String decrypt(String token) {
    return decryptWithKey(requireKey(), token);
  }

  /**
   * Encrypts with a caller-supplied key (used to build a passphrase verifier before the key is made
   * active). GCM authentication means decryption with the wrong key fails — that is the verifier.
   */
  public String encryptWithKey(SecretKeySpec key, String plaintext) {
    if (plaintext == null) return null;
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
      byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] out = new byte[iv.length + ct.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ct, 0, out, iv.length, ct.length);
      return VERSION_PREFIX + Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt credential", e);
    }
  }

  /** Decrypts a {@code v1:} token with a caller-supplied key. */
  public String decryptWithKey(SecretKeySpec key, String token) {
    if (token == null) return null;
    if (!token.startsWith(VERSION_PREFIX)) {
      throw new IllegalStateException(
          "Unrecognized credential ciphertext format (missing version prefix)");
    }
    try {
      byte[] all = Base64.getDecoder().decode(token.substring(VERSION_PREFIX.length()));
      byte[] iv = new byte[IV_LENGTH];
      System.arraycopy(all, 0, iv, 0, IV_LENGTH);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
      byte[] pt = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt credential", e);
    }
  }

  /** Whether a stored value is already an encrypted token (for idempotent backfill). */
  public boolean isEncrypted(String value) {
    return value != null && value.startsWith(VERSION_PREFIX);
  }

  private static byte[] parseBase64Key(String base64Key) {
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "platform.cred.key must be base64-encoded; failed to decode it", e);
    }
    if (raw.length != KEY_BYTES) {
      throw new IllegalStateException(
          "platform.cred.key must decode to exactly "
              + KEY_BYTES
              + " bytes (AES-256); got "
              + raw.length
              + " bytes");
    }
    return raw;
  }

  private SecretKeySpec requireKey() {
    SecretKeySpec k = activeKey.get();
    if (k == null) {
      throw new IllegalStateException(
          "No credential encryption key configured. Set PLATFORM_CRED_KEY "
              + "(base64 of 32 random bytes), or unlock with a passphrase, before using "
              + "integration credentials.");
    }
    return k;
  }
}
