package com.platform.testframework.step;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Resolves the display name for a {@code @Step}-annotated method.
 *
 * <p>Two resolution strategies:</p>
 * <ol>
 *   <li><b>Method name derivation</b> — when the annotation value is empty, the method
 *       name is split on camelCase boundaries and title-cased:
 *       {@code fillShippingAddress} → {@code "Fill Shipping Address"}</li>
 *   <li><b>Template interpolation</b> — when the annotation value contains
 *       {@code {paramName}} placeholders, each placeholder is replaced with the
 *       actual argument value at runtime:
 *       {@code "Submit payment with {cardType}"} + arg {@code "Visa"}
 *       → {@code "Submit payment with Visa"}</li>
 * </ol>
 */
public final class StepNameResolver {

    private StepNameResolver() {}

    /**
     * Resolves the step name from the annotation template, parameter names, and arguments.
     *
     * @param template   annotation value — empty string triggers method-name derivation
     * @param methodName the annotated method's simple name (e.g. {@code "fillShippingAddress"})
     * @param paramNames parameter names from the method signature (may be null if not available)
     * @param args       actual invocation arguments
     * @return resolved step name, never null
     */
    public static String resolve(String template, String methodName,
                                 String[] paramNames, Object[] args) {
        if (template == null || template.isBlank()) {
            return camelToTitle(methodName);
        }
        return interpolate(template, paramNames, args);
    }

    /**
     * Converts a camelCase method name to Title Case words.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code fillShippingAddress} → {@code "Fill Shipping Address"}</li>
     *   <li>{@code submitPaymentWithVisa} → {@code "Submit Payment With Visa"}</li>
     *   <li>{@code clickLoginButton} → {@code "Click Login Button"}</li>
     *   <li>{@code openURL} → {@code "Open Url"}</li>
     * </ul>
     */
    static String camelToTitle(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return methodName;
        }
        // Split on lower→upper and digit→upper and consecutive-upper→upper+lower transitions
        String[] words = methodName.split(
                "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"
        );
        return Arrays.stream(words)
                .filter(w -> !w.isBlank())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    /**
     * Replaces {@code {paramName}} placeholders in the template with actual argument values.
     *
     * <p>Unmatched placeholders are left as-is. Null arguments are rendered as
     * {@code "null"}.</p>
     *
     * @param template   template string containing zero or more {@code {name}} placeholders
     * @param paramNames method parameter names (null-safe)
     * @param args       actual argument values (null-safe)
     * @return interpolated string
     */
    static String interpolate(String template, String[] paramNames, Object[] args) {
        if (paramNames == null || args == null || paramNames.length == 0) {
            return template;
        }
        String result = template;
        for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
            result = result.replace("{" + paramNames[i] + "}", String.valueOf(args[i]));
        }
        return result;
    }
}
