package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestDatasetTest {

  @Test
  void nullEnvBecomesEmptyString() {
    TestDataset d = new TestDataset(UUID.randomUUID(), "logins", null, "{\"u\":\"a\"}");
    assertThat(d.getEnv()).isEmpty();
  }

  @Test
  void updateReplacesFields() {
    TestDataset d = new TestDataset(UUID.randomUUID(), "logins", "qa", "{}");
    d.update("logins2", "prod", "{\"k\":\"v\"}");
    assertThat(d.getName()).isEqualTo("logins2");
    assertThat(d.getEnv()).isEqualTo("prod");
    assertThat(d.getRows()).isEqualTo("{\"k\":\"v\"}");
  }
}
