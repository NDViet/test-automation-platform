package com.platform.core.repository;

import com.platform.core.domain.EnvironmentProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnvironmentPropertyRepository extends JpaRepository<EnvironmentProperty, UUID> {
    List<EnvironmentProperty> findByEnvironmentId(UUID environmentId);
}
