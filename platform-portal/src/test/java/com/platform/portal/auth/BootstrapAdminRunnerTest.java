package com.platform.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.User;
import com.platform.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminRunnerTest {

  @Mock UserRepository userRepo;
  final PasswordEncoder encoder = new BCryptPasswordEncoder();

  @Test
  void createsSuperAdminWhenEmpty() {
    when(userRepo.count()).thenReturn(0L);
    when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    new BootstrapAdminRunner(userRepo, encoder, "the-cred-key", "admin").run();

    ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
    verify(userRepo).save(cap.capture());
    User u = cap.getValue();
    assertThat(u.getUsername()).isEqualTo("admin");
    assertThat(u.isSuperAdmin()).isTrue();
    assertThat(u.isMustChangePassword()).isTrue();
    assertThat(u.getPasswordHash()).isNotEqualTo("the-cred-key"); // hashed, not plaintext
    assertThat(encoder.matches("the-cred-key", u.getPasswordHash())).isTrue();
  }

  @Test
  void noOpWhenUsersExist() {
    when(userRepo.count()).thenReturn(1L);

    new BootstrapAdminRunner(userRepo, encoder, "k", "admin").run();

    verify(userRepo, never()).save(any());
  }

  @Test
  void failsWhenCredKeyMissing() {
    when(userRepo.count()).thenReturn(0L);

    BootstrapAdminRunner runner = new BootstrapAdminRunner(userRepo, encoder, "  ", "admin");

    assertThatThrownBy(runner::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PLATFORM_CRED_KEY");
    verify(userRepo, never()).save(any());
  }
}
