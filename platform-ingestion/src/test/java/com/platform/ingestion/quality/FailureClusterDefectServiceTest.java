package com.platform.ingestion.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.FailureCluster;
import com.platform.core.quality.FailureClusterService;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import com.platform.integration.port.IssueReference;
import com.platform.integration.port.IssueRequest;
import com.platform.integration.port.IssueTrackerPort;
import com.platform.integration.port.TrackerFactoryRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FailureClusterDefectServiceTest {

  @Mock FailureClusterService clusters;
  @Mock CredentialResolver credentialResolver;
  @Mock TrackerFactoryRegistry trackerRegistry;
  @Mock IssueTrackerPort tracker;

  FailureClusterDefectService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID cid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new FailureClusterDefectService(clusters, credentialResolver, trackerRegistry);
  }

  private FailureCluster cluster() {
    return new FailureCluster(pid, "AUTOMATED", "sig", "acme.LoginTest#login", "Login NPE", "boom");
  }

  private ResolvedCredential cred() {
    return new ResolvedCredential(
        "AZURE_DEVOPS_BOARDS",
        "https://ado",
        Map.of("project_key", "PROJ"),
        Map.of("pat", "s"),
        null);
  }

  @Test
  void createsDefectThroughTrackerAndLinksItBack() {
    when(clusters.get(pid, cid)).thenReturn(cluster());
    when(trackerRegistry.lifecycleTypes()).thenReturn(List.of("AZURE_DEVOPS_BOARDS"));
    when(credentialResolver.resolve(pid, "AZURE_DEVOPS_BOARDS")).thenReturn(Optional.of(cred()));
    when(trackerRegistry.build(eq("AZURE_DEVOPS_BOARDS"), any())).thenReturn(Optional.of(tracker));
    when(tracker.createIssue(any()))
        .thenReturn(new IssueReference("AB-42", "https://ado/AB-42", "New", "Bug"));
    when(clusters.linkDefect(
            eq(pid), eq(cid), eq("AB-42"), eq("https://ado/AB-42"), any(), eq("New")))
        .thenReturn(cluster());

    service.createDefect(pid, cid, "Bug", "High", null);

    ArgumentCaptor<IssueRequest> req = ArgumentCaptor.forClass(IssueRequest.class);
    verify(tracker).createIssue(req.capture());
    assertThat(req.getValue().title()).isEqualTo("Login NPE");
    assertThat(req.getValue().projectKey()).isEqualTo("PROJ");
    assertThat(req.getValue().testId()).isEqualTo("acme.LoginTest#login");
    verify(clusters)
        .linkDefect(eq(pid), eq(cid), eq("AB-42"), eq("https://ado/AB-42"), any(), eq("New"));
  }

  @Test
  void skipsTrackerWithNoSecretAndFailsWhenNoneConfigured() {
    when(clusters.get(pid, cid)).thenReturn(cluster());
    when(trackerRegistry.lifecycleTypes()).thenReturn(List.of("JIRA_CLOUD"));
    when(credentialResolver.resolve(pid, "JIRA_CLOUD")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createDefect(pid, cid, null, null, null))
        .isInstanceOf(IllegalStateException.class);

    verify(tracker, never()).createIssue(any());
    verify(clusters, never()).linkDefect(any(), any(), any(), any(), any(), any());
  }
}
