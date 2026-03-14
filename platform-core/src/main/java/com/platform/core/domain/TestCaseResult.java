package com.platform.core.domain;

import com.platform.common.enums.TestStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "test_case_results",
       indexes = {
           @Index(name = "idx_tcr_test_id", columnList = "test_id"),
           @Index(name = "idx_tcr_execution_id", columnList = "execution_id"),
           @Index(name = "idx_tcr_status", columnList = "status")
       })
@EntityListeners(AuditingEntityListener.class)
public class TestCaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private TestExecution execution;

    @Column(name = "test_id", nullable = false, length = 500)
    private String testId;

    @Column(name = "display_name", length = 500)
    private String displayName;

    @Column(name = "class_name", length = 300)
    private String className;

    @Column(name = "method_name", length = 200)
    private String methodName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TestStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "retry_count")
    private int retryCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TestCaseResult() {}

    private TestCaseResult(Builder b) {
        this.execution = b.execution;
        this.testId = b.testId;
        this.displayName = b.displayName;
        this.className = b.className;
        this.methodName = b.methodName;
        this.tags = b.tags;
        this.status = b.status;
        this.durationMs = b.durationMs;
        this.failureMessage = b.failureMessage;
        this.stackTrace = b.stackTrace;
        this.retryCount = b.retryCount;
    }

    public static Builder builder() { return new Builder(); }

    // Getters
    public UUID getId() { return id; }
    public TestExecution getExecution() { return execution; }
    public String getTestId() { return testId; }
    public String getDisplayName() { return displayName; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public List<String> getTags() { return tags; }
    public TestStatus getStatus() { return status; }
    public Long getDurationMs() { return durationMs; }
    public String getFailureMessage() { return failureMessage; }
    public String getStackTrace() { return stackTrace; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestCaseResult r)) return false;
        return Objects.equals(id, r.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    public static class Builder {
        private TestExecution execution;
        private String testId;
        private String displayName;
        private String className;
        private String methodName;
        private List<String> tags = List.of();
        private TestStatus status;
        private Long durationMs;
        private String failureMessage;
        private String stackTrace;
        private int retryCount;

        public Builder execution(TestExecution v) { this.execution = v; return this; }
        public Builder testId(String v) { this.testId = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder className(String v) { this.className = v; return this; }
        public Builder methodName(String v) { this.methodName = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder status(TestStatus v) { this.status = v; return this; }
        public Builder durationMs(Long v) { this.durationMs = v; return this; }
        public Builder failureMessage(String v) { this.failureMessage = v; return this; }
        public Builder stackTrace(String v) { this.stackTrace = v; return this; }
        public Builder retryCount(int v) { this.retryCount = v; return this; }
        public TestCaseResult build() { return new TestCaseResult(this); }
    }
}
