package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A free-form tag on a curated test case (Kiwi-style). */
@Entity
@Table(
    name = "test_case_tags",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_tct_case_name",
            columnNames = {"test_case_id", "name"}))
public class TestCaseTag {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "test_case_id", nullable = false)
  private UUID testCaseId;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected TestCaseTag() {}

  public TestCaseTag(UUID testCaseId, String name) {
    this.testCaseId = testCaseId;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTestCaseId() {
    return testCaseId;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TestCaseTag t)) return false;
    return Objects.equals(id, t.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
