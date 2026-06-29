package com.platform.core.repository;

import com.platform.core.domain.TaskSubType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSubTypeRepository extends JpaRepository<TaskSubType, UUID> {

  List<TaskSubType> findByTaskTypeOrderByKeyAsc(String taskType);

  boolean existsByTaskType(String taskType);
}
