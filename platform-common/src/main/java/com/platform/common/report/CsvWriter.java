package com.platform.common.report;

import java.util.List;

/** Minimal RFC-4180-ish CSV builder — quotes fields containing commas, quotes, or newlines. */
public final class CsvWriter {

  private CsvWriter() {}

  /** Escapes a single CSV field. */
  public static String field(Object value) {
    if (value == null) {
      return "";
    }
    String s = value.toString();
    if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }

  /** Joins one row of cells (each escaped) with commas. */
  public static String row(List<?> cells) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(field(cells.get(i)));
    }
    return sb.toString();
  }
}
