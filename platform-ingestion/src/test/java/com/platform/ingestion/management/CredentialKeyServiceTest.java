package com.platform.ingestion.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.platform.core.domain.CredKeySetting;
import com.platform.core.repository.CredKeySettingRepository;
import com.platform.core.service.CredentialCipher;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * Uses a real {@link CredentialCipher} (no env key) so the PBKDF2 derivation + GCM verifier
 * round-trip is exercised end-to-end. The repo is a mock backed by a single captured row, so an
 * init in one service instance is visible to a freshly-constructed (post-restart) instance.
 */
@ExtendWith(MockitoExtension.class)
class CredentialKeyServiceTest {

  @Mock CredKeySettingRepository repo;

  CredentialCipher cipher;
  CredentialKeyService service;
  CredKeySetting saved; // shared "persisted" row

  static final String PASS = "correct horse battery staple";

  @BeforeEach
  void setUp() {
    cipher = new CredentialCipher(""); // no env key -> locked
    service = new CredentialKeyService(cipher, repo);
    lenientPersistence(repo);
  }

  private void lenientPersistence(CredKeySettingRepository r) {
    lenient()
        .when(r.save(any()))
        .thenAnswer(
            inv -> {
              saved = inv.getArgument(0);
              return saved;
            });
    lenient()
        .when(r.findFirstByOrderByCreatedAtAsc())
        .thenAnswer(inv -> Optional.ofNullable(saved));
  }

  @Test
  void initializeDerivesActivatesAndPersistsVerifier() {
    assertThat(cipher.isConfigured()).isFalse();

    CredentialKeyService.KeyStatus st = service.initialize(PASS);

    assertThat(st.unlocked()).isTrue();
    assertThat(st.initialized()).isTrue();
    assertThat(st.envProvided()).isFalse();
    assertThat(cipher.isConfigured()).isTrue();
    assertThat(saved).isNotNull();
    // a real encrypted secret round-trips with the now-active key
    assertThat(cipher.decrypt(cipher.encrypt("s3cret"))).isEqualTo("s3cret");
  }

  @Test
  void rejectsShortPassphrase() {
    assertThatThrownBy(() -> service.initialize("short"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(saved).isNull();
  }

  @Test
  void initializeRejectedWhenAlreadyInitialized() {
    service.initialize(PASS);
    assertThatThrownBy(() -> service.initialize(PASS)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void unlockWithCorrectPassphraseReactivatesSameKey() {
    service.initialize(PASS);
    String token = cipher.encrypt("payload");

    // Simulate a restart: fresh cipher (locked) backed by the same persisted settings row.
    CredentialCipher restarted = new CredentialCipher("");
    CredentialKeyService svc2 = new CredentialKeyService(restarted, repo);
    assertThat(restarted.isConfigured()).isFalse();

    CredentialKeyService.KeyStatus st = svc2.unlock(PASS);

    assertThat(st.unlocked()).isTrue();
    // the re-derived key decrypts data encrypted before the restart
    assertThat(restarted.decrypt(token)).isEqualTo("payload");
  }

  @Test
  void unlockWithWrongPassphraseFails() {
    service.initialize(PASS);
    CredentialCipher restarted = new CredentialCipher("");
    CredentialKeyService svc2 = new CredentialKeyService(restarted, repo);

    assertThatThrownBy(() -> svc2.unlock("totally wrong passphrase"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(restarted.isConfigured()).isFalse();
  }

  @Test
  void envProvidedKeyDisablesInit() {
    CredentialCipher envCipher =
        new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    CredentialKeyService svc = new CredentialKeyService(envCipher, repo);

    assertThat(svc.status().envProvided()).isTrue();
    assertThat(svc.status().unlocked()).isTrue();
    assertThatThrownBy(() -> svc.initialize(PASS)).isInstanceOf(ResponseStatusException.class);
  }
}
