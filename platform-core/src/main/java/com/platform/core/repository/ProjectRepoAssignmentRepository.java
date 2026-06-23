package com.platform.core.repository;

import com.platform.core.domain.ProjectRepoAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepoAssignmentRepository extends JpaRepository<ProjectRepoAssignment, UUID> {

    List<ProjectRepoAssignment> findByProjectIdOrderByRepoFullName(UUID projectId);

    @Modifying
    @Query("DELETE FROM ProjectRepoAssignment a WHERE a.projectId = :projectId")
    void deleteByProjectId(UUID projectId);
}
