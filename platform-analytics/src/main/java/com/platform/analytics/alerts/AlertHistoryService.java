package com.platform.analytics.alerts;

import com.platform.core.domain.AlertHistory;
import com.platform.core.repository.AlertHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Persists and queries fired {@link AlertEvent}s as {@link AlertHistory} rows.
 */
@Service
public class AlertHistoryService {

    private final AlertHistoryRepository historyRepo;

    public AlertHistoryService(AlertHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
    }

    @Transactional
    public AlertHistory record(AlertEvent event, boolean delivered, String channels) {
        AlertHistory entry = AlertHistory.builder()
                .ruleName(event.ruleName())
                .severity(event.severity().name())
                .message(event.message())
                .teamId(event.teamId())
                .projectId(event.projectId())
                .runId(event.runId())
                .channels(channels)
                .delivered(delivered)
                .build();
        return historyRepo.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AlertHistory> recentForProject(String projectId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return historyRepo.findByProjectIdSince(projectId, since);
    }

    @Transactional(readOnly = true)
    public List<AlertHistory> recentForProject(String projectId, int limit, boolean paginated) {
        return historyRepo.findByProjectIdOrderByFiredAtDesc(projectId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<AlertHistory> recentAll(int days) {
        return historyRepo.findAllSince(Instant.now().minus(days, ChronoUnit.DAYS));
    }
}
