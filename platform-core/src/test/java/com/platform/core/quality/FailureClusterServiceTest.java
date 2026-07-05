package com.platform.core.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.FailureCluster;
import com.platform.core.domain.FailureOccurrence;
import com.platform.core.repository.FailureClusterRepository;
import com.platform.core.repository.FailureOccurrenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FailureClusterServiceTest {

  @Mock FailureClusterRepository clusterRepo;
  @Mock FailureOccurrenceRepository occurrenceRepo;
  FailureClusterService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID exec = UUID.randomUUID();
  private final UUID run = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new FailureClusterService(clusterRepo, occurrenceRepo);
    lenient().when(clusterRepo.save(any(FailureCluster.class))).thenAnswer(i -> i.getArgument(0));
  }

  private FailureCluster existing(String status, int occ) {
    FailureCluster c = new FailureCluster(pid, "AUTOMATED", "sig", "ref", "t", "m");
    c.setStatus(status);
    c.setOccurrences(occ);
    return c;
  }

  @Test
  void newSignatureCreatesOpenClusterWithOneOccurrence() {
    when(clusterRepo.findByProjectIdAndSignatureHash(pid, "sig")).thenReturn(Optional.empty());

    FailureCluster c = service.record(pid, "AUTOMATED", "sig", "ref", "NPE", exec, run);

    assertThat(c.getStatus()).isEqualTo("OPEN");
    assertThat(c.getOccurrences()).isEqualTo(1);
    verify(occurrenceRepo).save(any(FailureOccurrence.class));
  }

  @Test
  void duplicateExecutionIsNotCountedTwice() {
    when(clusterRepo.findByProjectIdAndSignatureHash(pid, "sig"))
        .thenReturn(Optional.of(existing("OPEN", 1)));
    when(occurrenceRepo.existsByClusterIdAndExecutionId(any(), eq(exec))).thenReturn(true);

    FailureCluster c = service.record(pid, "AUTOMATED", "sig", "ref", "NPE", exec, run);

    assertThat(c.getOccurrences()).isEqualTo(1);
    verify(occurrenceRepo, never()).save(any());
  }

  @Test
  void resolvedClusterReopensOnNewOccurrence() {
    when(clusterRepo.findByProjectIdAndSignatureHash(pid, "sig"))
        .thenReturn(Optional.of(existing("RESOLVED", 1)));

    FailureCluster c = service.record(pid, "AUTOMATED", "sig", "ref", "NPE", exec, run);

    assertThat(c.getStatus()).isEqualTo("OPEN");
    assertThat(c.getOccurrences()).isEqualTo(2);
  }

  @Test
  void recordAutomatedClustersFailedAndBrokenResults() {
    when(clusterRepo.findByProjectIdAndSignatureHash(eq(pid), any())).thenReturn(Optional.empty());

    FailureCluster c =
        service.recordAutomated(
            pid, "acme.LoginTest#login", "FAILED", "NPE", "at a.B(B.java:1)", exec, run);

    assertThat(c).isNotNull();
    assertThat(c.getSource()).isEqualTo("AUTOMATED");
    assertThat(c.getOccurrences()).isEqualTo(1);
    verify(clusterRepo, org.mockito.Mockito.atLeastOnce()).save(any(FailureCluster.class));
  }

  @Test
  void recordAutomatedIgnoresNonFailureResults() {
    FailureCluster c =
        service.recordAutomated(pid, "acme.LoginTest#login", "PASSED", null, null, exec, run);

    assertThat(c).isNull();
    verify(clusterRepo, never()).save(any());
  }

  @Test
  void linkDefectStoresExternalReference() {
    FailureCluster c = existing("OPEN", 3);
    UUID id = UUID.randomUUID();
    when(clusterRepo.findByIdAndProjectId(id, pid)).thenReturn(Optional.of(c));

    FailureCluster out =
        service.linkDefect(pid, id, "AB-42", "https://ado/AB-42", "Login NPE", "Active");

    assertThat(out.getDefectExternalId()).isEqualTo("AB-42");
    assertThat(out.getDefectUrl()).isEqualTo("https://ado/AB-42");
    assertThat(out.getDefectState()).isEqualTo("Active");
    verify(clusterRepo).save(c);
  }

  @Test
  void linkDefectRejectsBlankExternalId() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.linkDefect(pid, UUID.randomUUID(), "  ", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    verify(clusterRepo, never()).save(any());
  }

  @Test
  void updateStatusSetsKnownIssue() {
    FailureCluster c = existing("OPEN", 1);
    UUID id = UUID.randomUUID();
    when(clusterRepo.findByIdAndProjectId(id, pid)).thenReturn(Optional.of(c));

    FailureCluster out = service.updateStatus(pid, id, "KNOWN_ISSUE");

    assertThat(out.getStatus()).isEqualTo("KNOWN_ISSUE");
    verify(clusterRepo).save(c);
  }

  @Test
  void updateStatusRejectsUnknownValue() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.updateStatus(pid, UUID.randomUUID(), "BOGUS"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(clusterRepo, never()).save(any());
  }

  @Test
  void knownIssueStaysSuppressedButStillCounts() {
    when(clusterRepo.findByProjectIdAndSignatureHash(pid, "sig"))
        .thenReturn(Optional.of(existing("KNOWN_ISSUE", 1)));

    FailureCluster c = service.record(pid, "AUTOMATED", "sig", "ref", "NPE", exec, run);

    assertThat(c.getStatus()).isEqualTo("KNOWN_ISSUE");
    assertThat(c.getOccurrences()).isEqualTo(2);
  }
}
