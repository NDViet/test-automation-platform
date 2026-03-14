package com.platform.testframework.assertion;

import org.assertj.core.api.SoftAssertions;

import java.util.function.Consumer;

/**
 * Wrapper around AssertJ {@link SoftAssertions} that also logs each assertion
 * check to the active {@link com.platform.testframework.context.TestContext}.
 *
 * <p>Collects all assertion failures and reports them together at the end —
 * so you see <em>every</em> failure in one test run, not just the first.</p>
 *
 * <h3>Usage — fluent block style:</h3>
 * <pre>{@code
 * SoftAssert.assertAll(soft -> {
 *     soft.assertThat(response.getStatus()).isEqualTo(200);
 *     soft.assertThat(response.getBody()).contains("success");
 *     soft.assertThat(header).isNotNull();
 * });
 * }</pre>
 *
 * <h3>Usage — instance style (for page-object methods):</h3>
 * <pre>{@code
 * var soft = new SoftAssert();
 * soft.check(page.getTitle()).isEqualTo("Dashboard");
 * soft.check(page.getUserName()).isEqualTo("admin");
 * soft.assertAll();  // throws with all failures collected
 * }</pre>
 */
public final class SoftAssert {

    private final SoftAssertions delegate = new SoftAssertions();

    /**
     * Returns an AssertJ {@code AbstractAssert} chain for the given object.
     * All failures are deferred until {@link #assertAll()} is called.
     */
    public <T> org.assertj.core.api.ObjectAssert<T> check(T actual) {
        return delegate.assertThat(actual);
    }

    public org.assertj.core.api.AbstractStringAssert<?> check(String actual) {
        return delegate.assertThat(actual);
    }

    public org.assertj.core.api.AbstractIntegerAssert<?> check(int actual) {
        return delegate.assertThat(actual);
    }

    public org.assertj.core.api.AbstractBooleanAssert<?> check(boolean actual) {
        return delegate.assertThat(actual);
    }

    /**
     * Throws an {@link AssertionError} listing all failures collected so far.
     * No-op if all assertions passed.
     */
    public void assertAll() {
        delegate.assertAll();
    }

    /**
     * Static convenience: runs {@code assertions} and calls {@code assertAll()}.
     *
     * @param assertions consumer that adds assertions to the soft-assert scope
     */
    public static void assertAll(Consumer<SoftAssertions> assertions) {
        SoftAssertions soft = new SoftAssertions();
        assertions.accept(soft);
        soft.assertAll();
    }
}
