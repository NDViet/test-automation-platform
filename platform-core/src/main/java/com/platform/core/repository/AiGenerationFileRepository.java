package com.platform.core.repository;

import com.platform.core.domain.AiGenerationFile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiGenerationFileRepository extends JpaRepository<AiGenerationFile, UUID> {}
