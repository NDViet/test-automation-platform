package com.platform.core.repository;

import com.platform.core.domain.SotTestPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SotTestPlanItemRepository extends JpaRepository<SotTestPlanItem, UUID> {

    List<SotTestPlanItem> findByPlanId(UUID planId);

    Optional<SotTestPlanItem> findByPlanIdAndTestCaseId(UUID planId, UUID testCaseId);

    @Query("SELECT COUNT(i) FROM SotTestPlanItem i WHERE i.planId = :planId AND i.result = 'NOT_RUN'")
    long countNotRunByPlanId(@Param("planId") UUID planId);

    @Query("SELECT COUNT(i) FROM SotTestPlanItem i WHERE i.planId = :planId AND i.result = 'PASS'")
    long countPassedByPlanId(@Param("planId") UUID planId);
}
