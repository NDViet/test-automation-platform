package com.platform.core.repository;

import com.platform.core.domain.AiPromptTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, UUID> {

  List<AiPromptTemplate> findByProjectIdOrderByKindAscNameAsc(UUID projectId);

  Optional<AiPromptTemplate> findByProjectIdAndKindAndIsDefaultTrue(UUID projectId, String kind);

  boolean existsByProjectIdAndKindAndName(UUID projectId, String kind, String name);
}
