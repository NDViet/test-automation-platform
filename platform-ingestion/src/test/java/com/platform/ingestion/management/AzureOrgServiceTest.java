package com.platform.ingestion.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.AzureManagedOrg;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.repository.AzureManagedOrgRepository;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.service.CredentialCipher;
import com.platform.ingestion.management.AzureOrgService.OrgDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AzureOrgServiceTest {

  @Mock IntegrationCredentialRepository credRepo;
  @Mock AzureManagedOrgRepository managedRepo;
  @Mock CredentialCipher cipher;
  @Mock IntegrationCredential cred;

  AzureOrgService service;

  @BeforeEach
  void setUp() {
    service = new AzureOrgService(credRepo, managedRepo, cipher, new ObjectMapper());
  }

  @Test
  void setManagedPersistsSelectedOrgs() {
    UUID id = UUID.randomUUID();
    when(cred.getIntegrationType()).thenReturn("AZURE_DEVOPS_BOARDS");
    when(credRepo.findById(id)).thenReturn(Optional.of(cred));
    when(managedRepo.findByCredentialIdOrderByAccountName(id))
        .thenReturn(List.of(new AzureManagedOrg(id, "acme", "a1", "https://dev.azure.com/acme")));

    List<OrgDto> result =
        service.setManaged(
            id, List.of(new OrgDto("acme", "a1", "https://dev.azure.com/acme", true)));

    verify(managedRepo).deleteByCredentialId(id);
    verify(managedRepo).saveAll(any());
    assertThat(result).extracting(OrgDto::accountName).containsExactly("acme");
    assertThat(result).allMatch(OrgDto::managed);
  }

  @Test
  void discoverAccountsRejectsBlankPat() {
    assertThatThrownBy(() -> service.discoverAccounts("  "))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.discoverAccounts(null))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsNonAzureCredential() {
    UUID id = UUID.randomUUID();
    when(cred.getIntegrationType()).thenReturn("GITHUB");
    when(credRepo.findById(id)).thenReturn(Optional.of(cred));

    assertThatThrownBy(() -> service.setManaged(id, List.of()))
        .isInstanceOf(ResponseStatusException.class);
  }
}
