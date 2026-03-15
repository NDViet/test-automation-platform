package com.platform.testframework;

import com.platform.testframework.classify.FailureCategory;
import com.platform.testframework.classify.FailureClassifier;
import com.platform.testframework.classify.FailureHint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FailureClassifier}.
 *
 * <p>For Selenium/Playwright exception types (not on classpath), inner static
 * exception classes are used — their names contain the target substring so
 * the classifier's {@code getClass().getName().contains(...)} logic fires.</p>
 */
class FailureClassifierTest {

    // ── Fake exception classes (class-name matching without Selenium/Playwright) ─

    static class NoSuchElementException       extends RuntimeException {
        NoSuchElementException(String m)       { super(m); } }
    static class StaleElementReferenceException extends RuntimeException {
        StaleElementReferenceException(String m) { super(m); } }
    static class ElementClickInterceptedException extends RuntimeException {
        ElementClickInterceptedException(String m) { super(m); } }
    static class ElementNotInteractableException extends RuntimeException {
        ElementNotInteractableException(String m)  { super(m); } }
    static class WebDriverException           extends RuntimeException {
        WebDriverException(String m)           { super(m); } }

    // ── null / empty ─────────────────────────────────────────────────────────

    @Test
    void nullThrowableReturnsUnknown() {
        FailureHint hint = FailureClassifier.classify(null, null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.UNKNOWN);
    }

    // ── Infrastructure — network ──────────────────────────────────────────────

