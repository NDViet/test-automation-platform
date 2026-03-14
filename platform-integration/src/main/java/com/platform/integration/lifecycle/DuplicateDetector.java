package com.platform.integration.lifecycle;

import com.platform.core.domain.IssueTrackerLink;
import com.platform.core.repository.IssueTrackerLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Prevents ticket spam by checking whether an open ticket already exists
 * for a given (testId, projectId, trackerType) before creating a new one.
 *
 * <p>The authoritative duplicate check is the {@code issue_tracker_links} table
 * unique constraint. This component provides an early-exit fast path.</p>
 */
@Component
public class DuplicateDetector {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetector.class);

    private final IssueTrackerLinkRepository linkRepo;

    public DuplicateDetector(IssueTrackerLinkRepository linkRepo) {
        this.linkRepo = linkRepo;
    }

    /**
     * Returns {@code true} if a non-closed ticket already exists for the test.
     */
    public boolean hasOpenTicket(String testId, UUID projectId, String trackerType) {
        return linkRepo.findByTestIdAndProjectIdAndTrackerType(testId, projectId, trackerType)
                .map(l -> !isDone(l.getIssueStatus()))
                .orElse(false);
    }

    /**
     * Returns existing link if any (open or closed) for the test.
     */
    public Optional<IssueTrackerLink> findExisting(String testId, UUID projectId, String trackerType) {
        return linkRepo.findByTestIdAndProjectIdAndTrackerType(testId, projectId, trackerType);
    }

    /**
     * All test IDs that have an existing ticket (any status) for this project.
     * Used to identify tests that may need close/reopen even if currently passing.
     */
    public List<String> findLinkedTestIds(UUID projectId, String trackerType) {
        return linkRepo.findTestIdsByProjectIdAndTrackerType(projectId, trackerType);
    }

    /**
     * Persists a new link or updates the status of an existing one.
     */
    public IssueTrackerLink saveOrUpdate(String testId, UUID projectId, String trackerType,
                                          String issueKey, String issueUrl, String issueType,
                                          String newStatus) {
        Optional<IssueTrackerLink> existing =
                linkRepo.findByTestIdAndProjectIdAndTrackerType(testId, projectId, trackerType);

        if (existing.isPresent()) {
            IssueTrackerLink link = existing.get();
            link.syncStatus(newStatus);
            return linkRepo.save(link);
        }

        IssueTrackerLink link = new IssueTrackerLink(testId, projectId, trackerType,
                issueKey, issueUrl, issueType);
        link.syncStatus(newStatus);
        return linkRepo.save(link);
    }

    private boolean isDone(String status) {
        if (status == null) return false;
        String s = status.toLowerCase();
        return s.contains("done") || s.contains("closed") || s.contains("resolved");
    }
}
