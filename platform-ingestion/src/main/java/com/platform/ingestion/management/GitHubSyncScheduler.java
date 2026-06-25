package com.platform.ingestion.management;

import com.platform.core.domain.GitHubRepoCache;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.repository.GitHubRepoCacheRepository;
import com.platform.core.repository.IntegrationCredentialRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes the GitHub repo cache for credentials that have a positive {@code
 * sync_interval_minutes} value. Checks every 60 seconds and triggers a sync when {@code
 * lastSyncedAt + interval < now}.
 */
@Component
@Configuration
@EnableScheduling
public class GitHubSyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(GitHubSyncScheduler.class);

  private final IntegrationCredentialRepository credRepo;
  private final GitHubRepoCacheRepository cacheRepo;
  private final GitHubRepoService gitHubRepoService;

  public GitHubSyncScheduler(
      IntegrationCredentialRepository credRepo,
      GitHubRepoCacheRepository cacheRepo,
      GitHubRepoService gitHubRepoService) {
    this.credRepo = credRepo;
    this.cacheRepo = cacheRepo;
    this.gitHubRepoService = gitHubRepoService;
  }

  @Scheduled(fixedDelay = 60_000)
  public void checkAndSync() {
    List<IntegrationCredential> candidates =
        credRepo.findByIntegrationTypeAndSyncIntervalMinutesGreaterThan("GITHUB", 0);
    if (candidates.isEmpty()) return;

    Instant now = Instant.now();
    for (IntegrationCredential cred : candidates) {
      long intervalSeconds = (long) cred.getSyncIntervalMinutes() * 60;
      Instant due =
          cacheRepo
              .findFirstByCredentialIdOrderBySyncedAtDesc(cred.getId())
              .map(GitHubRepoCache::getSyncedAt)
              .map(t -> t.plusSeconds(intervalSeconds))
              .orElse(Instant.EPOCH); // never synced → sync now

      if (due.isBefore(now)) {
        log.info(
            "[GitHubSync] Auto-syncing credential {} (interval={}m)",
            cred.getId(),
            cred.getSyncIntervalMinutes());
        try {
          gitHubRepoService.syncToCache(cred.getId());
          log.info("[GitHubSync] Completed sync for credential {}", cred.getId());
        } catch (Exception e) {
          log.warn("[GitHubSync] Sync failed for credential {}: {}", cred.getId(), e.getMessage());
        }
      }
    }
  }
}