    @Test
    void connectExceptionIsInfrastructure() {
        FailureHint hint = FailureClassifier.classify(
                new ConnectException("Connection refused: localhost:8080"), null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.INFRASTRUCTURE);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.90);
    }

    @Test
    void unknownHostExceptionIsInfrastructure() {
        FailureHint hint = FailureClassifier.classify(
                new UnknownHostException("api.example.internal"), null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.INFRASTRUCTURE);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.90);
        assertThat(hint.message()).contains("DNS");
    }

    // ── Infrastructure signals in log ─────────────────────────────────────────

    @Test
    void connectionRefusedInLogIsInfrastructure() {
        FailureHint hint = FailureClassifier.classify(
                new IOException("unexpected error"),
                null,
                "[NETWORK FAIL] GET http://api.svc → connection refused");
        assertThat(hint.category()).isEqualTo(FailureCategory.INFRASTRUCTURE);
    }

    @Test
    void service503InLogIsInfrastructure() {
        FailureHint hint = FailureClassifier.classify(
                new RuntimeException("request failed"),
                null,
                "Response: 503 Service Unavailable from upstream");
        assertThat(hint.category()).isEqualTo(FailureCategory.INFRASTRUCTURE);
    }

    // ── Application bug — assertions ──────────────────────────────────────────

    @Test
    void assertionErrorIsApplicationBug() {
        FailureHint hint = FailureClassifier.classify(
                new AssertionError("expected: <200> but was: <404>"), null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.APPLICATION_BUG);
        assertThat(hint.confidence()).isGreaterThan(0.70);
    }

    @Test
    void httpStatusAssertionHasHigherConfidence() {
        FailureHint hint = FailureClassifier.classify(
                new AssertionError("expected 200 but got 500"), null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.APPLICATION_BUG);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void assertionDuringVerifyStepBoostsConfidence() {
        FailureHint hint = FailureClassifier.classify(
                new AssertionError("expected: <Dashboard> but was: <Login>"),
                "verify dashboard is displayed",
                null);
        assertThat(hint.category()).isEqualTo(FailureCategory.APPLICATION_BUG);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.80);
    }

    // ── Bad locator ───────────────────────────────────────────────────────────

    @Test
    void noSuchElementExceptionIsBadLocator() {
        // Inner class name contains "NoSuchElementException" — classifier matches it
        FailureHint hint = FailureClassifier.classify(
                new NoSuchElementException(
                        "Unable to locate element: By.cssSelector: .login-btn"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.BAD_LOCATOR);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.90);
    }

    @Test
    void unableToLocateElementMessageIsBadLocator() {
        FailureHint hint = FailureClassifier.classify(
                new RuntimeException("unable to locate element: #submit-button"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.BAD_LOCATOR);
    }

    // ── Flaky timing ──────────────────────────────────────────────────────────

    @Test
    void staleElementReferenceIsFlaky() {
        FailureHint hint = FailureClassifier.classify(
                new StaleElementReferenceException(
                        "stale element reference: element is not attached to the page"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.FLAKY_TIMING);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.90);
        assertThat(hint.message()).contains("Stale");
    }

    @Test
    void elementClickInterceptedIsFlaky() {
        FailureHint hint = FailureClassifier.classify(
                new ElementClickInterceptedException(
                        "element click intercepted: Element <button> is not clickable"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.FLAKY_TIMING);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.90);
    }

    @Test
    void isNotClickableAtPointMessageIsFlaky() {
        FailureHint hint = FailureClassifier.classify(
                new RuntimeException("Element is not clickable at point (123, 456) "
                        + "because another element obscures it"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.FLAKY_TIMING);
    }

    @Test
    void elementNotInteractableIsFlaky() {
        FailureHint hint = FailureClassifier.classify(
                new ElementNotInteractableException("element not interactable"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.FLAKY_TIMING);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.80);
    }

    // ── WebDriver ─────────────────────────────────────────────────────────────

    @Test
    void webDriverSessionNotCreatedIsInfrastructure() {
        FailureHint hint = FailureClassifier.classify(
                new WebDriverException("session not created: This version of ChromeDriver only supports Chrome version 114"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.INFRASTRUCTURE);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.90);
    }

    @Test
    void webDriverStaleMessageIsFlaky() {
        FailureHint hint = FailureClassifier.classify(
                new WebDriverException("stale element in WebDriver response"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.FLAKY_TIMING);
    }

    @Test
    void genericWebDriverIsInfrastructure() {
        FailureHint hint = FailureClassifier.classify(
                new WebDriverException("unknown error: cannot determine loading status"),
                null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.INFRASTRUCTURE);
    }

    // ── Test code bug ─────────────────────────────────────────────────────────

    @Test
    void npeInTestClassIsTestCodeBug() {
        NullPointerException npe = new NullPointerException("value is null");
        StackTraceElement[] frames = {
                new StackTraceElement("com.example.LoginTest", "userCanLogin", "LoginTest.java", 42),
                new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 1)
        };
        npe.setStackTrace(frames);
        FailureHint hint = FailureClassifier.classify(npe, null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.TEST_CODE_BUG);
        assertThat(hint.confidence()).isGreaterThanOrEqualTo(0.80);
    }

    @Test
    void npeInAppCodeIsApplicationBug() {
        NullPointerException npe = new NullPointerException("null user returned from service");
        StackTraceElement[] frames = {
                new StackTraceElement("com.app.service.UserService", "findById", "UserService.java", 55),
                new StackTraceElement("com.app.controller.UserController", "getUser", "UserController.java", 20)
        };
        npe.setStackTrace(frames);
        FailureHint hint = FailureClassifier.classify(npe, null, null);
        assertThat(hint.category()).isEqualTo(FailureCategory.APPLICATION_BUG);
    }

    // ── FailureHint behaviour ─────────────────────────────────────────────────

    @Test
    void highConfidenceThresholdAt75() {
        FailureHint high   = FailureHint.of(FailureCategory.BAD_LOCATOR, 0.95, "msg");
        FailureHint border = FailureHint.of(FailureCategory.FLAKY_TIMING, 0.75, "msg");
        FailureHint below  = FailureHint.of(FailureCategory.UNKNOWN,     0.74, "msg");
        assertThat(high.isHighConfidence()).isTrue();
        assertThat(border.isHighConfidence()).isTrue();
        assertThat(below.isHighConfidence()).isFalse();
    }

    @Test
    void unknownFactoryMethod() {
        FailureHint hint = FailureHint.unknown();
        assertThat(hint.category()).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(hint.confidence()).isEqualTo(0.0);
    }
}
