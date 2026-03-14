package com.platform.ingestion.security;

import com.platform.core.domain.ApiKey;
import com.platform.core.repository.ApiKeyRepository;
import com.platform.core.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock ApiKeyRepository keyRepo;
    @Mock AuditEventRepository auditRepo;

    ApiKeyService service;
    UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(keyRepo, auditRepo);
        lenient().when(keyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsKeyWithPlatPrefix() {
        ApiKeyService.ApiKeyCreationResult result = service.create("CI pipeline", teamId, null);

        assertThat(result.rawKey()).startsWith("plat_");
        assertThat(result.prefix()).startsWith("plat_");
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    void rawKeyIsNeverStoredInDb() {
        ApiKeyService.ApiKeyCreationResult result = service.create("secure-key", teamId, 90);

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(keyRepo).save(captor.capture());

        ApiKey saved = captor.getValue();
        assertThat(saved.getKeyHash()).doesNotContain(result.rawKey());
        assertThat(saved.getKeyHash()).hasSize(64); // SHA-256 hex
    }

    @Test
    void setsExpiryWhenTtlProvided() {
        ApiKeyService.ApiKeyCreationResult result = service.create("expiring", teamId, 30);
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void persistsAuditEventOnCreate() {
        service.create("audit-test", teamId, null);
        verify(auditRepo).save(argThat(e -> e.getEventType().equals("API_KEY_CREATED")));
    }

    @Test
    void revokeSetsRevokedFlag() {
        UUID keyId = UUID.randomUUID();
        ApiKey key = ApiKey.builder()
                .name("to-revoke").keyHash("h").keyPrefix("plat_x")
                .teamId(teamId).build();

        when(keyRepo.findById(keyId)).thenReturn(Optional.of(key));

        service.revoke(keyId, null);

        assertThat(key.isRevoked()).isTrue();
        verify(keyRepo).save(key);
        verify(auditRepo).save(argThat(e -> e.getEventType().equals("API_KEY_REVOKED")));
    }

    @Test
    void revokeThrowsWhenKeyNotFound() {
        UUID keyId = UUID.randomUUID();
        when(keyRepo.findById(keyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(keyId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void generatedKeysAreUnique() {
        String k1 = service.create("a", teamId, null).rawKey();
        String k2 = service.create("b", teamId, null).rawKey();
        assertThat(k1).isNotEqualTo(k2);
    }
}
