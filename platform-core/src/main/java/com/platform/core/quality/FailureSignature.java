package com.platform.core.quality;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a stable SHA-256 signature for a test failure so that recurrences of the *same* failure
 * cluster together even though volatile details differ run-to-run.
 *
 * <p>The idea: a failure's identity is its exception type + the shape of its message + the top few
 * app stack frames — but NOT the concrete line numbers, object hashes, addresses, UUIDs,
 * timestamps, or literal values, which change every run. We strip those to placeholders before
 * hashing so "NullPointerException at LoginTest.java:42" and ":87" land in one cluster, while a
 * different exception lands in another.
 */
public final class FailureSignature {

  private FailureSignature() {}

  /** Top app frames to include — enough to distinguish call sites, few enough to stay stable. */
  private static final int MAX_FRAMES = 5;

  public static String compute(String message, String stackTrace) {
    StringBuilder sb = new StringBuilder();
    if (message != null && !message.isBlank()) {
      sb.append(normalize(message)).append('\n');
    }
    if (stackTrace != null && !stackTrace.isBlank()) {
      int frames = 0;
      for (String line : stackTrace.split("\\R")) {
        String t = line.trim();
        if (t.startsWith("at ")) {
          sb.append(normalize(t)).append('\n');
          if (++frames >= MAX_FRAMES) break;
        } else if (frames == 0 && !t.isEmpty()) {
          // Leading exception header line(s) before the first frame.
          sb.append(normalize(t)).append('\n');
        }
      }
    }
    return sha256(sb.toString().trim());
  }

  /** Replace volatile tokens with placeholders. Order matters (specific → generic). */
  static String normalize(String s) {
    if (s == null) return "";
    String out = s;
    out =
        out.replaceAll(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "<uuid>");
    out =
        out.replaceAll(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?",
            "<ts>");
    out = out.replaceAll("0x[0-9a-fA-F]+", "<hex>"); // pointer/hex address
    out = out.replaceAll("@[0-9a-fA-F]+", "@<id>"); // Object.toString() identity hash
    out = out.replaceAll(":\\d+\\)", ":<n>)"); // (File.java:42)
    out = out.replaceAll("\\d+", "<n>"); // any remaining digits (line numbers, literals, counts)
    out = out.replaceAll("\\s+", " ").trim();
    return out;
  }

  private static String sha256(String s) {
    try {
      byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(h.length * 2);
      for (byte b : h)
        hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
