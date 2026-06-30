package com.platform.security.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.platform.security.authz.Capability;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * Verifies the aspect resolves the effective {@link RequireCapability} from either the method or
 * the declaring class, with method-level precedence — the mechanism ingestion relies on to gate a
 * whole controller with one annotation while overriding individual mutating endpoints.
 */
class RequireCapabilityAspectTest {

  /** Whole controller defaults to VIEW; one endpoint overrides to OPERATE. */
  @RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
  static class SampleController {
    public String read(UUID projectId) {
      return "ok";
    }

    @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
    public String write(UUID projectId) {
      return "ok";
    }
  }

  /** Unannotated controller — the aspect must not enforce anything. */
  static class OpenController {
    public String ping() {
      return "ok";
    }
  }

  private <T> T proxy(T target, CapabilityEnforcer enforcer) {
    AspectJProxyFactory factory = new AspectJProxyFactory(target);
    factory.addAspect(new RequireCapabilityAspect(enforcer));
    return factory.getProxy();
  }

  @Test
  void classLevelAnnotationGatesEveryEndpoint() {
    CapabilityEnforcer enforcer = mock(CapabilityEnforcer.class);
    UUID projectId = UUID.randomUUID();

    proxy(new SampleController(), enforcer).read(projectId);

    verify(enforcer).enforce(Capability.VIEW_RESULTS, projectId);
  }

  @Test
  void methodLevelAnnotationOverridesClassLevel() {
    CapabilityEnforcer enforcer = mock(CapabilityEnforcer.class);
    UUID projectId = UUID.randomUUID();

    proxy(new SampleController(), enforcer).write(projectId);

    verify(enforcer).enforce(Capability.OPERATE_QUALITY, projectId);
  }

  @Test
  void unannotatedControllerIsNotEnforced() {
    CapabilityEnforcer enforcer = mock(CapabilityEnforcer.class);

    proxy(new OpenController(), enforcer).ping();

    verifyNoInteractions(enforcer);
  }
}
