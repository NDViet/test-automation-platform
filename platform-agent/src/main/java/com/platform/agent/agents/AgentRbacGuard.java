package com.platform.agent.agents;

import com.platform.security.authz.Capability;
import com.platform.security.web.CapabilityEnforcer;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Authorization for agent/task-agent writes, delegating to the platform-wide capability model.
 * Per the RBAC spec, agent management is a Tester capability: PROJECT-scoped writes require
 * {@code OPERATE_QUALITY}; ORG-scoped writes require {@code MANAGE_ORG}. A no-op while
 * {@code platform.security.enabled} is false (handled by {@link CapabilityEnforcer}). Reads are not
 * gated here.
 */
@Component
public class AgentRbacGuard {

  static final String SCOPE_ORG = "ORG";

  private final CapabilityEnforcer enforcer;

  public AgentRbacGuard(CapabilityEnforcer enforcer) {
    this.enforcer = enforcer;
  }

  /** Throw 403 unless the current user may manage agents at the given scope. */
  public void requireManage(String scope, UUID scopeId) {
    Capability capability =
        SCOPE_ORG.equals(scope) ? Capability.MANAGE_ORG : Capability.OPERATE_QUALITY;
    enforcer.enforce(capability, scopeId);
  }
}
