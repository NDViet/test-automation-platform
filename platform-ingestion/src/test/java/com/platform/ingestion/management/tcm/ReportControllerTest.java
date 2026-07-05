package com.platform.ingestion.management.tcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.platform.ingestion.management.tcm.CoverageDto.Row;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

  @Mock CoverageService coverage;

  @Test
  void traceabilityCsvHasHeaderAndRowPerRequirement() {
    UUID pid = UUID.randomUUID();
    Row row =
        new Row(
            "r1",
            "REQ-1",
            "Login, with comma",
            "Story",
            "Active",
            "Area\\A",
            "Team A",
            2,
            1,
            "PASSED");
    when(coverage.coverage(pid))
        .thenReturn(new CoverageDto(1, 1, 0, 0, 100.0, List.of(), List.of(), List.of(row)));

    String body = new ReportController(coverage).traceability(pid).getBody();

    String[] lines = body.strip().split("\n");
    assertThat(lines[0]).startsWith("Requirement ID,External ID,Title");
    // title contains a comma → must be quoted
    assertThat(lines[1]).contains("\"Login, with comma\"");
    assertThat(lines[1]).contains("REQ-1");
    assertThat(lines[1]).endsWith("PASSED");
  }
}
