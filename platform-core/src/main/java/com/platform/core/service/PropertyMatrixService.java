package com.platform.core.service;

import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Expands a test case's properties into value combinations — either the full Cartesian product or a
 * reduced pairwise (all-pairs) set, mirroring Kiwi TCMS's {@code TestRun.property_matrix}.
 *
 * <p>The pairwise generator is a deterministic greedy (AETG-style) algorithm: every pair of values
 * drawn from any two distinct properties appears in at least one returned combination, typically
 * with far fewer rows than the full product.
 */
@Service
public class PropertyMatrixService {

  /**
   * @param axes ordered map of property name → its candidate values
   * @param type FULL or PAIRWISE
   * @return ordered list of combinations (each an ordered map name → value); empty list when there
   *     are no axes
   */
  public List<LinkedHashMap<String, String>> expand(
      LinkedHashMap<String, List<String>> axes, MatrixType type) {
    if (axes == null || axes.isEmpty()) return List.of();
    // Drop empty-valued axes; they cannot contribute a combination.
    LinkedHashMap<String, List<String>> clean = new LinkedHashMap<>();
    axes.forEach(
        (k, v) -> {
          if (v != null && !v.isEmpty()) clean.put(k, v);
        });
    if (clean.isEmpty()) return List.of();

    if (type == MatrixType.FULL || clean.size() <= 1) {
      return cartesian(clean);
    }
    return pairwise(clean);
  }

  // ── Full Cartesian product ────────────────────────────────────────────────

  private List<LinkedHashMap<String, String>> cartesian(LinkedHashMap<String, List<String>> axes) {
    List<LinkedHashMap<String, String>> result = new ArrayList<>();
    result.add(new LinkedHashMap<>());
    for (Map.Entry<String, List<String>> axis : axes.entrySet()) {
      List<LinkedHashMap<String, String>> next = new ArrayList<>();
      for (LinkedHashMap<String, String> partial : result) {
        for (String value : axis.getValue()) {
          LinkedHashMap<String, String> combo = new LinkedHashMap<>(partial);
          combo.put(axis.getKey(), value);
          next.add(combo);
        }
      }
      result = next;
    }
    return result;
  }

  // ── Pairwise (greedy all-pairs) ───────────────────────────────────────────

  /** A value-pair between two distinct parameters (by index). */
  private record Pair(int i, String vi, int j, String vj) {}

  private List<LinkedHashMap<String, String>> pairwise(LinkedHashMap<String, List<String>> axes) {
    List<String> names = new ArrayList<>(axes.keySet());
    List<List<String>> values = new ArrayList<>();
    for (String n : names) values.add(axes.get(n));

    // Use a LinkedHashSet so seed selection is deterministic.
    Set<Pair> uncovered = allPairs(names, values);
    List<LinkedHashMap<String, String>> combos = new ArrayList<>();

    while (!uncovered.isEmpty()) {
      // Seed the candidate from one still-uncovered pair — guarantees progress.
      Pair seed = uncovered.iterator().next();
      String[] assigned = new String[names.size()];
      assigned[seed.i()] = seed.vi();
      assigned[seed.j()] = seed.vj();

      // Greedily fill remaining parameters to cover the most uncovered pairs.
      for (int idx = 0; idx < names.size(); idx++) {
        if (assigned[idx] != null) continue;
        String bestValue = values.get(idx).get(0);
        int bestGain = -1;
        for (String value : values.get(idx)) {
          int gain = 0;
          for (int p = 0; p < names.size(); p++) {
            if (p == idx || assigned[p] == null) continue;
            int lo = Math.min(p, idx), hi = Math.max(p, idx);
            String lv = (lo == p) ? assigned[p] : value;
            String hv = (hi == p) ? assigned[p] : value;
            if (uncovered.contains(new Pair(lo, lv, hi, hv))) gain++;
          }
          if (gain > bestGain) {
            bestGain = gain;
            bestValue = value;
          }
        }
        assigned[idx] = bestValue;
      }

      // Record the candidate and remove every pair it now covers.
      LinkedHashMap<String, String> candidate = new LinkedHashMap<>();
      for (int k = 0; k < names.size(); k++) candidate.put(names.get(k), assigned[k]);
      for (int i = 0; i < names.size(); i++) {
        for (int j = i + 1; j < names.size(); j++) {
          uncovered.remove(new Pair(i, assigned[i], j, assigned[j]));
        }
      }
      combos.add(candidate);
    }
    return combos;
  }

  private Set<Pair> allPairs(List<String> names, List<List<String>> values) {
    Set<Pair> pairs = new LinkedHashSet<>();
    for (int i = 0; i < names.size(); i++) {
      for (int j = i + 1; j < names.size(); j++) {
        for (String vi : values.get(i)) {
          for (String vj : values.get(j)) {
            pairs.add(new Pair(i, vi, j, vj));
          }
        }
      }
    }
    return pairs;
  }

  /** Serializes a combination as a stable "k=v;k=v" string for the execution discriminator. */
  public static String serialize(LinkedHashMap<String, String> combo) {
    StringBuilder sb = new StringBuilder();
    combo.forEach(
        (k, v) -> {
          if (sb.length() > 0) sb.append(';');
          sb.append(k).append('=').append(v);
        });
    return sb.toString();
  }
}
