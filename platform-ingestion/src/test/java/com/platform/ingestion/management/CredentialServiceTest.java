package com.platform.ingestion.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.service.CredentialCipher;
import com.platform.ingestion.management.dto.CredentialDto;
import com.platform.ingestion.management.dto.SaveCredentialRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CredentialServiceTest {

    private IntegrationCredentialRepository repo;
    private CredentialCipher cipher;
    private CredentialHealthChecker healthChecker;
    private CredentialService service;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repo = mock(IntegrationCredentialRepository.class);
        healthChecker = mock(CredentialHealthChecker.class);
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        cipher = new CredentialCipher(Base64.getEncoder().encodeToString(k));
        service = new CredentialService(repo, cipher, healthChecker, om);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void save_encryptsSecret_andDoesNotReturnIt() {
        UUID orgId = UUID.randomUUID();
        when(repo.findByScopeAndScopeIdAndIntegrationType("ORG", orgId, "AZURE_DEVOPS_BOARDS"))
                .thenReturn(Optional.empty());

        SaveCredentialRequest req = new SaveCredentialRequest(
                "ORG", orgId, "AZURE_DEVOPS_BOARDS", "Acme ADO",
                "https://dev.azure.com",
                Map.of("organization", "acme"),
                Map.of("pat", "SECRET_PAT"),
                true);

        CredentialDto dto = service.save(req, "admin");

        assertThat(dto.hasSecret()).isTrue();
        assertThat(dto.connectionParams()).containsEntry("organization", "acme");

        ArgumentCaptor<IntegrationCredential> cap = ArgumentCaptor.forClass(IntegrationCredential.class);
        verify(repo).save(cap.capture());
        String ct = cap.getValue().getSecretCiphertext();
        assertThat(cipher.isEncrypted(ct)).isTrue();
        assertThat(ct).doesNotContain("SECRET_PAT");
        assertThat(cipher.decrypt(ct)).contains("SECRET_PAT");
    }

    @Test
    void update_withoutSecret_keepsExistingCiphertext() {
        UUID orgId = UUID.randomUUID();
        String existingCt = cipher.encrypt("{\"pat\":\"OLD\"}");
        IntegrationCredential existing = new IntegrationCredential(
                IntegrationCredential.Scope.ORG, orgId, "GITHUB_ISSUES", "GH",
                "https://api.github.com", new java.util.HashMap<>(Map.of("owner", "acme")), existingCt);
        when(repo.findByScopeAndScopeIdAndIntegrationType("ORG", orgId, "GITHUB_ISSUES"))
                .thenReturn(Optional.of(existing));

        SaveCredentialRequest req = new SaveCredentialRequest(
                "ORG", orgId, "GITHUB_ISSUES", "GH renamed", "https://api.github.com",
                Map.of("owner", "acme", "repo", "checkout"), null, true);

        service.save(req, "admin");

        ArgumentCaptor<IntegrationCredential> cap = ArgumentCaptor.forClass(IntegrationCredential.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getSecretCiphertext()).isEqualTo(existingCt); // unchanged
        assertThat(cap.getValue().getDisplayName()).isEqualTo("GH renamed");
    }

    @Test
    void testConnection_decryptsSecretIntoParams() {
        UUID id = UUID.randomUUID();
        String ct = cipher.encrypt("{\"pat\":\"P123\"}");
        IntegrationCredential c = new IntegrationCredential(
                IntegrationCredential.Scope.ORG, null, "AZURE_DEVOPS_BOARDS", "ADO",
                "https://dev.azure.com", new java.util.HashMap<>(Map.of("organization", "acme")), ct);
        when(repo.findById(id)).thenReturn(Optional.of(c));
        when(healthChecker.check(anyString(), any(), anyMap()))
                .thenReturn(new CredentialHealthChecker.Result(true, "OK"));

        var result = service.testConnection(id);

        assertThat(result.ok()).isTrue();
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(healthChecker).check(eq("AZURE_DEVOPS_BOARDS"), eq("https://dev.azure.com"), params.capture());
        assertThat(params.getValue()).containsEntry("organization", "acme").containsEntry("pat", "P123");
    }

    @Test
    void list_org_returnsMaskedDtos() {
        UUID orgId = UUID.randomUUID();
        IntegrationCredential c = new IntegrationCredential(
                IntegrationCredential.Scope.ORG, orgId, "JIRA_CLOUD", "Jira",
                "https://acme.atlassian.net", Map.of("email", "a@b.com"),
                cipher.encrypt("{\"pat\":\"x\"}"));
        when(repo.findByScopeAndScopeId("ORG", orgId)).thenReturn(List.of(c));

        List<CredentialDto> dtos = service.list("ORG", orgId);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).hasSecret()).isTrue();
        assertThat(dtos.get(0).integrationType()).isEqualTo("JIRA_CLOUD");
    }
}
