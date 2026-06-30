package com.platform.security.web;

import com.platform.security.authz.Capability;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the capability a controller method requires. {@code scope} names the method parameter
 * holding the target scope id (project id for project capabilities, org id for {@code MANAGE_ORG});
 * empty for SUPER capabilities that take no scope. Enforced by {@link RequireCapabilityAspect} when
 * {@code platform.security.enabled} is true.
 *
 * <p>May be placed on a method or on the controller class (applies to every endpoint in it); a
 * method-level annotation overrides the class-level one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireCapability {

  Capability value();

  String scope() default "";
}
