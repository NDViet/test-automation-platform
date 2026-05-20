package com.platform.agent.hub.graph;

import com.platform.agent.contract.AgentGridFixtures;
import com.platform.common.agent.TokenUsage;
import com.platform.core.domain.AgentTokenBudget;
import com.platform.core.repository.AgentTokenBudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TokenBudgetGuard.
 * Verifies budget enforcement contract: hard-limit blocks, soft-limit warns, no-record allows.
 */
@ExtendWith(MockitoExtension.class)
class TokenBudgetGuardTest {

    @Mock private AgentTokenBudgetRepository budgetRepo;

    private TokenBudgetGuard guard;
    private final UUID projectId = AgentGridFixtures.PROJECT_ID;
    private final String currentMonth = YearMonth.now().toString();

    @BeforeEach
    void setUp() {
        guard = new TokenBudgetGuard(budgetRepo);
    }

    // -------------------------------------------------------------------------
    // isWithinBudget
    // -------------------------------------------------------------------------

    @Test
    void isWithinBudget_noBudgetRecord_returnsTrue() {
        when(budgetRepo.findByProjectIdAndBudgetMonth(projectId, currentMonth))
                .thenReturn(Optional.empty());

        assertThat(guard.isWithinBudget(projectId)).isTrue();
    }

    @Test
    void isWithinBudget_hardLimitNotExceeded_returnsTrue() {
        AgentTokenBudget budget = makeBudget(true, 5_000_000L, 1_000_000L, "3000.00");
        when(budgetRepo.findByProjectIdAndBudgetMonth(projectId, currentMonth))
                .thenReturn(Optional.of(budget));

        assertThat(guard.isWithinBudget(projectId)).isTrue();
    }

    @Test
    void isWithinBudget_hardLimitExceeded_returnsFalse() {
        AgentTokenBudget budget = makeBudget(true, 10_000_000L, 10_000_000L, "5001.00");
        // max = 10M input, 2M output, $50 → used exceeds $50 cost
        when(budgetRepo.findByProjectIdAndBudgetMonth(projectId, currentMonth))
                .thenReturn(Optional.of(budget));

        assertThat(guard.isWithinBudget(projectId)).isFalse();
    }

    @Test
    void isWithinBudget_softLimitExceeded_returnsTrue() {
        // Soft limit (hardLimit=false) → always true regardless of usage
        AgentTokenBudget budget = makeBudget(false, 10_000_001L, 2_000_001L, "5001.00");
        when(budgetRepo.findByProjectIdAndBudgetMonth(projectId, currentMonth))
                .thenReturn(Optional.of(budget));

        assertThat(guard.isWithinBudget(projectId)).isTrue();
    }

    @Test
    void isWithinBudget_inputTokensExceededHard_returnsFalse() {
        AgentTokenBudget budget = makeBudgetByTokens(true,
                10_000_001L, 10_000_000L, // used > max
                0L, 2_000_000L,
                "0.00", "5000.00");
        when(budgetRepo.findByProjectIdAndBudgetMonth(projectId, currentMonth))
                .thenReturn(Optional.of(budget));

        assertThat(guard.isWithinBudget(projectId)).isFalse();
    }

    // -------------------------------------------------------------------------
    // recordUsage
    // -------------------------------------------------------------------------

    @Test
    void recordUsage_callsUpsertWithCorrectValues() {
        TokenUsage usage = new TokenUsage(1000, 200, 500, 300, BigDecimal.valueOf(0.85));
        // totalInputTokens = 1000 + 200 + 500 = 1700
        doNothing().when(budgetRepo).upsertUsage(any(), any(), anyLong(), anyLong(), any());
        when(budgetRepo.findByProjectIdAndBudgetMonth(any(), any())).thenReturn(Optional.empty());

        guard.recordUsage(projectId, usage);

        verify(budgetRepo).upsertUsage(
                eq(projectId),
                eq(currentMonth),
                eq(1700L),       // totalInputTokens
                eq(300L),        // outputTokens
                eq(BigDecimal.valueOf(0.85)));
    }

    @Test
    void recordUsage_zeroUsage_doesNotCallUpsert() {
        guard.recordUsage(projectId, TokenUsage.zero());
        verify(budgetRepo, never()).upsertUsage(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void recordUsage_nullUsage_doesNotCallUpsert() {
        guard.recordUsage(projectId, null);
        verify(budgetRepo, never()).upsertUsage(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void recordUsage_nullCostCents_usesZero() {
        TokenUsage usage = new TokenUsage(100, 0, 0, 50, null);
        doNothing().when(budgetRepo).upsertUsage(any(), any(), anyLong(), anyLong(), any());
        when(budgetRepo.findByProjectIdAndBudgetMonth(any(), any())).thenReturn(Optional.empty());

        guard.recordUsage(projectId, usage);

        verify(budgetRepo).upsertUsage(any(), any(), eq(100L), eq(50L), eq(BigDecimal.ZERO));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Budget with cost exceeded (used > max). */
    private AgentTokenBudget makeBudget(boolean hardLimit, long usedInput, long usedOutput, String usedCostCents) {
        AgentTokenBudget b = new AgentTokenBudget(projectId, currentMonth);
        setField(b, "hardLimit",       hardLimit);
        setField(b, "usedInputTokens", usedInput);
        setField(b, "usedOutputTokens",usedOutput);
        setField(b, "usedCostCents",   new BigDecimal(usedCostCents));
        // defaults: maxInput=10M, maxOutput=2M, maxCost=$50
        return b;
    }

    private AgentTokenBudget makeBudgetByTokens(boolean hardLimit,
            long usedInput, long maxInput,
            long usedOutput, long maxOutput,
            String usedCost, String maxCost) {
        AgentTokenBudget b = new AgentTokenBudget(projectId, currentMonth);
        setField(b, "hardLimit",        hardLimit);
        setField(b, "usedInputTokens",  usedInput);
        setField(b, "maxInputTokens",   maxInput);
        setField(b, "usedOutputTokens", usedOutput);
        setField(b, "maxOutputTokens",  maxOutput);
        setField(b, "usedCostCents",    new BigDecimal(usedCost));
        setField(b, "maxCostCents",     new BigDecimal(maxCost));
        return b;
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set field " + fieldName, e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new RuntimeException("Field not found: " + name);
    }
}
