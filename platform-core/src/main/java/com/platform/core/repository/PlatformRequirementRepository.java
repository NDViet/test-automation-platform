package com.platform.core.repository;

import com.platform.core.domain.PlatformRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformRequirementRepository extends JpaRepository<PlatformRequirement, UUID> {

    Optional<PlatformRequirement> findByProjectIdAndExternalId(UUID projectId, String externalId);

    List<PlatformRequirement> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    List<PlatformRequirement> findByProjectIdAndParentId(UUID projectId, UUID parentId);

    List<PlatformRequirement> findByProjectIdAndStatusOrderByUpdatedAtDesc(UUID projectId, String status);

    List<PlatformRequirement> findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(UUID projectId, String issueType);

    @Query("SELECT r FROM PlatformRequirement r WHERE r.projectId = :pid " +
           "AND (LOWER(r.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(r.externalId) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY r.updatedAt DESC")
    List<PlatformRequirement> searchByProjectId(@Param("pid") UUID projectId, @Param("q") String query);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, String status);

    @Modifying
    @Query("UPDATE PlatformRequirement r SET r.parentId = :parentId, r.depth = :depth, r.updatedAt = :now WHERE r.id = :id")
    void updateParent(@Param("id") UUID id,
                      @Param("parentId") UUID parentId,
                      @Param("depth") int depth,
                      @Param("now") Instant now);

    /**
     * Upsert a requirement from an external source.
     * Only updates title/description/syncedAt when they actually changed (version check via title+desc hash).
     * Uses the unique partial index on (project_id, external_id) WHERE external_id IS NOT NULL.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO platform_requirements
                (id, project_id, integration_config_id, external_id, title, description,
                 acceptance_criteria, issue_type, status, synced_at, created_at, updated_at)
            VALUES
                (gen_random_uuid(), :projectId, :configId, :externalId, :title, :description,
                 '[]'::jsonb, :issueType, 'OPEN', :now, :now, :now)
            ON CONFLICT (project_id, external_id) WHERE external_id IS NOT NULL
            DO UPDATE SET
                title       = EXCLUDED.title,
                description = EXCLUDED.description,
                synced_at   = EXCLUDED.synced_at,
                updated_at  = EXCLUDED.updated_at
            WHERE platform_requirements.title        != EXCLUDED.title
               OR platform_requirements.description != EXCLUDED.description
            """)
    int upsert(@Param("projectId") UUID projectId,
               @Param("configId") UUID integrationConfigId,
               @Param("externalId") String externalId,
               @Param("title") String title,
               @Param("description") String description,
               @Param("issueType") String issueType,
               @Param("now") Instant now);
}
