package com.platform.sdk.annotation;

import java.lang.annotation.*;

/**
 * Optional annotation to override the team / project configured in {@code platform.properties}
 * or environment variables on a per-class basis.
 *
 * <pre>{@code
 * @PlatformProject(teamId = "team-payments", projectId = "checkout-e2e")
 * class CheckoutE2ETest { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PlatformProject {

    /** Override the team identifier for this test class. Empty = use global config. */
    String teamId() default "";

    /** Override the project identifier for this test class. Empty = use global config. */
    String projectId() default "";
}
