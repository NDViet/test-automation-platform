package com.platform.testframework.context;

import com.platform.testframework.step.TestStep;

import java.time.Instant;
import java.util.*;

/**
 * Mutable per-test context stored in a thread-local.
 *
 * <p>Holds the running state of a single test execution: identity, timing,
 * accumulated steps, attachments, and trace correlation. The {@link #snapshot()}
 * method produces an immutable view for publishing after the test completes.</p>
 *
 * <p>Nested steps are tracked as a stack — {@link #pushStep} / {@link #popStep}
 * allow keyword-driven and page-object helpers to open child steps transparently.</p>
 */
public final class TestContext {

    private final String testId;
    private final String displayName;
    private final String className;
    private final String methodName;
    private final List<String> tags;
    private final String traceId;
    private final String teamId;
    private final String projectId;
    private final Instant startedAt;

    private final Deque<TestStep> stepStack = new ArrayDeque<>();
    private final List<TestStep> rootSteps  = new ArrayList<>();
    private final List<String>   attachments = new ArrayList<>();
    private final Map<String, String> environment = new LinkedHashMap<>();

    private final StringBuilder capturedLog = new StringBuilder();
    private int retryCount;
    private List<String> coveredClasses = new ArrayList<>();

    public TestContext(String testId, String displayName, String className,
                       String methodName, List<String> tags,
                       String traceId, String teamId, String projectId) {
        this.testId      = testId;
        this.displayName = displayName;
        this.className   = className;
        this.methodName  = methodName;
        this.tags        = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.traceId     = traceId;
        this.teamId      = teamId;
        this.projectId   = projectId;
        this.startedAt   = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Step management
    // -------------------------------------------------------------------------

    /** Open a new step. Nested calls create child steps under the current step. */
    public TestStep pushStep(String name) {
        TestStep step = new TestStep(name);
        if (stepStack.isEmpty()) {
            rootSteps.add(step);
        } else {
            stepStack.peek().addChild(step);
        }
        stepStack.push(step);
        return step;
    }

    /** Close the currently open step and return to the parent (or root). */
    public void popStep() {
        if (!stepStack.isEmpty()) {
            stepStack.pop().finish();
        }
    }

    /** Close all steps (e.g. on unexpected test abort). */
    public void closeAllSteps() {
        while (!stepStack.isEmpty()) {
            stepStack.pop().finish();
        }
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    public void appendLog(String line) {
        capturedLog.append(line).append('\n');
        if (!stepStack.isEmpty()) {
            stepStack.peek().appendLog(line);
        }
    }

    // -------------------------------------------------------------------------
    // Attachments & environment
    // -------------------------------------------------------------------------

    public void addAttachment(String path) {
        attachments.add(path);
    }

    public void putEnvironment(String key, String value) {
        environment.put(key, value);
    }

    public void putAllEnvironment(Map<String, String> entries) {
        environment.putAll(entries);
    }

    // -------------------------------------------------------------------------
    // Retry tracking
    // -------------------------------------------------------------------------

    public void incrementRetry() {
        retryCount++;
    }

    public void setCoveredClasses(List<String> classes) {
        this.coveredClasses = classes != null ? new ArrayList<>(classes) : new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Snapshot for publishing
    // -------------------------------------------------------------------------

    public Snapshot snapshot() {
        closeAllSteps();
        return new Snapshot(
                testId, displayName, className, methodName,
                Collections.unmodifiableList(tags),
                traceId, teamId, projectId, startedAt,
                Collections.unmodifiableList(rootSteps),
                Collections.unmodifiableList(attachments),
                Collections.unmodifiableMap(new LinkedHashMap<>(environment)),
                capturedLog.toString(),
                retryCount,
                Collections.unmodifiableList(coveredClasses)
        );
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getTestId()      { return testId; }
    public String getTraceId()     { return traceId; }
    public String getTeamId()      { return teamId; }
    public String getProjectId()   { return projectId; }
    public Instant getStartedAt()  { return startedAt; }

    /**
     * Immutable snapshot of a finished test context — safe to hand off to the publisher.
     */
    public record Snapshot(
            String testId,
            String displayName,
            String className,
            String methodName,
            List<String> tags,
            String traceId,
            String teamId,
            String projectId,
            Instant startedAt,
            List<TestStep> rootSteps,
            List<String> attachments,
            Map<String, String> environment,
            String capturedLog,
            int retryCount,
            List<String> coveredClasses
    ) {}
}
