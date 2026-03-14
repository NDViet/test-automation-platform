package com.platform.testframework.context;

/**
 * Static thread-local accessor for {@link TestContext}.
 *
 * <p>Safe for parallel test execution — each thread (test worker) gets its own
 * context. Always call {@link #clear()} in the {@code afterEach} lifecycle hook.</p>
 */
public final class TestContextHolder {

    private static final ThreadLocal<TestContext> HOLDER = new ThreadLocal<>();

    private TestContextHolder() {}

    public static void set(TestContext ctx) {
        HOLDER.set(ctx);
    }

    /** Returns the current context, or {@code null} if called outside a test. */
    public static TestContext get() {
        return HOLDER.get();
    }

    /** Returns the current context, throwing if none is active. */
    public static TestContext require() {
        TestContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "No active TestContext — ensure @ExtendWith(PlatformExtension.class) is on your test class");
        }
        return ctx;
    }

    /** Removes the context for the current thread. Must be called after each test. */
    public static void clear() {
        HOLDER.remove();
    }
}
