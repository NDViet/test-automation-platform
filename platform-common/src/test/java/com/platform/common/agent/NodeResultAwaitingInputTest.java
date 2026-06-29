package com.platform.common.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NodeResultAwaitingInputTest {

  @Test
  void awaitingInputCarriesQuestionsAndCheckpointAndFlags() {
    UUID session = UUID.randomUUID();
    UUID workflow = UUID.randomUUID();
    String questionsJson = "[{\"id\":\"q1\",\"question\":\"Which browsers?\"}]";

    NodeResult r =
        NodeResult.awaitingInput(
            session,
            workflow,
            NodeType.TEST_GENERATION,
            AgentTaskType.GENERATE_TEST_CASES,
            questionsJson,
            "chk-123",
            TokenUsage.zero());

    assertThat(r.status()).isEqualTo(NodeResultStatus.AWAITING_INPUT);
    assertThat(r.summary()).isEqualTo(questionsJson);
    assertThat(r.checkpointId()).isEqualTo("chk-123");
    assertThat(r.needsInput()).isTrue();
    assertThat(r.needsReview()).isFalse();
    assertThat(r.hasFailed()).isFalse();
  }
}
