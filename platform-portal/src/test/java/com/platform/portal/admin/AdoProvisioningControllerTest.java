package com.platform.portal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.security.jwt.AuthenticatedUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdoProvisioningControllerTest {

  @Mock AdoProvisioningService service;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void delegatesToServiceWithJwtPrincipal() {
    AdoProvisioningController controller = new AdoProvisioningController(service);
    UUID projectId = UUID.randomUUID();
    AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), "root", true);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null));
    var summary = new AdoProvisioningService.ProvisionSummary(5, 3, 2, 2, 4, 1, 1);
    when(service.provisionProject(projectId, actor)).thenReturn(summary);

    var result = controller.provision(projectId);

    assertThat(result).isSameAs(summary);
    verify(service).provisionProject(projectId, actor);
  }
}
