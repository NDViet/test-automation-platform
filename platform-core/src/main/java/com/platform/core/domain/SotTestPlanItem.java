package com.platform.core.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sot_test_plan_items")
public class SotTestPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirement_ids", columnDefinition = "jsonb")
    private List<UUID> requirementIds = new ArrayList<>();

    /** AUTOMATED | MANUAL | EXPLORATORY */
    @Column(name = "execution_type", nullable = false, length = 20)
    private String executionType = "MANUAL";

    /** MUST_RUN | SHOULD_RUN | NICE_TO_HAVE */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority = "SHOULD_RUN";

    /** PASS | FAIL | BLOCKED | SKIPPED | NOT_RUN */
    @Column(name = "result", nullable = false, length = 20)
    private String result = "NOT_RUN";

    @Column(name = "executed_at")
    private Instant executedAt;

    protected SotTestPlanItem() {}

    public SotTestPlanItem(UUID planId, UUID testCaseId, List<UUID> requirementIds,
                            String executionType, String priority) {
        this.planId         = planId;
        this.testCaseId     = testCaseId;
        this.requirementIds = requirementIds != null ? new ArrayList<>(requirementIds) : new ArrayList<>();
        this.executionType  = executionType != null ? executionType : "MANUAL";
        this.priority       = priority != null ? priority : "SHOULD_RUN";
    }

    public void recordResult(String result) {
        this.result     = result;
        this.executedAt = Instant.now();
    }

    public UUID getId()                   { return id; }
    public UUID getPlanId()               { return planId; }
    public UUID getTestCaseId()           { return testCaseId; }
    public List<UUID> getRequirementIds() { return requirementIds; }
    public String getExecutionType()      { return executionType; }
    public String getPriority()           { return priority; }
    public String getResult()             { return result; }
    public Instant getExecutedAt()        { return executedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SotTestPlanItem i)) return false;
        return Objects.equals(id, i.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
