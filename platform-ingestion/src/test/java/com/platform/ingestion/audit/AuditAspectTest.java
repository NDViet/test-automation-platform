package com.platform.ingestion.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.AuditEvent;
import com.platform.core.repository.AuditEventRepository;
import com.platform.security.jwt.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

  @Mock AuditEventRepository auditRepo;
  @Mock ProceedingJoinPoint pjp;
  @Mock AuditLog annotation;

  AuditAspect aspect;

  @BeforeEach
  void setUp() {
    aspect = new AuditAspect(auditRepo);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void capturesPortalUserAsActor() throws Throwable {
    UUID uid = UUID.randomUUID();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(uid, "bob", false), null, List.of()));
    when(annotation.eventType()).thenReturn("TEST_CASE_APPROVED");
    when(annotation.resourceType()).thenReturn("TEST_CASE");

    aspect.audit(pjp, annotation);

    ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRepo).save(saved.capture());
    assertThat(saved.getValue().getActorUserId()).isEqualTo(uid);
    assertThat(saved.getValue().getActorUsername()).isEqualTo("bob");
    assertThat(saved.getValue().getOutcome()).isEqualTo("SUCCESS");
    assertThat(saved.getValue().getActorKeyId()).isNull();
  }
}
