package com.platform.core.domain;

import com.platform.common.enums.TestStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "test_case_results",
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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tags", columnDefinition = "jsonb")
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

  @Column(name = "trace_store_path", length = 500)
  private String traceStorePath;

  // ── Tier-1 metadata ───────────────────────────────────────────────────────

  /** Spec file path relative to the project root, e.g. "tests/checkout/payment.spec.ts". */
  @Column(name = "spec_file", length = 500)
  private String specFile;

  /** Playwright project name: "chromium", "firefox", "webkit", "Mobile Chrome", etc. */
  @Column(name = "browser", length = 100)
  private String browser;

  /**
   * Playwright built-in and user-defined annotations (excluding tags, tia:* and label:*). Each
   * entry is {"type":"fixme"|"slow"|"fail"|"skip"|…, "description":"optional text"}.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "annotations", columnDefinition = "jsonb")
  private List<Map<String, String>> annotations;

  // ── Tier-2 metadata ───────────────────────────────────────────────────────

  @Column(name = "has_screenshot", nullable = false)
  private boolean hasScreenshot;

  @Column(name = "has_video", nullable = false)
  private boolean hasVideo;

  /** Playwright worker slot index (0-based). Null when not reported. */
  @Column(name = "worker_index")
  private Integer workerIndex;

  // ── TIA metadata ──────────────────────────────────────────────────────────

  /**
   * Source files exercised by this test (declared via tia.covers()). Used by Test Impact Analysis
   * to select tests affected by a changeset.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "covered_files", columnDefinition = "jsonb")
  private List<String> coveredFiles;

  /** Logical components exercised (declared via tia.component()). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "covered_components", columnDefinition = "jsonb")
  private List<String> coveredComponents;

  /** HTTP/UI routes exercised (declared via tia.route()). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "covered_routes", columnDefinition = "jsonb")
  private List<String> coveredRoutes;

  /**
   * Arbitrary key-value labels (owner, jira ticket, team, etc.). Each entry is
   * {"key":"owner","value":"payments-team"}.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "labels", columnDefinition = "jsonb")
  private List<Map<String, String>> labels;

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
    this.traceStorePath = b.traceStorePath;
    this.specFile = b.specFile;
    this.browser = b.browser;
    this.annotations = b.annotations;
    this.hasScreenshot = b.hasScreenshot;
    this.hasVideo = b.hasVideo;
    this.workerIndex = b.workerIndex;
    this.coveredFiles = b.coveredFiles;
    this.coveredComponents = b.coveredComponents;
    this.coveredRoutes = b.coveredRoutes;
    this.labels = b.labels;
  }

  public static Builder builder() {
    return new Builder();
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public TestExecution getExecution() {
    return execution;
  }

  public String getTestId() {
    return testId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public List<String> getTags() {
    return tags;
  }

  public TestStatus getStatus() {
    return status;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public String getTraceStorePath() {
    return traceStorePath;
  }

  public void setTraceStorePath(String v) {
    this.traceStorePath = v;
  }

  public void setStatus(TestStatus v) {
    this.status = v;
  }

  public void setDurationMs(Long v) {
    this.durationMs = v;
  }

  public void setFailureMessage(String v) {
    this.failureMessage = v;
  }

  public void setStackTrace(String v) {
    this.stackTrace = v;
  }

  public void setRetryCount(int v) {
    this.retryCount = v;
  }

  // Tier-1
  public String getSpecFile() {
    return specFile;
  }

  public String getBrowser() {
    return browser;
  }

  public List<Map<String, String>> getAnnotations() {
    return annotations;
  }

  public void setSpecFile(String v) {
    this.specFile = v;
  }

  public void setBrowser(String v) {
    this.browser = v;
  }

  public void setAnnotations(List<Map<String, String>> v) {
    this.annotations = v;
  }

  // Tier-2
  public boolean isHasScreenshot() {
    return hasScreenshot;
  }

  public boolean isHasVideo() {
    return hasVideo;
  }

  public Integer getWorkerIndex() {
    return workerIndex;
  }

  public void setHasScreenshot(boolean v) {
    this.hasScreenshot = v;
  }

  public void setHasVideo(boolean v) {
    this.hasVideo = v;
  }

  public void setWorkerIndex(Integer v) {
    this.workerIndex = v;
  }

  // TIA
  public List<String> getCoveredFiles() {
    return coveredFiles;
  }

  public List<String> getCoveredComponents() {
    return coveredComponents;
  }

  public List<String> getCoveredRoutes() {
    return coveredRoutes;
  }

  public List<Map<String, String>> getLabels() {
    return labels;
  }

  public void setCoveredFiles(List<String> v) {
    this.coveredFiles = v;
  }

  public void setCoveredComponents(List<String> v) {
    this.coveredComponents = v;
  }

  public void setCoveredRoutes(List<String> v) {
    this.coveredRoutes = v;
  }

  public void setLabels(List<Map<String, String>> v) {
    this.labels = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TestCaseResult r)) return false;
    return Objects.equals(id, r.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

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
    private String traceStorePath;
    // Tier-1
    private String specFile;
    private String browser;
    private List<Map<String, String>> annotations;
    // Tier-2
    private boolean hasScreenshot;
    private boolean hasVideo;
    private Integer workerIndex;
    // TIA
    private List<String> coveredFiles;
    private List<String> coveredComponents;
    private List<String> coveredRoutes;
    private List<Map<String, String>> labels;

    public Builder execution(TestExecution v) {
      this.execution = v;
      return this;
    }

    public Builder testId(String v) {
      this.testId = v;
      return this;
    }

    public Builder displayName(String v) {
      this.displayName = v;
      return this;
    }

    public Builder className(String v) {
      this.className = v;
      return this;
    }

    public Builder methodName(String v) {
      this.methodName = v;
      return this;
    }

    public Builder tags(List<String> v) {
      this.tags = v;
      return this;
    }

    public Builder status(TestStatus v) {
      this.status = v;
      return this;
    }

    public Builder durationMs(Long v) {
      this.durationMs = v;
      return this;
    }

    public Builder failureMessage(String v) {
      this.failureMessage = v;
      return this;
    }

    public Builder stackTrace(String v) {
      this.stackTrace = v;
      return this;
    }

    public Builder retryCount(int v) {
      this.retryCount = v;
      return this;
    }

    public Builder traceStorePath(String v) {
      this.traceStorePath = v;
      return this;
    }

    // Tier-1
    public Builder specFile(String v) {
      this.specFile = v;
      return this;
    }

    public Builder browser(String v) {
      this.browser = v;
      return this;
    }

    public Builder annotations(List<Map<String, String>> v) {
      this.annotations = v;
      return this;
    }

    // Tier-2
    public Builder hasScreenshot(boolean v) {
      this.hasScreenshot = v;
      return this;
    }

    public Builder hasVideo(boolean v) {
      this.hasVideo = v;
      return this;
    }

    public Builder workerIndex(Integer v) {
      this.workerIndex = v;
      return this;
    }

    // TIA
    public Builder coveredFiles(List<String> v) {
      this.coveredFiles = v;
      return this;
    }

    public Builder coveredComponents(List<String> v) {
      this.coveredComponents = v;
      return this;
    }

    public Builder coveredRoutes(List<String> v) {
      this.coveredRoutes = v;
      return this;
    }

    public Builder labels(List<Map<String, String>> v) {
      this.labels = v;
      return this;
    }

    public TestCaseResult build() {
      return new TestCaseResult(this);
    }
  }
}
