package com.platform.ingestion.management;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages the passphrase-derived credential encryption key for platforms that don't set {@code
 * PLATFORM_CRED_KEY} in the environment. Lets a from-scratch platform establish the key (and
 * re-open it after a restart) from the UI instead of generating a base64 blob by hand. Secrets are
 * never returned.
 */
@RestController
@RequestMapping("/api/v1/security/cred-key")
@Tag(name = "Credential Key")
@RequireCapability(Capability.MANAGE_PLATFORM)
public class CredentialKeyController {

  private final CredentialKeyService keyService;

  public CredentialKeyController(CredentialKeyService keyService) {
    this.keyService = keyService;
  }

  public record PassphraseRequest(String passphrase) {}

  @GetMapping("/status")
  public CredentialKeyService.KeyStatus status() {
    return keyService.status();
  }

  /** First-run setup: choose a passphrase, derive + activate the key. */
  @PostMapping("/init")
  public CredentialKeyService.KeyStatus init(@RequestBody PassphraseRequest req) {
    return keyService.initialize(req.passphrase());
  }

  /** After a restart: re-enter the passphrase to unlock credential encryption. */
  @PostMapping("/unlock")
  public CredentialKeyService.KeyStatus unlock(@RequestBody PassphraseRequest req) {
    return keyService.unlock(req.passphrase());
  }
}
