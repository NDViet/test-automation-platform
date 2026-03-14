package com.platform.analytics.flakiness;

import com.platform.core.domain.FlakinessScore;
import com.platform.core.repository.FlakinessScoreRepository;
import com.platform.core.repository.ProjectRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Query service for flakiness scores — powers API and portal.
 */
@Service
@Transactional(readOnly = true)
public class FlakinessReportService {

    private static final int DEFAULT_LIMIT = 20;

    private final FlakinessScoreRepository scoreRepo;
    private final ProjectRepository projectRepo;

    public FlakinessReportService(FlakinessScoreRepository scoreRepo,
                                   ProjectRepository projectRepo) {
        this.scoreRepo  = scoreRepo;
        this.projectRepo = projectRepo;
    }

    public List<FlakinessScore> getTopFlakyForProject(UUID projectId, int limit) {
        return scoreRepo.findTopFlakyByProject(projectId,
                PageRequest.of(0, limit > 0 ? limit : DEFAULT_LIMIT));
    }

    public List<FlakinessScore> getTopFlakyByClassification(UUID projectId,
                                                              FlakinessScore.Classification classification,
                                                              int limit) {
        return scoreRepo.findTopFlakyByProject(projectId,
                        PageRequest.of(0, limit > 0 ? limit : DEFAULT_LIMIT))
                .stream()
                .filter(s -> s.getClassification() == classification)
                .toList();
    }

    public List<FlakinessScore> getOrgWideTopFlaky(int limit) {
        return scoreRepo.findTopFlakyAcrossOrg(
                PageRequest.of(0, limit > 0 ? limit : DEFAULT_LIMIT));
    }
}
