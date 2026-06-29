package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AiPromptTemplateControllerTest {

  @Mock AiPromptTemplateService service;
  AiPromptTemplateController controller;

  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    controller = new AiPromptTemplateController(service);
  }

  @Test
  void defaultsReturnsResolvedSystemAndUserBodies() {
    when(service.resolveDefault(projectId, "SYSTEM")).thenReturn("SYS");
    when(service.resolveDefault(projectId, "USER")).thenReturn("USR");

    AiPromptTemplateController.PromptDefaults defaults = controller.defaults(projectId);

    assertThat(defaults.system()).isEqualTo("SYS");
    assertThat(defaults.user()).isEqualTo("USR");
  }

  @Test
  void createReturns201AndPassesActor() {
    AiPromptTemplateRequest req = new AiPromptTemplateRequest("SYSTEM", "n", "b", true);
    when(service.create(projectId, req, "alice")).thenReturn(null);

    var resp = controller.create(projectId, req, "alice");

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(service).create(projectId, req, "alice");
  }

  @Test
  void deleteReturns204() {
    UUID id = UUID.randomUUID();
    var resp = controller.delete(projectId, id);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(service).delete(projectId, id);
  }
}
