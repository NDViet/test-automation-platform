package com.platform.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;

class PropertyMatrixServiceTest {

  private final PropertyMatrixService service = new PropertyMatrixService();

  private static LinkedHashMap<String, List<String>> axes(Object... kv) {
    LinkedHashMap<String, List<String>> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], (List<String>) kv[i + 1]);
    }
    return m;
  }

  @Test
  void noAxes_returnsEmpty() {
    assertThat(service.expand(new LinkedHashMap<>(), MatrixType.FULL)).isEmpty();
  }

  @Test
  void full_isCartesianProduct() {
    var result =
        service.expand(
            axes(
                "browser", List.of("Chrome", "Firefox"),
                "os", List.of("Linux", "Windows", "Mac")),
            MatrixType.FULL);

    assertThat(result).hasSize(2 * 3);
    assertThat(result).allSatisfy(c -> assertThat(c).containsKeys("browser", "os"));
  }

  @Test
  void pairwise_coversAllPairs_withFewerRowsThanFull() {
    var axes =
        axes(
            "browser", List.of("Chrome", "Firefox", "Safari"),
            "os", List.of("Linux", "Windows", "Mac"),
            "lang", List.of("en", "fr", "de"));

    var pairwise = service.expand(axes, MatrixType.PAIRWISE);
    var full = service.expand(axes, MatrixType.FULL);

    assertThat(full).hasSize(27);
    assertThat(pairwise.size()).isLessThan(full.size());
    assertThat(uncoveredPairs(axes, pairwise)).isEmpty();
  }

  @Test
  void singleAxis_pairwiseEqualsEachValue() {
    var result = service.expand(axes("env", List.of("dev", "stg", "prod")), MatrixType.PAIRWISE);
    assertThat(result).hasSize(3);
  }

  @Test
  void emptyValuedAxis_isDropped() {
    var result =
        service.expand(
            axes(
                "a", List.of("1", "2"),
                "b", List.of()),
            MatrixType.FULL);
    assertThat(result).hasSize(2);
    assertThat(result).allSatisfy(c -> assertThat(c).containsOnlyKeys("a"));
  }

  /** Returns the set of value-pairs not covered by any combination. */
  private Set<String> uncoveredPairs(
      LinkedHashMap<String, List<String>> axes, List<LinkedHashMap<String, String>> combos) {
    List<String> names = new ArrayList<>(axes.keySet());
    Set<String> pairs = new HashSet<>();
    for (int i = 0; i < names.size(); i++) {
      for (int j = i + 1; j < names.size(); j++) {
        for (String vi : axes.get(names.get(i))) {
          for (String vj : axes.get(names.get(j))) {
            pairs.add(i + "=" + vi + "|" + j + "=" + vj);
          }
        }
      }
    }
    for (var combo : combos) {
      for (int i = 0; i < names.size(); i++) {
        for (int j = i + 1; j < names.size(); j++) {
          pairs.remove(i + "=" + combo.get(names.get(i)) + "|" + j + "=" + combo.get(names.get(j)));
        }
      }
    }
    return pairs;
  }
}
