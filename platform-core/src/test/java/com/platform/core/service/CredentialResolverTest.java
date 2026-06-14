package com.platform.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.domain.IntegrationCredential.Scope;
import com.platform.core.domain.Organization;
import com.platform.core.domain.Project;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CredentialResolverTest {

    private IntegrationCredentialRepository credRepo;
    private ProjectRepository projectRepo;
    private CredentialCipher cipher;
    private CredentialResolver resolver;

    private final UUID projectId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final String TYPE = "AZURE_DEVOPS_BOARDS";

    @BeforeEach
    void setUp() {
        credRepo = mock(IntegrationCredentialRepository.class);
        projectRepo = mock(ProjectRepository.class);
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        cipher = new CredentialCipher(Base64.getEncoder().encodeToString(k));
        resolver = new CredentialResolver(credRepo, projectRepo, cipher, new ObjectMapper());

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(orgId);
        Project project = mock(Project.class);
        when(project.getOrganization()).thenReturn(org);
        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));

        when(credRepo.findByScopeAndScopeIdAndIntegrationType(anyString(), any(), anyString()))
                .thenReturn(Optional.empty());
    }

    private IntegrationCredential cred(Scope scope, UUID scopeId, String baseUrl,
                                       Map<String, String> params, Map<String, String> secret) {
        String ciphertext = null;
        if (secret != null && !secret.isEmpty()) {
            try { ciphertext = cipher.encrypt(new ObjectMapper().writeValueAsString(secret)); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        return new IntegrationCredential(scope, scopeId, TYPE, "disp", baseUrl, params, ciphertext);
    }

    @Test
    void noCredentials_returnsEmpty() {
        assertThat(resolver.resolve(projectId, TYPE)).isEmpty();
    }

    @Test
    void orgScoped_isUsed() {
        when(credRepo.findByScopeAndScopeIdAndIntegrationType(Scope.ORG.name(), orgId, TYPE))
                .thenReturn(Optional.of(cred(Scope.ORG, orgId, "https://dev.azure.com/contoso",
                        Map.of("organization", "contoso"), Map.of("pat", "ORG_PAT"))));

        ResolvedCredential r = resolver.resolve(projectId, TYPE).orElseThrow();

        assertThat(r.param("organization")).isEqualTo("contoso");
        assertThat(r.secret("pat")).isEqualTo("ORG_PAT");
        assertThat(r.secretScope()).isEqualTo(Scope.ORG);
    }

    @Test
    void projectOverridesOrg() {
        when(credRepo.findByScopeAndScopeIdAndIntegrationType(Scope.ORG.name(), orgId, TYPE))
                .thenReturn(Optional.of(cred(Scope.ORG, orgId, "https://dev.azure.com/contoso",
                        Map.of("organization", "contoso", "area_path", "Default"), Map.of("pat", "ORG_PAT"))));
        when(credRepo.findByScopeAndScopeIdAndIntegrationType(Scope.PROJECT.name(), projectId, TYPE))
                .thenReturn(Optional.of(cred(Scope.PROJECT, projectId, null,
                        Map.of("area_path", "Product House\\Checkout"), Map.of())));

        ResolvedCredential r = resolver.resolve(projectId, TYPE).orElseThrow();

        assertThat(r.param("organization")).isEqualTo("contoso");                 // inherited from ORG
        assertThat(r.param("area_path")).isEqualTo("Product House\\Checkout");   // PROJECT wins
        assertThat(r.secret("pat")).isEqualTo("ORG_PAT");                        // inherited secret
        assertThat(r.baseUrl()).isEqualTo("https://dev.azure.com/contoso");
    }

    @Test
    void teamOverridesProjectAndOrg() {
        when(credRepo.findByScopeAndScopeIdAndIntegrationType(Scope.ORG.name(), orgId, TYPE))
                .thenReturn(Optional.of(cred(Scope.ORG, orgId, "u", Map.of(), Map.of("pat", "ORG"))));
        when(credRepo.findByScopeAndScopeIdAndIntegrationType(Scope.PROJECT.name(), projectId, TYPE))
                .thenReturn(Optional.of(cred(Scope.PROJECT, projectId, "u", Map.of(), Map.of("pat", "PROJECT"))));
        when(credRepo.findByScopeAndScopeIdAndIntegrationType(Scope.TEAM.name(), teamId, TYPE))
                .thenReturn(Optional.of(cred(Scope.TEAM, teamId, "u", Map.of(), Map.of("pat", "TEAM"))));

        ResolvedCredential r = resolver.resolve(projectId, teamId, TYPE).orElseThrow();

        assertThat(r.secret("pat")).isEqualTo("TEAM");
        assertThat(r.secretScope()).isEqualTo(Scope.TEAM);
    }

    @Test
    void disabledCredential_isIgnored() {
        IntegrationCredential disabled = cred(Scope.ORG, orgId, "u", Map.of(), Map.of("pat", "ORG"));
        disabled.setEnabled(false);
        when(credRepo.findByScopeAndScopeIdAndIntegrationType(Scope.ORG.name(), orgId, TYPE))
                .thenReturn(Optional.of(disabled));

        assertThat(resolver.resolve(projectId, TYPE)).isEmpty();
    }
}
