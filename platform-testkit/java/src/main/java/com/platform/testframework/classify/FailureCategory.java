package com.platform.testframework.classify;

/**
 * Coarse-grained failure category inferred at the test framework level.
 *
 * <p>Sent to the platform as {@code platform.hint.category} in the environment
 * map of each test result. The platform AI classifier uses it as a strong prior
 * signal, reducing hallucination and improving confidence scores.</p>
 *
 * <h3>Decision flow used by {@link FailureClassifier}:</h3>
 * <pre>
 * Exception type / message
 *   ├─ NoSuchElementException / "unable to locate element"  → BAD_LOCATOR
 *   ├─ StaleElementReferenceException                       → FLAKY_TIMING
 *   ├─ ElementClickInterceptedException                     → FLAKY_TIMING
 *   ├─ TimeoutException (short timeout, dynamic content)    → FLAKY_TIMING
 *   ├─ TimeoutException (long, infra-related)               → TIMEOUT
 *   ├─ ConnectException / UnknownHostException              → INFRASTRUCTURE
 *   ├─ WebDriverException "session not created" / crash     → INFRASTRUCTURE
 *   ├─ AssertionError                                       → APPLICATION_BUG
 *   ├─ NullPointerException / ClassCastException in tests   → TEST_CODE_BUG
 *   └─ Everything else                                      → UNKNOWN
 * </pre>
 */
public enum FailureCategory {

    /**
     * Element selector / locator is wrong or no longer matches.
     * Fix: update the selector in the test/POM.
     */
    BAD_LOCATOR,

    /**
     * Race condition — element existed but was stale, animating, or intercepted.
     * Fix: add explicit waits; mark test as @Retryable if intermittent.
     */
    FLAKY_TIMING,

    /**
     * External timeout — network, DB, third-party service, or browser startup.
     * May indicate infrastructure degradation rather than a test issue.
     */
    TIMEOUT,

    /**
     * Infrastructure failure — browser crash, connection refused, DNS failure,
     * Docker container not running, Kafka/DB unreachable.
     * Fix: check environment, not the test.
     */
    INFRASTRUCTURE,

    /**
     * The application returned wrong data/status — the test is correctly
     * catching a regression.
     * Fix: fix the application.
     */
    APPLICATION_BUG,

    /**
     * The test code itself has a bug — NPE in setup, wrong assertion logic,
     * missing preconditions, bad data setup.
     * Fix: fix the test.
     */
    TEST_CODE_BUG,

    /** Insufficient signal to classify. Claude AI will attempt deeper analysis. */
    UNKNOWN
}
