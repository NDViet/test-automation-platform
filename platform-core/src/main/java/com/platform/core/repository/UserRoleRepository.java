package com.platform.core.repository;

import com.platform.core.domain.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

  List<UserRole> findByUserId(UUID userId);

  List<UserRole> findByScopeAndScopeId(String scope, UUID scopeId);

  Optional<UserRole> findByUserIdAndRoleAndScopeAndScopeId(
      UUID userId, String role, String scope, UUID scopeId);
}
