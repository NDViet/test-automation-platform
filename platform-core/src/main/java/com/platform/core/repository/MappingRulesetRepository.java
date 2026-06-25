package com.platform.core.repository;

import com.platform.core.domain.MappingRuleset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MappingRulesetRepository extends JpaRepository<MappingRuleset, UUID> {

  Optional<MappingRuleset> findByScopeAndScopeId(String scope, UUID scopeId);

  void deleteByScopeAndScopeId(String scope, UUID scopeId);

  boolean existsByScopeAndScopeId(String scope, UUID scopeId);
}
