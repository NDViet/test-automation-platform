package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An AI-generated test case held in staging, pending human review. Generation writes these (status
 * {@code PROPOSED}) instead of creating catalog rows; the user reviews them and, per case, accepts
 * (→ a DRAFT {@link PlatformTestCase}), rejects, or refines (the AI revises it in place). A
 * proposal is 1:N under a generation workflow, ordered by {@code ordinal}.
 */
@Entity
@Table(name = "generated_test_case_proposals")
public class GeneratedTestCaseProposal {

  public static final String PROPOSED = "PROPOSED";
  public static final String ACCEPTED = "ACCEPTED";
  public static final String REJECTED = "REJECTED";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "workflow_id", nullable = false)
  private UUID workflowId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "ordinal", nullable = false)
  private int ordinal;

  @Column(name = "title", nullable = false, columnDefinition = "TEXT")
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "preconditions", columnDefinition = "TEXT")
  private String preconditions;

  @Column(name = "expected_result", columnDefinition = "TEXT")
  private String expectedResult;

  @Column(name = "priority", length = 20)
  private String priority;

  /** Raw value the model returned (may not be a valid UUID); parsed on accept. */
  @Column(name = "source_requirement_id", length = 200)
  private String sourceRequirementId;

  /** Ordered steps {@code [{action, expectedResult, notes}]} as JSON. */
  @Column(name = "steps_json", nullable = false, columnDefinition = "TEXT")
  private String stepsJson = "[]";

  @Column(name = "status", nullable = false, length = 20)
  private String status = PROPOSED;

  /** Set on accept: the {@link PlatformTestCase} created from this proposal. */
  @Column(name = "accepted_test_case_id")
  private UUID acceptedTestCaseId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected GeneratedTestCaseProposal() {}

  public GeneratedTestCaseProposal(
      UUID workflowId,
      UUID projectId,
      int ordinal,
      String title,
      String description,
      String preconditions,
      String expectedResult,
      String priority,
      String sourceRequirementId,
      String stepsJson) {
    this.workflowId = workflowId;
    this.projectId = projectId;
    this.ordinal = ordinal;
    this.title = title;
    this.description = description;
    this.preconditions = preconditions;
    this.expectedResult = expectedResult;
    this.priority = priority;
    this.sourceRequirementId = sourceRequirementId;
    this.stepsJson = stepsJson != null ? stepsJson : "[]";
  }

  public boolean isProposed() {
    return PROPOSED.equals(status);
  }

  /** Mark accepted and link the created catalog test case. */
  public void markAccepted(UUID testCaseId) {
    this.status = ACCEPTED;
    this.acceptedTestCaseId = testCaseId;
    this.updatedAt = Instant.now();
  }

  public void markRejected() {
    this.status = REJECTED;
    this.updatedAt = Instant.now();
  }

  /** Replace the proposal content after an AI refinement (status stays PROPOSED). */
  public void refineContent(
      String title,
      String description,
      String preconditions,
      String expectedResult,
      String priority,
      String sourceRequirementId,
      String stepsJson) {
    this.title = title;
    this.description = description;
    this.preconditions = preconditions;
    this.expectedResult = expectedResult;
    this.priority = priority;
    this.sourceRequirementId = sourceRequirementId;
    if (stepsJson != null) {
      this.stepsJson = stepsJson;
    }
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkflowId() {
    return workflowId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getPreconditions() {
    return preconditions;
  }

  public String getExpectedResult() {
    return expectedResult;
  }

  public String getPriority() {
    return priority;
  }

  public String getSourceRequirementId() {
    return sourceRequirementId;
  }

  public String getStepsJson() {
    return stepsJson;
  }

  public String getStatus() {
    return status;
  }

  public UUID getAcceptedTestCaseId() {
    return acceptedTestCaseId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GeneratedTestCaseProposal p)) return false;
    return Objects.equals(id, p.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
