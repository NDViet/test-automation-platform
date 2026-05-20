package com.platform.agent.contract;

import com.platform.common.agent.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the NodeResult factory contract:
 * correct status flags, non-null invariants, and token accounting.
 */
class NodeResultContractTest {

    private final UUID sid = AgentGridFixtures.SESSION_ID;
    private final UUID wid = AgentGridFixtures.WORKFLOW_ID;
    private final TokenUsage usage = AgentGridFixtures.tokenUsage(500, 200);

    @Test
    void completed_hasCorrectStatus() {
        NodeResult result = NodeResult.completed(sid, wid,
                NodeType.TEST_GEN, AgentTaskType.GENERATE_AUTOMATED_TESTS,
                ArtifactManifest.empty(), "done", usage);

        assertThat(result.status()).isEqualTo(NodeResultStatus.COMPLETED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasFailed()).isFalse();
        assertThat(result.needsReview()).isFalse();
        assertThat(result.checkpointId()).isNull();
        assertThat(result.errorCode()).isNull();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    void awaitingReview_hasCorrectStatus() {
        NodeResult result = NodeResult.awaitingReview(sid, wid,
                NodeType.TEST_GEN, AgentTaskType.GENERATE_AUTOMATED_TESTS,
                ArtifactManifest.empty(), "review this", "chk-001", usage);

        assertThat(result.status()).isEqualTo(NodeResultStatus.AWAITING_REVIEW);
        assertThat(result.needsReview()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasFailed()).isFalse();
        assertThat(result.checkpointId()).isEqualTo("chk-001");
    }

    @Test
    void failed_hasCorrectStatus() {
        NodeResult result = NodeResult.failed(sid, wid,
                NodeType.HEALING, AgentTaskType.PROPOSE_HEAL_FIX,
                "MISSING_API_KEY", "API key not configured", usage);

        assertThat(result.status()).isEqualTo(NodeResultStatus.FAILED);
        assertThat(result.hasFailed()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("MISSING_API_KEY");
        assertThat(result.errorMessage()).isEqualTo("API key not configured");
        assertThat(result.checkpointId()).isNull();
        assertThat(result.summary()).isNull();
    }

    @Test
    void tokenUsage_accumulatesCorrectly() {
        TokenUsage a = new TokenUsage(100, 50, 200, 300, BigDecimal.valueOf(0.05));
        TokenUsage b = new TokenUsage(200, 0,  100, 150, BigDecimal.valueOf(0.03));
        TokenUsage sum = a.add(b);

        assertThat(sum.inputFresh()).isEqualTo(300);
        assertThat(sum.inputCacheWrite()).isEqualTo(50);
        assertThat(sum.inputCacheRead()).isEqualTo(300);
        assertThat(sum.outputTokens()).isEqualTo(450);
        assertThat(sum.totalInputTokens()).isEqualTo(650); // 300+50+300
        assertThat(sum.effectiveCostCents()).isEqualByComparingTo(BigDecimal.valueOf(0.08));
    }

    @Test
    void nullArtifacts_defaultsToEmpty() {
        NodeResult result = new NodeResult(sid, wid,
                NodeType.INSIGHT, AgentTaskType.GENERATE_NIGHTLY_DIGEST,
                NodeResultStatus.COMPLETED,
                null,   // null artifacts → normalized to empty
                "ok", null, usage, null, null, null, null);

        assertThat(result.artifacts()).isNotNull();
        assertThat(result.artifacts().artifacts()).isEmpty();
    }

    @Test
    void nullWarnings_defaultsToEmpty() {
        NodeResult result = NodeResult.completed(sid, wid,
                NodeType.ANALYSIS, AgentTaskType.ANALYZE_PR_DIFF,
                ArtifactManifest.empty(), "ok", usage);
        assertThat(result.warnings()).isEmpty();
    }
}
