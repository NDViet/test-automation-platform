package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestCaseExecutionAssigneeTest {

  @Test
  void assignThenUnassign() {
    TestCaseExecution e = new TestCaseExecution(UUID.randomUUID(), UUID.randomUUID());
    assertThat(e.getAssigneeUserId()).isNull();

    UUID user = UUID.randomUUID();
    e.assignTo(user);
    assertThat(e.getAssigneeUserId()).isEqualTo(user);

    e.assignTo(null);
    assertThat(e.getAssigneeUserId()).isNull();
  }
}
