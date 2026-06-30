package com.platform.security.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.platform.security.authz.Capability;
import com.platform.security.authz.PermissionEvaluator;
import com.platform.security.jwt.AuthenticatedUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CapabilityEnforcerTest {

  @Mock PermissionEvaluator evaluator;

  private final UUID scopeId = UUID.randomUUID();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private void setUser(AuthenticatedUser u) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, java.util.List.of()));
  }

  @Test
  void disabledIsNoOp() {
    CapabilityEnforcer off = new CapabilityEnforcer(evaluator, false);
    off.enforce(Capability.OPERATE_QUALITY, scopeId);
    verifyNoInteractions(evaluator);
  }

  @Test
  void enabledDelegatesToEvaluatorWithCurrentUser() {
    AuthenticatedUser u = new AuthenticatedUser(UUID.randomUUID(), "alice", false);
    setUser(u);
    CapabilityEnforcer on = new CapabilityEnforcer(evaluator, true);

    on.enforce(Capability.OPERATE_QUALITY, scopeId);

    verify(evaluator).require(u, Capability.OPERATE_QUALITY, scopeId);
  }

  @Test
  void enabledWithNoUserStillCallsEvaluatorWhichDenies() {
    // No authentication set ⇒ CurrentUser.get() is null; the evaluator decides (denies null).
    CapabilityEnforcer on = new CapabilityEnforcer(evaluator, true);

    assertThatCode(() -> on.enforce(Capability.VIEW_RESULTS, scopeId)).doesNotThrowAnyException();
    verify(evaluator).require(null, Capability.VIEW_RESULTS, scopeId);
  }
}
