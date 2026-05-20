package com.platform.core.domain;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

/** A single step in a manual test case (action + expected result). */
@Entity
@Table(name = "test_case_steps")
public class TestCaseStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "action", nullable = false, columnDefinition = "TEXT")
    private String action;

    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    protected TestCaseStep() {}

    public TestCaseStep(UUID testCaseId, int stepNumber, String action,
                        String expectedResult, String notes) {
        this.testCaseId     = testCaseId;
        this.stepNumber     = stepNumber;
        this.action         = action;
        this.expectedResult = expectedResult;
        this.notes          = notes;
    }

    public UUID getId()             { return id; }
    public UUID getTestCaseId()     { return testCaseId; }
    public int getStepNumber()      { return stepNumber; }
    public String getAction()       { return action; }
    public String getExpectedResult() { return expectedResult; }
    public String getNotes()        { return notes; }

    public void setAction(String action)               { this.action = action; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    public void setNotes(String notes)                 { this.notes = notes; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestCaseStep s)) return false;
        return Objects.equals(id, s.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
