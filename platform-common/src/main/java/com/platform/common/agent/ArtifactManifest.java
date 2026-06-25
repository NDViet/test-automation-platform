package com.platform.common.agent;

import java.util.List;
import java.util.Optional;

/**
 * All artifacts produced in a session, returned in NodeResult. Nodes report this to the Hub; the
 * Hub routes each artifact to ReviewGateway.
 */
public record ArtifactManifest(List<ArtifactRef> artifacts) {

  public ArtifactManifest {
    artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
  }

  public static ArtifactManifest empty() {
    return new ArtifactManifest(List.of());
  }

  public boolean isEmpty() {
    return artifacts.isEmpty();
  }

  public Optional<ArtifactRef> firstOfType(ArtifactType type) {
    return artifacts.stream().filter(a -> a.type() == type).findFirst();
  }

  public List<ArtifactRef> ofType(ArtifactType type) {
    return artifacts.stream().filter(a -> a.type() == type).toList();
  }
}
