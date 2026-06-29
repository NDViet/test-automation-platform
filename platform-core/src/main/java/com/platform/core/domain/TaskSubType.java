package com.platform.core.domain;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * Catalog of sub-types allowed per task type (e.g. GENERATE_TEST_CASES → FUNCTIONAL /
 * NON_FUNCTIONAL). Tasks with no meaningful sub-typing have a single {@code DEFAULT} entry. The
 * {@code isDefault} row is selected when the user doesn't pick a sub-type.
 */
@Entity
@Table(name = "task_sub_types")
public class TaskSubType {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** AgentTaskType name. */
  @Column(name = "task_type", nullable = false, length = 60)
  private String taskType;

  @Column(name = "key", nullable = false, length = 40)
  private String key;

  @Column(name = "label", nullable = false, length = 100)
  private String label;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault = false;

  protected TaskSubType() {}

  public TaskSubType(String taskType, String key, String label, boolean isDefault) {
    this.taskType = taskType;
    this.key = key;
    this.label = label;
    this.isDefault = isDefault;
  }

  public UUID getId() {
    return id;
  }

  public String getTaskType() {
    return taskType;
  }

  public String getKey() {
    return key;
  }

  public String getLabel() {
    return label;
  }

  public boolean isDefault() {
    return isDefault;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TaskSubType t)) return false;
    return Objects.equals(id, t.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
