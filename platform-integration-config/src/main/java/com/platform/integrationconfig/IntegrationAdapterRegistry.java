package com.platform.integrationconfig;

import com.platform.common.integration.IntegrationAdapter;
import com.platform.common.integration.IntegrationType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Spring-managed registry of all IntegrationAdapter beans. Adapters self-register by being declared
 * as @Component (or @Service) beans. Hub uses this to look up the right adapter by IntegrationType
 * at runtime.
 */
@Component
public class IntegrationAdapterRegistry {

  private final Map<IntegrationType, IntegrationAdapter<?, ?>> adapters;

  public IntegrationAdapterRegistry(List<IntegrationAdapter<?, ?>> adapterList) {
    this.adapters =
        adapterList.stream()
            .collect(Collectors.toUnmodifiableMap(IntegrationAdapter::type, Function.identity()));
  }

  @SuppressWarnings("unchecked")
  public <R, C> Optional<IntegrationAdapter<R, C>> find(IntegrationType type) {
    return Optional.ofNullable((IntegrationAdapter<R, C>) adapters.get(type));
  }

  public boolean supports(IntegrationType type) {
    return adapters.containsKey(type);
  }

  public java.util.Set<IntegrationType> supportedTypes() {
    return adapters.keySet();
  }
}
