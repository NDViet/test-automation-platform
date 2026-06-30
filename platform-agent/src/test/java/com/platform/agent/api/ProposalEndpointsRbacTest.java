package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Locks in the capability gate on the proposal-review endpoints of {@link
 * TestCaseGenerationController}: curating AI proposals requires {@code OPERATE_QUALITY} on the
 * project (spec F6). Reflection-based; enforcement is covered in platform-security.
 */
class ProposalEndpointsRbacTest {

  private static RequireCapability gate(String method) {
    Method m =
        Arrays.stream(TestCaseGenerationController.class.getDeclaredMethods())
            .filter(x -> x.getName().equals(method))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no method " + method));
    RequireCapability rc = m.getAnnotation(RequireCapability.class);
    assertThat(rc).as("%s must be gated", method).isNotNull();
    return rc;
  }

  @Test
  void proposalEndpointsRequireOperateScopedToProject() {
    for (String m :
        new String[] {
          "listProposals",
          "acceptProposal",
          "acceptAllProposals",
          "rejectProposal",
          "refineProposal",
          "refineAllProposals"
        }) {
      RequireCapability rc = gate(m);
      assertThat(rc.value()).as("%s capability", m).isEqualTo(Capability.OPERATE_QUALITY);
      assertThat(rc.scope()).as("%s scope", m).isEqualTo("projectId");
    }
  }
}
