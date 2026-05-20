package com.platform.core.repository;

import com.platform.core.domain.AgentTokenBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentTokenBudgetRepository extends JpaRepository<AgentTokenBudget, UUID> {

    Optional<AgentTokenBudget> findByProjectIdAndBudgetMonth(UUID projectId, String budgetMonth);

    @Modifying
    @Query(value = """
            INSERT INTO agent_token_budgets
                (project_id, budget_month, used_input_tokens, used_output_tokens, used_cost_cents, updated_at)
            VALUES (:projectId, :month, :inputTokens, :outputTokens, :costCents, now())
            ON CONFLICT (project_id, budget_month) DO UPDATE SET
                used_input_tokens  = agent_token_budgets.used_input_tokens  + EXCLUDED.used_input_tokens,
                used_output_tokens = agent_token_budgets.used_output_tokens + EXCLUDED.used_output_tokens,
                used_cost_cents    = agent_token_budgets.used_cost_cents    + EXCLUDED.used_cost_cents,
                updated_at         = now()
            """, nativeQuery = true)
    void upsertUsage(@Param("projectId") UUID projectId,
                     @Param("month") String month,
                     @Param("inputTokens") long inputTokens,
                     @Param("outputTokens") long outputTokens,
                     @Param("costCents") BigDecimal costCents);
}
