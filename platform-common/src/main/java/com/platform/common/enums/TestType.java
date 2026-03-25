package com.platform.common.enums;

/**
 * High-level test category — distinguishes functional (correctness) tests from
 * non-functional (performance, security, accessibility) tests.
 *
 * <p>Inferred automatically from {@link SourceFormat}:
 * <ul>
 *   <li>K6, GATLING, JMETER → {@code PERFORMANCE}</li>
 *   <li>All others → {@code FUNCTIONAL}</li>
 * </ul>
 *
 * <p>CONTRACT, SECURITY, and ACCESSIBILITY are reserved for future parsers.
 */
public enum TestType {
    /** Unit, integration, E2E, API contract tests. Default for JUnit/Cucumber/TestNG/Playwright/Newman/Allure. */
    FUNCTIONAL,
    /** Load, stress, soak, and spike tests. Set for K6, Gatling, JMeter. */
    PERFORMANCE,
    /** Contract tests (Pact, Swagger validation). Reserved for future use. */
    CONTRACT,
    /** Security scan tests (OWASP ZAP, Burp). Reserved for future use. */
    SECURITY,
    /** Accessibility tests (axe, Lighthouse). Reserved for future use. */
    ACCESSIBILITY;

    /** Infer test type from source format — no caller should need to hardcode this mapping. */
    public static TestType from(SourceFormat format) {
        if (format == null) return FUNCTIONAL;
        return switch (format) {
            case K6, GATLING, JMETER -> PERFORMANCE;
            default -> FUNCTIONAL;
        };
    }
}
