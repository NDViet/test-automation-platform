package com.platform.core.repository;

import com.platform.core.domain.AiSkill;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSkillRepository extends JpaRepository<AiSkill, UUID> {

  List<AiSkill> findByProjectIdOrderByNameAsc(UUID projectId);

  boolean existsByProjectIdAndName(UUID projectId, String name);
}
