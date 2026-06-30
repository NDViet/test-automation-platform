package com.platform.portal.auth;

import com.platform.core.domain.User;
import com.platform.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On first start (no users yet), provisions the bootstrap super-admin so the platform owner can log
 * in. Username defaults to {@code admin}; the initial password is {@code PLATFORM_CRED_KEY}
 * (BCrypt-hashed) and the account is forced to change it on first login. Idempotent — a no-op once
 * any user exists. Fails fast if {@code PLATFORM_CRED_KEY} is unset, so we never create an account
 * with an empty/known-default password.
 */
@Component
public class BootstrapAdminRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

  private final UserRepository userRepo;
  private final PasswordEncoder encoder;
  private final String credKey;
  private final String username;

  public BootstrapAdminRunner(
      UserRepository userRepo,
      PasswordEncoder encoder,
      @Value("${PLATFORM_CRED_KEY:}") String credKey,
      @Value("${SUPER_ADMIN_USERNAME:admin}") String username) {
    this.userRepo = userRepo;
    this.encoder = encoder;
    this.credKey = credKey;
    this.username = username;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (userRepo.count() > 0) {
      return; // already provisioned
    }
    if (credKey == null || credKey.isBlank()) {
      throw new IllegalStateException(
          "Cannot bootstrap super-admin: PLATFORM_CRED_KEY is not set");
    }
    User admin =
        new User(username, null, encoder.encode(credKey), "Super Admin", true, true);
    userRepo.save(admin);
    log.warn(
        "Bootstrapped super-admin '{}' (initial password = PLATFORM_CRED_KEY; change on first login)",
        username);
  }
}
