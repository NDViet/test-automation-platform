package com.platform.testframework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a named test step.
 *
 * <p>When a method annotated with {@code @Step} is called during a test, the platform
 * testkit automatically opens a step in the current test context, marks it PASSED on
 * success or FAILED/BROKEN on exception, and closes it when the method returns.</p>
 *
 * <p>If {@code allure-java-commons} is on the classpath, the same step is also written
 * to the Allure lifecycle — producing a local HTML report with no extra code.</p>
 *
 * <h3>Step name resolution</h3>
 * <ul>
 *   <li>Empty {@link #value()} → name derived from the method name by splitting on
 *       camelCase boundaries: {@code fillShippingAddress} → {@code "Fill Shipping Address"}</li>
 *   <li>Non-empty {@link #value()} is used as-is, with optional {@code {paramName}}
 *       placeholders replaced by the actual argument values at runtime.</li>
 * </ul>
 *
 * <h3>Nesting</h3>
 * <p>A {@code @Step} method calling another {@code @Step} method produces a parent-child
 * step hierarchy automatically — no extra configuration needed.</p>
 *
 * <pre>{@code
 * // Name derived from method: "Fill Shipping Address"
 * @Step
 * public void fillShippingAddress(Address address) { ... }
 *
 * // Name with parameter interpolation: "Submit payment with Visa"
 * @Step("Submit payment with {cardType}")
 * public void submitPayment(String cardType, Card card) { ... }
 *
 * // Composed step — nesting built automatically
 * @Step("Complete checkout")
 * public void completeCheckout(Address address, Card card) {
 *     fillShippingAddress(address);   // child step
 *     submitPayment("Visa", card);    // child step
 * }
 * }</pre>
 *
 * <h3>AOP requirement</h3>
 * <p>{@code @Step} interception requires AspectJ load-time weaving. Add
 * {@code aspectjweaver} as a JVM agent in your test runner:</p>
 * <pre>{@code
 * <!-- Maven Surefire — resolves path via maven-dependency-plugin:properties -->
 * <argLine>-javaagent:${org.aspectj:aspectjweaver:jar}</argLine>
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Step {

    /**
     * Step description. Supports {@code {paramName}} interpolation.
     * Leave empty to derive the name from the method name.
     */
    String value() default "";
}
