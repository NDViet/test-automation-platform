package com.platform.security.web;

import com.platform.security.authz.Capability;
import com.platform.security.authz.PermissionEvaluator;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Applies a capability check for the current request, gated by {@code platform.security.enabled}
 * (default false). When disabled it is a no-op — preserving today's behavior during staged rollout.
 */
@Component
public class CapabilityEnforcer {

  private final PermissionEvaluator evaluator;
  private final boolean enabled;

  public CapabilityEnforcer(
      PermissionEvaluator evaluator, @Value("${platform.security.enabled:true}") boolean enabled) {
    this.evaluator = evaluator;
    this.enabled = enabled;
  }

  /** Enforce the capability for the current user at the given scope (no-op when disabled). */
  public void enforce(Capability capability, UUID scopeId) {
    if (!enabled) {
      return;
    }
    evaluator.require(CurrentUser.get(), capability, scopeId);
  }

  public boolean isEnabled() {
    return enabled;
  }
}
