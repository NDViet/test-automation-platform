package com.platform.core.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
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

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public CredentialCipher(@Value("${platform.cred.key:}") String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      this.key = null;
      log.warn(
          "[CredentialCipher] platform.cred.key (PLATFORM_CRED_KEY) is not set. Encrypting or"
              + " decrypting integration credentials will fail until it is configured.");
    } else {
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
      this.key = new SecretKeySpec(raw, "AES");
    }
  }

  /** True when a usable key is configured. */
  public boolean isConfigured() {
    return key != null;
  }

  /** Encrypts UTF-8 plaintext, returning a {@code v1:}-prefixed token. */
  public String encrypt(String plaintext) {
    if (plaintext == null) return null;
    requireKey();
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

  /** Decrypts a token produced by {@link #encrypt(String)}. */
  public String decrypt(String token) {
    if (token == null) return null;
    requireKey();
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

  private void requireKey() {
    if (key == null) {
      throw new IllegalStateException(
          "No credential encryption key configured. Set PLATFORM_CRED_KEY "
              + "(base64 of 32 random bytes) before using integration credentials.");
    }
  }
}
