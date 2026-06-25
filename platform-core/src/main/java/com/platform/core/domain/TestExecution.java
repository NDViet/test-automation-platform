package com.platform.core.domain;

import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestType;
import com.platform.common.enums.TriggerType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "test_executions",
    indexes = {
      @Index(name = "idx_te_project_id", columnList = "project_id"),
      @Index(name = "idx_te_branch", columnList = "branch"),
      @Index(name = "idx_te_executed_at", columnList = "executed_at")
    })
@EntityListeners(AuditingEntityListener.class)
public class TestExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "run_id", nullable = false, unique = true, length = 200)
  private String runId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "team_id")
  private Team team;

  @Column(name = "branch", length = 200)
  private String branch;

  @Column(name = "commit_sha", length = 40)
  private String commitSha;

  @Column(name = "environment", length = 50)
  private String environment;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", length = 20)
  private TriggerType triggerType;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_format", nullable = false, length = 30)
  private SourceFormat sourceFormat;

  @Enumerated(EnumType.STRING)
  @Column(name = "test_type", nullable = false, length = 20)
  private TestType testType = TestType.FUNCTIONAL;

  @Column(name = "ci_provider", length = 30)
  private String ciProvider;

  @Column(name = "ci_run_url", length = 1000)
  private String ciRunUrl;

  @Column(name = "total_tests", nullable = false)
  private int totalTests;

  @Column(name = "passed", nullable = false)
  private int passed;

  @Column(name = "failed", nullable = false)
  private int failed;

  @Column(name = "skipped", nullable = false)
  private int skipped;

  @Column(name = "broken", nullable = false)
  private int broken;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "execution_mode", nullable = false, length = 20)
  private String executionMode = "UNKNOWN";

  @Column(name = "parallelism", nullable = false)
  private int parallelism;

  @Column(name = "suite_name", nullable = false, length = 500)
  private String suiteName = "";

  @Column(name = "executed_at", nullable = false)
  private Instant executedAt;

  @CreatedDate
  @Column(name = "ingested_at", nullable = false, updatable = false)
  private Instant ingestedAt;

  @Column(name = "area_slug", length = 200)
  private String areaSlug;

  @Column(name = "iteration_path", length = 500)
  private String iterationPath;

  /** RUNNING while a streaming run is in progress; COMPLETED once finishRun() is called. */
  @Column(name = "status", nullable = false, length = 20)
  private String status = "COMPLETED";

  @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TestCaseResult> testCaseResults = new ArrayList<>();

  protected TestExecution() {}

  private TestExecution(Builder b) {
    this.runId = b.runId;
    this.project = b.project;
    this.team = b.team;
    this.areaSlug = b.areaSlug;
    this.iterationPath = b.iterationPath;
    this.branch = b.branch;
    this.commitSha = b.commitSha;
    this.environment = b.environment;
    this.triggerType = b.triggerType;
    this.sourceFormat = b.sourceFormat;
    this.testType = b.testType != null ? b.testType : TestType.from(b.sourceFormat);
    this.ciProvider = b.ciProvider;
    this.ciRunUrl = b.ciRunUrl;
    this.totalTests = b.totalTests;
    this.passed = b.passed;
    this.failed = b.failed;
    this.skipped = b.skipped;
    this.broken = b.broken;
    this.durationMs = b.durationMs;
    this.executionMode = b.executionMode != null ? b.executionMode : "UNKNOWN";
    this.parallelism = b.parallelism;
    this.suiteName = b.suiteName != null ? b.suiteName : "";
    this.executedAt = b.executedAt;
    this.status = b.status != null ? b.status : "COMPLETED";
  }

  public static Builder builder() {
    return new Builder();
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public Project getProject() {
    return project;
  }

  public Team getTeam() {
    return team;
  }

  public String getAreaSlug() {
    return areaSlug;
  }

  public String getIterationPath() {
    return iterationPath;
  }

  public String getBranch() {
    return branch;
  }

  public String getCommitSha() {
    return commitSha;
  }

  public String getEnvironment() {
    return environment;
  }

  public TriggerType getTriggerType() {
    return triggerType;
  }

  public SourceFormat getSourceFormat() {
    return sourceFormat;
  }

  public TestType getTestType() {
    return testType;
  }

  public String getCiProvider() {
    return ciProvider;
  }

  public String getCiRunUrl() {
    return ciRunUrl;
  }

  public int getTotalTests() {
    return totalTests;
  }

  public int getPassed() {
    return passed;
  }

  public int getFailed() {
    return failed;
  }

  public int getSkipped() {
    return skipped;
  }

  public int getBroken() {
    return broken;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public String getExecutionMode() {
    return executionMode;
  }

  public int getParallelism() {
    return parallelism;
  }

  public String getSuiteName() {
    return suiteName;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public Instant getIngestedAt() {
    return ingestedAt;
  }

  public String getStatus() {
    return status;
  }

  public List<TestCaseResult> getTestCaseResults() {
    return testCaseResults;
  }

  // Setters for fields updated during streaming run finalization and scope linking
  public void setStatus(String v) {
    this.status = v;
  }

  public void setAreaSlug(String v) {
    this.areaSlug = v;
  }

  public void setIterationPath(String v) {
    this.iterationPath = v;
  }

  public void setTotalTests(int v) {
    this.totalTests = v;
  }

  public void setPassed(int v) {
    this.passed = v;
  }

  public void setFailed(int v) {
    this.failed = v;
  }

  public void setSkipped(int v) {
    this.skipped = v;
  }

  public void setBroken(int v) {
    this.broken = v;
  }

  public void setDurationMs(Long v) {
    this.durationMs = v;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TestExecution e)) return false;
    return Objects.equals(id, e.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  public static class Builder {
    private String runId;
    private Project project;
    private Team team;
    private String areaSlug;
    private String iterationPath;
    private String branch;
    private String commitSha;
    private String environment = "unknown";
    private TriggerType triggerType;
    private SourceFormat sourceFormat;
    private TestType testType;
    private String ciProvider;
    private String ciRunUrl;
    private int totalTests;
    private int passed;
    private int failed;
    private int skipped;
    private int broken;
    private Long durationMs;
    private String executionMode = "UNKNOWN";
    private int parallelism;
    private String suiteName = "";
    private Instant executedAt;
    private String status;

    public Builder runId(String v) {
      this.runId = v;
      return this;
    }

    public Builder project(Project v) {
      this.project = v;
      return this;
    }

    public Builder team(Team v) {
      this.team = v;
      return this;
    }

    public Builder areaSlug(String v) {
      this.areaSlug = v;
      return this;
    }

    public Builder iterationPath(String v) {
      this.iterationPath = v;
      return this;
    }

    public Builder branch(String v) {
      this.branch = v;
      return this;
    }

    public Builder commitSha(String v) {
      this.commitSha = v;
      return this;
    }

    public Builder environment(String v) {
      this.environment = v;
      return this;
    }

    public Builder triggerType(TriggerType v) {
      this.triggerType = v;
      return this;
    }

    public Builder sourceFormat(SourceFormat v) {
      this.sourceFormat = v;
      return this;
    }

    public Builder testType(TestType v) {
      this.testType = v;
      return this;
    }

    public Builder ciProvider(String v) {
      this.ciProvider = v;
      return this;
    }

    public Builder ciRunUrl(String v) {
      this.ciRunUrl = v;
      return this;
    }

    public Builder totalTests(int v) {
      this.totalTests = v;
      return this;
    }

    public Builder passed(int v) {
      this.passed = v;
      return this;
    }

    public Builder failed(int v) {
      this.failed = v;
      return this;
    }

    public Builder skipped(int v) {
      this.skipped = v;
      return this;
    }

    public Builder broken(int v) {
      this.broken = v;
      return this;
    }

    public Builder durationMs(Long v) {
      this.durationMs = v;
      return this;
    }

    public Builder executionMode(String v) {
      this.executionMode = v;
      return this;
    }

    public Builder parallelism(int v) {
      this.parallelism = v;
      return this;
    }

    public Builder suiteName(String v) {
      this.suiteName = v;
      return this;
    }

    public Builder executedAt(Instant v) {
      this.executedAt = v;
      return this;
    }

    public Builder status(String v) {
      this.status = v;
      return this;
    }

    public TestExecution build() {
      return new TestExecution(this);
    }
  }
}
