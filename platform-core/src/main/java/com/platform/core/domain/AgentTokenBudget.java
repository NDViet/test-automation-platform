package com.platform.core.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "agent_token_budgets")
public class AgentTokenBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** "YYYY-MM" */
    @Column(name = "budget_month", nullable = false, length = 7)
    private String budgetMonth;

    @Column(name = "max_input_tokens", nullable = false)
    private long maxInputTokens = 10_000_000L;

    @Column(name = "used_input_tokens", nullable = false)
    private long usedInputTokens = 0L;

    @Column(name = "max_output_tokens", nullable = false)
    private long maxOutputTokens = 2_000_000L;

    @Column(name = "used_output_tokens", nullable = false)
    private long usedOutputTokens = 0L;

    @Column(name = "max_cost_cents", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxCostCents = new BigDecimal("5000.00");

    @Column(name = "used_cost_cents", nullable = false, precision = 10, scale = 2)
    private BigDecimal usedCostCents = BigDecimal.ZERO;

    /** true = block when any limit exceeded */
    @Column(name = "hard_limit", nullable = false)
    private boolean hardLimit = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AgentTokenBudget() {}

    public AgentTokenBudget(UUID projectId, String budgetMonth) {
        this.projectId   = projectId;
        this.budgetMonth = budgetMonth;
    }

    public void addUsage(long inputTokens, long outputTokens, BigDecimal costCents) {
        this.usedInputTokens  += inputTokens;
        this.usedOutputTokens += outputTokens;
        this.usedCostCents    = this.usedCostCents.add(costCents != null ? costCents : BigDecimal.ZERO);
        this.updatedAt        = Instant.now();
    }

    public boolean isInputExceeded()  { return usedInputTokens  >= maxInputTokens; }
    public boolean isOutputExceeded() { return usedOutputTokens >= maxOutputTokens; }
    public boolean isCostExceeded()   { return usedCostCents.compareTo(maxCostCents) >= 0; }
    public boolean isAnyLimitExceeded() {
        return isInputExceeded() || isOutputExceeded() || isCostExceeded();
    }

    public UUID getId()               { return id; }
    public UUID getProjectId()        { return projectId; }
    public String getBudgetMonth()    { return budgetMonth; }
    public long getMaxInputTokens()   { return maxInputTokens; }
    public long getUsedInputTokens()  { return usedInputTokens; }
    public long getMaxOutputTokens()  { return maxOutputTokens; }
    public long getUsedOutputTokens() { return usedOutputTokens; }
    public BigDecimal getMaxCostCents()  { return maxCostCents; }
    public BigDecimal getUsedCostCents() { return usedCostCents; }
    public boolean isHardLimit()      { return hardLimit; }
    public Instant getUpdatedAt()     { return updatedAt; }

    public void setHardLimit(boolean v)            { this.hardLimit = v; this.updatedAt = Instant.now(); }
    public void setMaxInputTokens(long v)          { this.maxInputTokens = v; this.updatedAt = Instant.now(); }
    public void setMaxOutputTokens(long v)         { this.maxOutputTokens = v; this.updatedAt = Instant.now(); }
    public void setMaxCostCents(BigDecimal v)      { this.maxCostCents = v; this.updatedAt = Instant.now(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentTokenBudget b)) return false;
        return Objects.equals(id, b.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
