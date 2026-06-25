package com.platform.core.repository;

import com.platform.core.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
  Optional<Organization> findBySlug(String slug);

  boolean existsBySlug(String slug);
}
