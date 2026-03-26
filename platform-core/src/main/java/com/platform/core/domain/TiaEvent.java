package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a single Test Impact Analysis query call and its outcome.
 *
 * <p>One row per {@link com.platform.analytics.impact.TestImpactService#analyse} invocation.
 * Used by Grafana dashboards to trend reduction %, risk level distribution,
 * and coverage effectiveness over time.</p>
 */
@Entity
@Table(name = "tia_events")
@EntityListeners(AuditingEntityListener.class)
public class TiaEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    // ── Query inputs ─────────────────────────────────────────────────────────
    @Column(name = "changed_classes", nullable = false)
    private int changedClasses;

    // ── Query outcome ─────────────────────────────────────────────────────────
    @Column(name = "total_tests", nullable = false)
    private int totalTests;

    @Column(name = "selected_tests", nullable = false)
    private int selectedTests;

    @Column(name = "uncovered_classes", nullable = false)
    private int uncoveredClasses;

    @Column(name = "reduction_pct")
    private Double reductionPct;

    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    // ── Context ───────────────────────────────────────────────────────────────
    @Column(name = "branch", length = 500)
    private String branch;

    @Column(name = "triggered_by", nullable = false, length = 50)
    private String triggeredBy = "api";

    @CreatedDate
    @Column(name = "queried_at", nullable = false, updatable = false)
    private Instant queriedAt;

    protected TiaEvent() {}

    public TiaEvent(UUID projectId,
                    int changedClasses, int totalTests, int selectedTests,
                    int uncoveredClasses, Double reductionPct, String riskLevel,
                    String branch, String triggeredBy) {
        this.projectId       = projectId;
        this.changedClasses  = changedClasses;
        this.totalTests      = totalTests;
        this.selectedTests   = selectedTests;
        this.uncoveredClasses = uncoveredClasses;
        this.reductionPct    = reductionPct;
        this.riskLevel       = riskLevel;
        this.branch          = branch;
        this.triggeredBy     = triggeredBy != null ? triggeredBy : "api";
    }

    public UUID    getId()               { return id; }
    public UUID    getProjectId()        { return projectId; }
    public int     getChangedClasses()   { return changedClasses; }
    public int     getTotalTests()       { return totalTests; }
    public int     getSelectedTests()    { return selectedTests; }
    public int     getUncoveredClasses() { return uncoveredClasses; }
    public Double  getReductionPct()     { return reductionPct; }
    public String  getRiskLevel()        { return riskLevel; }
    public String  getBranch()           { return branch; }
    public String  getTriggeredBy()      { return triggeredBy; }
    public Instant getQueriedAt()        { return queriedAt; }
}
