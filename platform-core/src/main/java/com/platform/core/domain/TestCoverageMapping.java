package com.platform.core.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Records which production class (or module) a test case covers.
 * Populated during ingestion from {@code @AffectedBy} annotations (Java) or
 * {@code coveredModules} reporter option (TypeScript/JS).
 */
@Entity
@Table(name = "test_coverage_mappings",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_coverage",
               columnNames = {"project_id", "test_case_id", "class_name"}))
public class TestCoverageMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "test_case_id", nullable = false, length = 500)
    private String testCaseId;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "method_name")
    private String methodName;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected TestCoverageMapping() {}

    public TestCoverageMapping(UUID projectId, String testCaseId, String className, String methodName) {
        this.projectId   = projectId;
        this.testCaseId  = testCaseId;
        this.className   = className;
        this.methodName  = methodName;
        this.lastSeenAt  = Instant.now();
    }

    public UUID getId()          { return id; }
    public UUID getProjectId()   { return projectId; }
    public String getTestCaseId(){ return testCaseId; }
    public String getClassName() { return className; }
    public String getMethodName(){ return methodName; }
    public Instant getLastSeenAt(){ return lastSeenAt; }

    public void touch() { this.lastSeenAt = Instant.now(); }
}
