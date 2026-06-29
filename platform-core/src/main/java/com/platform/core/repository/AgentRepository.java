package com.platform.core.repository;

import com.platform.core.domain.Agent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

  List<Agent> findByScopeAndScopeIdOrderByNameAsc(String scope, UUID scopeId);

  Optional<Agent> findByScopeAndScopeIdAndName(String scope, UUID scopeId, String name);
}
