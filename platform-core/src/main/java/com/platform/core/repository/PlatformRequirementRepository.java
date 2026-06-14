package com.platform.core.repository;

import com.platform.core.domain.PlatformRequirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    List<PlatformRequirement> findByProjectIdAndIssueTypeIn(UUID projectId, java.util.Collection<String> issueTypes);

    @Query("SELECT r FROM PlatformRequirement r WHERE r.projectId = :pid " +
           "AND (LOWER(r.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(r.externalId) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY r.updatedAt DESC")
    List<PlatformRequirement> searchByProjectId(@Param("pid") UUID projectId, @Param("q") String query);

    /**
     * Paged, combinable filter+search (each filter optional via "" sentinel — empty
     * means "no filter"). Empty strings are used instead of nulls so Postgres types the
     * bind parameters as varchar (a null bind makes LOWER() resolve to lower(bytea)).
     */
    @Query("SELECT r FROM PlatformRequirement r WHERE r.projectId = :pid " +
           "AND (:status = '' OR r.status = :status) " +
           "AND (:issueType = '' OR r.issueType = :issueType) " +
           "AND (:q = '' OR LOWER(r.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(r.externalId) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<PlatformRequirement> searchPage(@Param("pid") UUID projectId,
                                         @Param("status") String status,
                                         @Param("issueType") String issueType,
                                         @Param("q") String query,
                                         Pageable pageable);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, String status);

    /** Distinct people referenced on a project's work items (assignee / creator / changer). */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT person FROM (
                SELECT raw_upstream->>'System.AssignedTo' AS person FROM platform_requirements WHERE project_id = :pid
                UNION SELECT raw_upstream->>'System.CreatedBy'  FROM platform_requirements WHERE project_id = :pid
                UNION SELECT raw_upstream->>'System.ChangedBy'  FROM platform_requirements WHERE project_id = :pid
            ) s WHERE person IS NOT NULL AND person <> ''
            """)
    List<String> findDistinctPeople(@Param("pid") UUID projectId);

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
