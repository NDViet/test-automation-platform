package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImpactAnalysisTest {

  @Test
  void customPromptDefaultsNullAndRoundTrips() {
    ImpactAnalysis a = new ImpactAnalysis();
    assertThat(a.getCustomPrompt()).isNull();
    a.setCustomPrompt("Focus on the payment checkout flow.");
    assertThat(a.getCustomPrompt()).isEqualTo("Focus on the payment checkout flow.");
  }
}
