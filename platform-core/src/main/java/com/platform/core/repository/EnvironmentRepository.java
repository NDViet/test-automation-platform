package com.platform.core.repository;

import com.platform.core.domain.Environment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {
    List<Environment> findByProjectIdOrderByNameAsc(UUID projectId);
    Optional<Environment> findByProjectIdAndName(UUID projectId, String name);
}
