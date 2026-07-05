package com.platform.core.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureSignatureTest {

  @Test
  void sameFailureDifferingOnlyByVolatileTokensHashesEqually() {
    String a =
        FailureSignature.compute(
            "NullPointerException at 0x7f3a for req 3f2a8b1c-1111-2222-3333-444455556666 at"
                + " 2026-07-04T10:00:00Z",
            "at com.acme.LoginTest.login(LoginTest.java:42)\n\tat org.junit.Foo.run(Foo.java:99)");
    String b =
        FailureSignature.compute(
            "NullPointerException at 0x9c1b for req 8b1c3f2a-9999-8888-7777-666655554444 at"
                + " 2026-07-04T11:30:05Z",
            "at com.acme.LoginTest.login(LoginTest.java:87)\n\tat org.junit.Foo.run(Foo.java:12)");
    assertThat(a).isEqualTo(b).hasSize(64);
  }

  @Test
  void differentExceptionHashesDifferently() {
    String npe = FailureSignature.compute("NullPointerException in checkout", "at a.B.c(B.java:1)");
    String ae =
        FailureSignature.compute(
            "AssertionError expected true but was false", "at a.B.c(B.java:1)");
    assertThat(npe).isNotEqualTo(ae);
  }

  @Test
  void isNullSafe() {
    assertThat(FailureSignature.compute(null, null)).hasSize(64);
  }
}
