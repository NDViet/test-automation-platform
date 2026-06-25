package com.platform.ingestion.security;

import com.platform.core.domain.ApiKey;
import com.platform.core.repository.ApiKeyRepository;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a service-account API key for the portal at ingestion startup.
 *
 * <p>Set {@code PLATFORM_PORTAL_KEY} to the raw {@code plat_...} key value. The runner hashes it
 * and upserts a single row in {@code api_keys} (no-op if the row already exists). The same value
 * must be set as {@code portal.services.ingestion.service-key} on the portal service so it sends
 * the key on every request.
 *
 * <p>To generate a suitable key:
 *
 * <pre>
 *   python3 -c "import secrets,base64; print('plat_'+base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('='))"
 * </pre>
 *
 * <p>The key has no expiry and no team — it is a system service-account key.
 */
@Component
public class BootstrapApiKeyRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapApiKeyRunner.class);
  private static final String KEY_NAME = "portal-service-account";

  private final ApiKeyRepository keyRepo;
  private final String rawKey;

  public BootstrapApiKeyRunner(
      ApiKeyRepository keyRepo, @Value("${platform.portal.service-key:}") String rawKey) {
    this.keyRepo = keyRepo;
    this.rawKey = rawKey;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (rawKey == null || rawKey.isBlank()) {
      log.info(
          "[Bootstrap] PLATFORM_PORTAL_KEY not set — skipping portal service-account key"
              + " bootstrap.");
      return;
    }
    if (!rawKey.startsWith("plat_")) {
      log.error("[Bootstrap] PLATFORM_PORTAL_KEY must start with 'plat_'. Key not seeded.");
      return;
    }

    String hash = sha256hex(rawKey);
    String prefix = rawKey.substring(0, Math.min(12, rawKey.length()));

    if (keyRepo.findByKeyHash(hash).isPresent()) {
      log.debug("[Bootstrap] Portal service-account key already seeded (prefix={}).", prefix);
      return;
    }

    ApiKey key =
        ApiKey.builder()
            .name(KEY_NAME)
            .keyHash(hash)
            .keyPrefix(prefix)
            .teamId(null) // service-account keys are not team-scoped
            .build();
    keyRepo.save(key);
    log.info("[Bootstrap] Portal service-account API key seeded (prefix={}).", prefix);
  }

  private static String sha256hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
