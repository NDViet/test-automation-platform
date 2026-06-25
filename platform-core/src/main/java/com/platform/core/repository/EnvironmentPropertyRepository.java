package com.platform.core.repository;

import com.platform.core.domain.EnvironmentProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentPropertyRepository extends JpaRepository<EnvironmentProperty, UUID> {
  List<EnvironmentProperty> findByEnvironmentId(UUID environmentId);
}
