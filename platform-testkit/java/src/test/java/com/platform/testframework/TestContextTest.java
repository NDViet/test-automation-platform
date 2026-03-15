package com.platform.testframework;

import com.platform.testframework.context.TestContext;
import com.platform.testframework.context.TestContextHolder;
import com.platform.testframework.step.TestStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestContextTest {

    @AfterEach
    void cleanup() {
        TestContextHolder.clear();
    }

    @Test
    void holderStoredAndRetrievedByCurrentThread() {
        TestContext ctx = new TestContext(
                "com.example.Foo#bar", "bar()", "com.example.Foo", "bar",
                List.of("smoke"), "trace-abc", "team-1", "proj-1");
        TestContextHolder.set(ctx);

        assertThat(TestContextHolder.get()).isSameAs(ctx);
        assertThat(TestContextHolder.require()).isSameAs(ctx);
    }

    @Test
    void requireThrowsWhenNoContextIsActive() {
        assertThatThrownBy(TestContextHolder::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active TestContext");
    }

    @Test
    void pushPopStepBuildsRootSteps() {
        TestContext ctx = new TestContext(
                "id", "display", "ClassName", "method",
                List.of(), "trace-1", "team", "proj");

        ctx.pushStep("Step A");
        ctx.appendLog("log line A");
        ctx.popStep();

        ctx.pushStep("Step B");
        ctx.popStep();

        TestContext.Snapshot snap = ctx.snapshot();
        assertThat(snap.rootSteps()).hasSize(2);
        assertThat(snap.rootSteps().get(0).getName()).isEqualTo("Step A");
        assertThat(snap.rootSteps().get(1).getName()).isEqualTo("Step B");
    }

    @Test
    void nestedStepsAreChildrenOfParent() {
        TestContext ctx = new TestContext(
                "id", "display", "ClassName", "method",
                List.of(), "trace-2", "team", "proj");

        ctx.pushStep("Parent");
        ctx.pushStep("Child A");
        ctx.popStep();
        ctx.pushStep("Child B");
        ctx.popStep();
        ctx.popStep();

        TestContext.Snapshot snap = ctx.snapshot();
        assertThat(snap.rootSteps()).hasSize(1);

        TestStep parent = snap.rootSteps().get(0);
        assertThat(parent.getName()).isEqualTo("Parent");
        assertThat(parent.toDto().steps()).hasSize(2);
        assertThat(parent.toDto().steps().get(0).name()).isEqualTo("Child A");
        assertThat(parent.toDto().steps().get(1).name()).isEqualTo("Child B");
    }

    @Test
    void environmentAndAttachmentsArePreserved() {
        TestContext ctx = new TestContext(
                "id", "display", "ClassName", "method",
                List.of(), "trace-3", "team", "proj");

        ctx.putEnvironment("browser", "Chrome 120");
        ctx.putEnvironment("app.url", "https://staging.example.com");
        ctx.addAttachment("/tmp/screenshot.png");

        TestContext.Snapshot snap = ctx.snapshot();
        assertThat(snap.environment()).containsEntry("browser", "Chrome 120");
        assertThat(snap.environment()).containsEntry("app.url", "https://staging.example.com");
        assertThat(snap.attachments()).containsExactly("/tmp/screenshot.png");
    }

    @Test
    void snapshotClosesAllOpenSteps() {
        TestContext ctx = new TestContext(
                "id", "display", "ClassName", "method",
                List.of(), "trace-4", "team", "proj");

        ctx.pushStep("Unclosed step");
        // snapshot() called without popStep() — should auto-close
        TestContext.Snapshot snap = ctx.snapshot();
        assertThat(snap.rootSteps()).hasSize(1);
        assertThat(snap.rootSteps().get(0).getName()).isEqualTo("Unclosed step");
    }
}
