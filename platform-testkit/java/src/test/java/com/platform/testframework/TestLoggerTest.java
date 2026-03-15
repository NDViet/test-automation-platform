package com.platform.testframework;

import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.logging.TestLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestLoggerTest {

    private TestContext ctx;
    private final TestLogger log = TestLogger.forClass(TestLoggerTest.class);

    @BeforeEach
    void setUp() {
        ctx = new TestContext(
                "TestLoggerTest#test", "test()", "TestLoggerTest", "test",
                List.of(), "trace-logger", "team", "proj");
        TestContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TestContextHolder.clear();
    }

    @Test
    void stepAppearsInContextRootSteps() {
        log.step("Navigate to home page");
        log.endStep();

        TestContext.Snapshot snap = ctx.snapshot();
        assertThat(snap.rootSteps()).hasSize(1);
        assertThat(snap.rootSteps().get(0).getName()).isEqualTo("Navigate to home page");
    }

    @Test
    void infoIsAppendedToCapturedLog() {
        log.info("User is: {}", "admin");

        TestContext.Snapshot snap = ctx.snapshot();
        assertThat(snap.capturedLog()).contains("User is: admin");
    }

    @Test
    void nestedStepsViaRunnableClosedAutomatically() {
        log.step("Parent step", () -> {
            log.step("Child step");
            log.info("inside child");
            log.endStep();
        });

        TestContext.Snapshot snap = ctx.snapshot();
        assertThat(snap.rootSteps()).hasSize(1);
        assertThat(snap.rootSteps().get(0).toDto().steps()).hasSize(1);
    }

    @Test
    void loggerWorksGracefullyWithoutActiveContext() {
        TestContextHolder.clear();
        // Should not throw even with no active context
        log.step("orphan step");
        log.info("orphan info");
        log.endStep();
    }
}
