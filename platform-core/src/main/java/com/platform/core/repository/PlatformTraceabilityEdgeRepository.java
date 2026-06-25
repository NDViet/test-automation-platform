package com.platform.core.repository;

import com.platform.core.domain.PlatformTraceabilityEdge;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformTraceabilityEdgeRepository
    extends JpaRepository<PlatformTraceabilityEdge, UUID> {

  List<PlatformTraceabilityEdge> findByProjectIdAndFromIdAndFromTier(
      UUID projectId, UUID fromId, String fromTier);

  List<PlatformTraceabilityEdge> findByProjectIdAndToIdAndToTier(
      UUID projectId, UUID toId, String toTier);

  List<PlatformTraceabilityEdge> findByProjectIdAndEdgeType(UUID projectId, String edgeType);

  Optional<PlatformTraceabilityEdge> findByProjectIdAndFromIdAndToIdAndEdgeType(
      UUID projectId, UUID fromId, UUID toId, String edgeType);

  @Query(
      """
      SELECT e FROM PlatformTraceabilityEdge e
      WHERE e.projectId = :projectId
        AND e.toId = :requirementId
        AND e.toTier = 'REQUIREMENT'
        AND e.edgeType = 'COVERED_BY'
      """)
  List<PlatformTraceabilityEdge> findCoverageEdgesForRequirement(
      @Param("projectId") UUID projectId, @Param("requirementId") UUID requirementId);

  @Modifying
  @Query(
      """
      DELETE FROM PlatformTraceabilityEdge e
      WHERE e.projectId = :projectId
        AND e.fromId = :fromId
        AND e.edgeType = :edgeType
      """)
  void deleteByProjectIdAndFromIdAndEdgeType(
      @Param("projectId") UUID projectId,
      @Param("fromId") UUID fromId,
      @Param("edgeType") String edgeType);
}
