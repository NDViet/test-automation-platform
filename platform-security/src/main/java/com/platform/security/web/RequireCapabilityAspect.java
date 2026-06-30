package com.platform.security.web;

import java.lang.reflect.Method;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link RequireCapability} on controller methods: resolves the scope id from the named
 * method parameter and delegates to {@link CapabilityEnforcer} (which no-ops when security is off).
 *
 * <p>The annotation may sit on the method <em>or</em> on the controller class — handy for services
 * like ingestion where a whole controller maps to one capability/scope. A method-level annotation
 * always wins over the class-level one (e.g. a read-default controller can override individual
 * mutating endpoints to {@code OPERATE_QUALITY}).
 */
@Aspect
@Component
public class RequireCapabilityAspect {

  private final CapabilityEnforcer enforcer;

  public RequireCapabilityAspect(CapabilityEnforcer enforcer) {
    this.enforcer = enforcer;
  }

  @Before(
      "@annotation(com.platform.security.web.RequireCapability)"
          + " || @within(com.platform.security.web.RequireCapability)")
  public void enforce(JoinPoint joinPoint) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    RequireCapability req = method.getAnnotation(RequireCapability.class);
    if (req == null) {
      req = method.getDeclaringClass().getAnnotation(RequireCapability.class);
    }
    if (req == null) {
      return;
    }
    UUID scopeId = req.scope().isBlank() ? null : resolveScopeId(joinPoint, req.scope());
    enforcer.enforce(req.value(), scopeId);
  }

  private UUID resolveScopeId(JoinPoint joinPoint, String paramName) {
    MethodSignature sig = (MethodSignature) joinPoint.getSignature();
    String[] names = sig.getParameterNames();
    Object[] args = joinPoint.getArgs();
    if (names != null) {
      for (int i = 0; i < names.length; i++) {
        if (paramName.equals(names[i])) {
          return toUuid(args[i]);
        }
      }
    }
    return null;
  }

  private static UUID toUuid(Object value) {
    if (value == null) return null;
    if (value instanceof UUID u) return u;
    try {
      return UUID.fromString(value.toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
