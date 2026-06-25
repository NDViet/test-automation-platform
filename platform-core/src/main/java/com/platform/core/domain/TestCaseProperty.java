package com.platform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A parametrization property on a test case. Multiple rows with the same {@code name} but different
 * {@code value}s define the axis values that the property matrix expands into per-combination
 * executions.
 */
@Entity
@Table(name = "test_case_properties")
public class TestCaseProperty {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "test_case_id", nullable = false)
  private UUID testCaseId;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "value", nullable = false, length = 500)
  private String value;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected TestCaseProperty() {}

  public TestCaseProperty(UUID testCaseId, String name, String value) {
    this.testCaseId = testCaseId;
    this.name = name;
    this.value = value;
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

  public String getValue() {
    return value;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TestCaseProperty p)) return false;
    return Objects.equals(id, p.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
