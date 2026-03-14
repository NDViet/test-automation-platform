package com.platform.testframework.classify;

import java.util.Arrays;
import java.util.List;

/**
 * Rule-based failure classifier that analyzes a {@link Throwable} and test context
 * to produce a {@link FailureHint} before the result is sent to the platform.
 *
 * <h3>Design principles:</h3>
 * <ul>
 *   <li><b>No hard dependencies</b> — matches Selenium/Playwright/RestAssured
 *       exception types by class name string so this compiles without those
 *       libraries on the classpath.</li>
 *   <li><b>Layered rules</b> — most-specific exception types win; ambiguous
 *       timeouts are disambiguated by message content and step name.</li>
 *   <li><b>Transparent</b> — confidence score reflects certainty; low-confidence
 *       results still surface to Claude AI for deeper analysis.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * FailureHint hint = FailureClassifier.classify(throwable, currentStepName, capturedLog);
 * context.putEnvironment("platform.hint.category",    hint.category().name());
 * context.putEnvironment("platform.hint.confidence",  String.valueOf(hint.confidence()));
 * context.putEnvironment("platform.hint.message",     hint.message());
 * }</pre>
 */
public final class FailureClassifier {

    private FailureClassifier() {}

    /**
     * Classify a test failure.
     *
     * @param throwable   the exception that caused the test to fail (may be null)
     * @param currentStep name of the last/current step when failure occurred (may be null)
     * @param capturedLog accumulated log lines from the test (may be null)
     * @return a {@link FailureHint} — never null
     */
    public static FailureHint classify(Throwable throwable, String currentStep, String capturedLog) {
        if (throwable == null) return FailureHint.unknown();

        String exName    = fullExceptionName(throwable);
        String exMsg     = messageOf(throwable);
        String stepLower = currentStep  != null ? currentStep.toLowerCase()  : "";
        String logLower  = capturedLog  != null ? capturedLog.toLowerCase()  : "";

        // ── 1. Bad locator ───────────────────────────────────────────────
        if (is(exName, "NoSuchElementException", "ElementNotFoundException",
                       "ElementNotFoundError")) {
            return FailureHint.of(FailureCategory.BAD_LOCATOR, 0.95,
                    "Element not found. The selector '" + extractSelector(exMsg)
                    + "' did not match any element. Check the locator is still "
                    + "valid in the current DOM — it may have been renamed or moved.");
        }
        if (contains(exMsg, "unable to locate element", "no such element",
                             "element not found", "locator", "no element found")) {
            return FailureHint.of(FailureCategory.BAD_LOCATOR, 0.90,
                    "Locator resolution failed: " + firstLine(exMsg)
                    + ". Verify the selector strategy and the current page state.");
        }

        // ── 2. Flaky timing — stale element ──────────────────────────────
        if (is(exName, "StaleElementReferenceException")) {
            return FailureHint.of(FailureCategory.FLAKY_TIMING, 0.95,
                    "Stale element reference — the DOM was updated after the element "
                    + "was located. Add an explicit wait for the element to stabilise, "
                    + "or re-locate the element inside the action. Consider @Retryable.");
        }

        // ── 3. Flaky timing — click intercepted ──────────────────────────
        if (is(exName, "ElementClickInterceptedException", "ElementClickInterceptedError")) {
            return FailureHint.of(FailureCategory.FLAKY_TIMING, 0.92,
                    "Click intercepted by an overlapping element (modal, spinner, overlay). "
                    + "Wait for the overlay to disappear before clicking. Consider @Retryable.");
        }
        if (contains(exMsg, "element click intercepted", "other element would receive the click",
                             "is not clickable at point")) {
            return FailureHint.of(FailureCategory.FLAKY_TIMING, 0.88,
                    "Element is obscured: " + firstLine(exMsg)
                    + ". Wait for animations/overlays to finish.");
        }

        // ── 4. Flaky timing — element not interactable ───────────────────
        if (is(exName, "ElementNotInteractableException", "ElementNotInteractableError")) {
            return FailureHint.of(FailureCategory.FLAKY_TIMING, 0.85,
                    "Element is present but not interactable (disabled, hidden, off-screen, "
                    + "or still animating). Add an explicit visibility/enabled wait.");
        }

        // ── 5. Playwright-specific timeout disambiguated by message ───────
        if (is(exName, "TimeoutError") && contains(exName, "playwright", "microsoft")) {
            return classifyPlaywrightTimeout(exMsg, stepLower);
        }

        // ── 6. Selenium TimeoutException ─────────────────────────────────
        if (is(exName, "TimeoutException") && !contains(exName, "java.net", "concurrent")) {
            return classifySeleniumTimeout(exMsg, stepLower);
        }

        // ── 7. Infrastructure — network / connectivity ────────────────────
        if (is(exName, "ConnectException", "ConnectionRefusedException")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.95,
                    "Connection refused: " + firstLine(exMsg)
                    + ". The target service is not reachable. "
                    + "Check if the server/container is running.");
        }
        if (is(exName, "UnknownHostException")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.95,
                    "DNS resolution failed for '" + firstLine(exMsg)
                    + "'. Check the hostname and network configuration.");
        }
        if (is(exName, "SocketTimeoutException")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.75,
                    "Socket read timeout — the server accepted the connection but did not "
                    + "respond in time. Could be slow infrastructure or high load.");
        }
        if (is(exName, "SSLException", "SSLHandshakeException")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.85,
                    "SSL/TLS handshake failure: " + firstLine(exMsg)
                    + ". Check certificate validity and TLS configuration.");
        }

        // ── 8. Infrastructure — browser / WebDriver ───────────────────────
        if (is(exName, "WebDriverException") || contains(exName, "webdriver")) {
            return classifyWebDriverException(exMsg);
        }
        if (contains(exMsg, "browser has been closed", "target closed",
                             "browser context was closed", "browser was disconnected")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.90,
                    "Browser/context was closed unexpectedly. This is usually a "
                    + "memory/resource issue in the test environment, not the application.");
        }
        if (contains(exMsg, "session not created", "chrome not reachable",
                             "driver executable does not exist", "cannot find chrome binary")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.95,
                    "Browser/driver setup failed: " + firstLine(exMsg)
                    + ". Check that the browser driver is installed and matches the browser version.");
        }

        // ── 9. Generic timeout ────────────────────────────────────────────
        if (is(exName, "SocketTimeoutException", "TimeoutException")
                || contains(exMsg, "timed out", "timeout expired", "read timed out")) {
            return FailureHint.of(FailureCategory.TIMEOUT, 0.80,
                    "Operation timed out. Could be slow infrastructure, "
                    + "network congestion, or a genuinely hanging operation. "
                    + "Check environment health.");
        }

        // ── 10. Test code bug ─────────────────────────────────────────────
        if (is(exName, "NullPointerException")) {
            return classifyNpe(throwable);
        }
        if (is(exName, "ClassCastException", "ArrayIndexOutOfBoundsException",
                       "IndexOutOfBoundsException", "IllegalArgumentException",
                       "IllegalStateException")) {
            if (isInTestCode(throwable)) {
                return FailureHint.of(FailureCategory.TEST_CODE_BUG, 0.80,
                        exName + " in test/framework code: " + firstLine(exMsg)
                        + ". This is likely a bug in the test setup or assertion logic.");
            }
        }

        // ── 11. Application bug — assertion failures ──────────────────────
        if (throwable instanceof AssertionError) {
            return classifyAssertionError(exMsg, stepLower);
        }

        // ── 12. Infrastructure signals in log ─────────────────────────────
        if (contains(logLower, "connection refused", "econnrefused", "503 service unavailable",
                               "502 bad gateway", "database is unavailable", "kafka unavailable")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.70,
                    "Infrastructure issue detected in test logs. "
                    + "A dependent service appears to be unreachable.");
        }

        return FailureHint.unknown();
    }

    // ── Playwright timeout disambiguation ────────────────────────────────────

    private static FailureHint classifyPlaywrightTimeout(String msg, String step) {
        // "Locator.click: Timeout ... waiting for locator 'xxx'" → BAD_LOCATOR hint
        if (contains(msg, "locator", "waiting for locator", "no element found",
                          "not found in page")) {
            return FailureHint.of(FailureCategory.BAD_LOCATOR, 0.80,
                    "Playwright timed out waiting for a locator that was never found: "
                    + firstLine(msg)
                    + ". Verify the selector and ensure the element appears on the page.");
        }
        // Animation / loading → FLAKY_TIMING
        if (contains(msg, "animation", "transition", "loading", "spinner")
                || contains(step, "wait", "animation", "loading")) {
            return FailureHint.of(FailureCategory.FLAKY_TIMING, 0.78,
                    "Playwright timeout while waiting for animation/loading to complete. "
                    + "Consider increasing the timeout or waiting for a stable state.");
        }
        return FailureHint.of(FailureCategory.FLAKY_TIMING, 0.70,
                "Playwright TimeoutError: " + firstLine(msg)
                + ". The element/condition was not met within the timeout. "
                + "Could be timing or a missing element.");
    }

    // ── Selenium timeout disambiguation ──────────────────────────────────────

    private static FailureHint classifySeleniumTimeout(String msg, String step) {
        if (contains(msg, "element", "locator", "find")) {
            return FailureHint.of(FailureCategory.BAD_LOCATOR, 0.72,
                    "Selenium timed out waiting for an element: " + firstLine(msg)
                    + ". The element may not exist or the selector may have changed.");
        }
        if (contains(step, "wait for", "animation", "loading", "spinner")) {
            return FailureHint.of(FailureCategory.FLAKY_TIMING, 0.75,
                    "Timeout while waiting for a dynamic condition. "
                    + "The page may be slower than expected — consider @Retryable.");
        }
        return FailureHint.of(FailureCategory.TIMEOUT, 0.65,
                "Selenium TimeoutException: " + firstLine(msg));
    }

    // ── WebDriver exception disambiguation ───────────────────────────────────

    private static FailureHint classifyWebDriverException(String msg) {
        if (contains(msg, "session not created", "cannot find chrome binary",
                          "driver executable does not exist")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.95,
                    "Browser/driver setup failure: " + firstLine(msg));
        }
        if (contains(msg, "chrome not reachable", "unable to connect",
                          "connection refused")) {
            return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.90,
                    "Browser process is not reachable: " + firstLine(msg)
                    + ". The browser may have crashed or been killed by the OS.");
        }
        if (contains(msg, "stale", "obsolete")) {
            return FailureHint.of(FailureCategory.FLAKY_TIMING, 0.85,
                    "Stale/obsolete element in WebDriverException: " + firstLine(msg));
        }
        return FailureHint.of(FailureCategory.INFRASTRUCTURE, 0.60,
                "WebDriverException: " + firstLine(msg)
                + ". Check browser driver compatibility and environment.");
    }

    // ── NPE disambiguation ───────────────────────────────────────────────────

    private static FailureHint classifyNpe(Throwable t) {
        if (isInTestCode(t)) {
            return FailureHint.of(FailureCategory.TEST_CODE_BUG, 0.82,
                    "NullPointerException in test/framework code. "
                    + "Check test data setup and preconditions.");
        }
        return FailureHint.of(FailureCategory.APPLICATION_BUG, 0.65,
                "NullPointerException in application code — the app may be returning null "
                + "where the test expects a value.");
    }

    // ── Assertion error disambiguation ───────────────────────────────────────

    private static FailureHint classifyAssertionError(String msg, String step) {
        if (contains(msg, "status", "http", "response code", "expected 200", "expected 201")) {
            return FailureHint.of(FailureCategory.APPLICATION_BUG, 0.85,
                    "HTTP status assertion failed: " + firstLine(msg)
                    + ". The application returned an unexpected response code.");
        }
        if (contains(step, "verify", "assert", "check", "validate", "confirm")) {
            return FailureHint.of(FailureCategory.APPLICATION_BUG, 0.80,
                    "Assertion failed during verification step '" + step + "': "
                    + firstLine(msg));
        }
        return FailureHint.of(FailureCategory.APPLICATION_BUG, 0.72,
                "Test assertion failed: " + firstLine(msg)
                + ". The application behavior does not match expected values.");
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /** True if ANY of the classNames appear in the exception's full class name. */
    private static boolean is(String exceptionName, String... classNames) {
        for (String name : classNames) {
            if (exceptionName.contains(name)) return true;
        }
        return false;
    }

    /** True if the text contains ANY of the fragments (case-insensitive). */
    private static boolean contains(String text, String... fragments) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        for (String frag : fragments) {
            if (lower.contains(frag.toLowerCase())) return true;
        }
        return false;
    }

    private static String fullExceptionName(Throwable t) {
        return t.getClass().getName();
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : "";
    }

    private static String firstLine(String s) {
        if (s == null || s.isBlank()) return "(no message)";
        int nl = s.indexOf('\n');
        return nl > 0 ? s.substring(0, nl).trim() : s.trim();
    }

    private static String extractSelector(String msg) {
        // Try to pull selector from messages like: "By.cssSelector: .login-btn"
        if (msg == null) return "unknown";
        for (String prefix : List.of("By.cssSelector:", "By.xpath:", "By.id:", "locator(")) {
            int idx = msg.indexOf(prefix);
            if (idx >= 0) {
                String rest = msg.substring(idx + prefix.length()).trim();
                int end = rest.indexOf('\n');
                return end > 0 ? rest.substring(0, end).trim() : rest.trim();
            }
        }
        return "unknown";
    }

    /**
     * Heuristic: is the exception likely thrown from test/framework code rather
     * than the application or browser library?
     */
    private static boolean isInTestCode(Throwable t) {
        return Arrays.stream(t.getStackTrace())
                .limit(5) // check top 5 frames
                .anyMatch(frame -> {
                    String cls = frame.getClassName();
                    return cls.contains(".test.") || cls.endsWith("Test")
                            || cls.endsWith("Spec") || cls.endsWith("Steps")
                            || cls.endsWith("Page") || cls.endsWith("Helper");
                });
    }
}
