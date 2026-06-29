package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One round of clarifying questions the agent asked during a generation workflow, plus the user's
 * answers once supplied. Rounds increment per pause; the conversation resumes from the workflow's
 * checkpoint after each round is answered.
 */
@Entity
@Table(name = "generation_clarifications")
public class GenerationClarification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "workflow_id", nullable = false)
  private UUID workflowId;

  @Column(name = "round", nullable = false)
  private int round;

  /** Checkpoint locating the saved conversation to resume once this round is answered. */
  @Column(name = "checkpoint_id", length = 200)
  private String checkpointId;

  /** JSON array of questions: [{id, question, kind, options?}]. */
  @Column(name = "questions", nullable = false, columnDefinition = "TEXT")
  private String questionsJson;

  /** JSON array of answers: [{id, answer}] — null until answered. */
  @Column(name = "answers", columnDefinition = "TEXT")
  private String answersJson;

  /** PENDING | ANSWERED | SKIPPED */
  @Column(name = "status", nullable = false, length = 12)
  private String status = "PENDING";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "answered_at")
  private Instant answeredAt;

  protected GenerationClarification() {}

  public GenerationClarification(
      UUID workflowId, int round, String questionsJson, String checkpointId) {
    this.workflowId = workflowId;
    this.round = round;
    this.questionsJson = questionsJson;
    this.checkpointId = checkpointId;
  }

  public void markAnswered(String answersJson) {
    this.answersJson = answersJson;
    this.status = "ANSWERED";
    this.answeredAt = Instant.now();
  }

  public void markSkipped() {
    this.status = "SKIPPED";
    this.answeredAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkflowId() {
    return workflowId;
  }

  public int getRound() {
    return round;
  }

  public String getCheckpointId() {
    return checkpointId;
  }

  public String getQuestionsJson() {
    return questionsJson;
  }

  public String getAnswersJson() {
    return answersJson;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getAnsweredAt() {
    return answeredAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GenerationClarification c)) return false;
    return Objects.equals(id, c.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
