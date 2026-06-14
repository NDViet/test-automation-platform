package com.platform.core.repository;

import com.platform.core.domain.MappingRuleset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MappingRulesetRepository extends JpaRepository<MappingRuleset, UUID> {

    Optional<MappingRuleset> findByScopeAndScopeId(String scope, UUID scopeId);

    void deleteByScopeAndScopeId(String scope, UUID scopeId);

    boolean existsByScopeAndScopeId(String scope, UUID scopeId);
}
