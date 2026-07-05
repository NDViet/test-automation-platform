package com.platform.common.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

  @Test
  void quotesFieldsWithCommasQuotesNewlines() {
    assertThat(CsvWriter.field("plain")).isEqualTo("plain");
    assertThat(CsvWriter.field("a,b")).isEqualTo("\"a,b\"");
    assertThat(CsvWriter.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    assertThat(CsvWriter.field("line1\nline2")).isEqualTo("\"line1\nline2\"");
    assertThat(CsvWriter.field(null)).isEmpty();
    assertThat(CsvWriter.field(42)).isEqualTo("42");
  }

  @Test
  void rowJoinsEscapedCells() {
    assertThat(CsvWriter.row(List.of("a", "b,c", "d"))).isEqualTo("a,\"b,c\",d");
    assertThat(CsvWriter.row(Arrays.asList("x", null, 7))).isEqualTo("x,,7");
  }
}
