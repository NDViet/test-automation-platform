package com.platform.core.domain;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/** The concrete property value assigned to one parametrized test-case execution. */
@Entity
@Table(name = "test_execution_properties")
public class TestExecutionProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "test_case_execution_id", nullable = false)
    private UUID testCaseExecutionId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "value", nullable = false, length = 500)
    private String value;

    protected TestExecutionProperty() {}

    public TestExecutionProperty(UUID testCaseExecutionId, String name, String value) {
        this.testCaseExecutionId = testCaseExecutionId;
        this.name                = name;
        this.value               = value;
    }

    public UUID getId()                  { return id; }
    public UUID getTestCaseExecutionId() { return testCaseExecutionId; }
    public String getName()              { return name; }
    public String getValue()             { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestExecutionProperty p)) return false;
        return Objects.equals(id, p.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
