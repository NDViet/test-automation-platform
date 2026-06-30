package com.platform.agent.agents;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.platform.security.authz.Capability;
import com.platform.security.web.CapabilityEnforcer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AgentRbacGuardTest {

  @Mock CapabilityEnforcer enforcer;

  AgentRbacGuard guard;

  @BeforeEach
  void setUp() {
    guard = new AgentRbacGuard(enforcer);
  }

  @Test
  void projectScopeRequiresOperate() {
    UUID id = UUID.randomUUID();
    guard.requireManage("PROJECT", id);
    verify(enforcer).enforce(Capability.OPERATE_QUALITY, id);
  }

  @Test
  void orgScopeRequiresManageOrg() {
    UUID id = UUID.randomUUID();
    guard.requireManage("ORG", id);
    verify(enforcer).enforce(Capability.MANAGE_ORG, id);
  }

  @Test
  void denialPropagates() {
    UUID id = UUID.randomUUID();
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no"))
        .when(enforcer)
        .enforce(Capability.OPERATE_QUALITY, id);

    assertThatThrownBy(() -> guard.requireManage("PROJECT", id))
        .isInstanceOf(ResponseStatusException.class);
  }
}
