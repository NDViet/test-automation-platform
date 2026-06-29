package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AiSkillControllerTest {

  @Mock AiSkillService service;
  AiSkillController controller;

  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    controller = new AiSkillController(service);
  }

  private AiSkillDto dto(String name) {
    return new AiSkillDto(
        UUID.randomUUID(), projectId, name, null, "i", true, "alice", Instant.now(), Instant.now());
  }

  @Test
  void listDelegatesToService() {
    when(service.list(projectId)).thenReturn(List.of(dto("A")));
    assertThat(controller.list(projectId)).hasSize(1);
    verify(service).list(projectId);
  }

  @Test
  void createReturns201AndPassesActor() {
    AiSkillRequest req = new AiSkillRequest("A", null, "i", true);
    when(service.create(projectId, req, "alice")).thenReturn(dto("A"));

    ResponseEntity<AiSkillDto> resp = controller.create(projectId, req, "alice");

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resp.getBody()).isNotNull();
    verify(service).create(projectId, req, "alice");
  }

  @Test
  void deleteReturns204() {
    UUID skillId = UUID.randomUUID();
    ResponseEntity<Void> resp = controller.delete(projectId, skillId);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(service).delete(projectId, skillId);
  }
}
