package com.platform.core.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private static String randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void encryptThenDecrypt_roundTrips() {
        CredentialCipher cipher = new CredentialCipher(randomKey());
        String plaintext = "{\"pat\":\"ghp_abc123\",\"email\":\"a@b.com\"}";

        String token = cipher.encrypt(plaintext);

        assertThat(token).startsWith("v1:");
        assertThat(token).doesNotContain("ghp_abc123");
        assertThat(cipher.isEncrypted(token)).isTrue();
        assertThat(cipher.decrypt(token)).isEqualTo(plaintext);
    }

    @Test
    void encryption_isNonDeterministic() {
        CredentialCipher cipher = new CredentialCipher(randomKey());
        String a = cipher.encrypt("same");
        String b = cipher.encrypt("same");
        assertThat(a).isNotEqualTo(b);            // random IV per call
        assertThat(cipher.decrypt(a)).isEqualTo(cipher.decrypt(b));
    }

    @Test
    void wrongKey_cannotDecrypt() {
        CredentialCipher enc = new CredentialCipher(randomKey());
        CredentialCipher other = new CredentialCipher(randomKey());
        String token = enc.encrypt("secret");
        assertThatThrownBy(() -> other.decrypt(token))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingKey_failsFast() {
        CredentialCipher cipher = new CredentialCipher("");
        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_CRED_KEY");
    }

    @Test
    void invalidKeyLength_rejected() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new CredentialCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void nullInputs_passThrough() {
        CredentialCipher cipher = new CredentialCipher(randomKey());
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }
}
