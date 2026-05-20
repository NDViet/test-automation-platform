package com.platform.agent.hub.graph;

import com.platform.core.domain.AgentTokenBudget;
import com.platform.core.repository.AgentTokenBudgetRepository;
import com.platform.common.agent.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Enforces monthly per-project token budgets.
 * Hard-limit projects are blocked when any budget threshold is exceeded.
 * Soft-limit projects log a warning but proceed.
 */
@Service
public class TokenBudgetGuard {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetGuard.class);

    private final AgentTokenBudgetRepository budgetRepo;

    public TokenBudgetGuard(AgentTokenBudgetRepository budgetRepo) {
        this.budgetRepo = budgetRepo;
    }

    /**
     * Returns true if the project is within budget for the current month.
     * For hard-limit projects, logs an error and returns false when exceeded.
     */
    public boolean isWithinBudget(UUID projectId) {
        String month = currentMonth();
        return budgetRepo.findByProjectIdAndBudgetMonth(projectId, month)
                .map(budget -> {
                    if (!budget.isHardLimit()) return true;
                    if (budget.isAnyLimitExceeded()) {
                        log.error("Hard budget exceeded for project {} in {}: " +
                                  "input={}/{}, output={}/{}, cost={}/{}",
                                projectId, month,
                                budget.getUsedInputTokens(),  budget.getMaxInputTokens(),
                                budget.getUsedOutputTokens(), budget.getMaxOutputTokens(),
                                budget.getUsedCostCents(),    budget.getMaxCostCents());
                        return false;
                    }
                    return true;
                })
                .orElse(true); // no budget record = unlimited
    }

    /**
     * Records token usage after a node completes.
     * Uses an atomic upsert to avoid lost updates under concurrent sessions.
     */
    @Transactional
    public void recordUsage(UUID projectId, TokenUsage usage) {
        if (usage == null || usage.equals(TokenUsage.zero())) return;
        String month = currentMonth();
        BigDecimal cost = usage.effectiveCostCents() != null
                ? usage.effectiveCostCents()
                : BigDecimal.ZERO;
        try {
            budgetRepo.upsertUsage(projectId, month,
                    usage.totalInputTokens(),
                    usage.outputTokens(),
                    cost);

            budgetRepo.findByProjectIdAndBudgetMonth(projectId, month).ifPresent(b -> {
                if (b.isAnyLimitExceeded()) {
                    if (b.isHardLimit()) {
                        log.error("Project {} crossed hard budget threshold in {}", projectId, month);
                    } else {
                        log.warn("Project {} soft budget threshold exceeded in {}", projectId, month);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to record token usage for project {}: {}", projectId, e.getMessage());
        }
    }

    private String currentMonth() {
        return YearMonth.now().toString(); // "YYYY-MM"
    }
}
