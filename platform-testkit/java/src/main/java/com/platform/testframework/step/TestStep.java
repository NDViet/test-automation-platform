package com.platform.testframework.step;

import com.platform.common.dto.TestStepDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A mutable step being built during test execution.
 *
 * <p>Managed by {@link com.platform.testframework.context.TestContext} through a stack.
 * Converted to {@link TestStepDto} for platform publishing via {@link #toDto()}.</p>
 */
public final class TestStep {

    public enum Status { PASSED, FAILED, BROKEN, SKIPPED }

    private final String name;
    private final long startMs;
    private Status status = Status.PASSED;
    private String errorMessage;
    private final StringBuilder log = new StringBuilder();
    private final List<TestStep> children = new ArrayList<>();
    private long finishedMs;

    public TestStep(String name) {
        this.name    = name;
        this.startMs = Instant.now().toEpochMilli();
    }

    /** Called by TestContext when the step is popped from the stack. */
    public void finish() {
        if (finishedMs == 0) {
            finishedMs = Instant.now().toEpochMilli();
        }
    }

    public void markFailed(String message) {
        this.status       = Status.FAILED;
        this.errorMessage = message;
        finish();
    }

    public void markBroken(String message) {
        this.status       = Status.BROKEN;
        this.errorMessage = message;
        finish();
    }

    public void appendLog(String line) {
        log.append(line).append('\n');
    }

    public void addChild(TestStep child) {
        children.add(child);
    }

    public String getName()           { return name; }
    public Status getStatus()         { return status; }
    public List<TestStep> getChildren() { return Collections.unmodifiableList(children); }

    public TestStepDto toDto() {
        long duration = finishedMs > 0 ? finishedMs - startMs : 0;
        List<TestStepDto> childDtos = children.stream()
                .map(TestStep::toDto)
                .toList();
        return new TestStepDto(
                name,
                status.name(),
                duration,
                log.isEmpty() ? null : log.toString(),
                errorMessage,
                childDtos
        );
    }
}
