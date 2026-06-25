package com.platform.core.repository;

import com.platform.core.domain.GitHubRepoCache;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubRepoCacheRepository extends JpaRepository<GitHubRepoCache, UUID> {

  List<GitHubRepoCache> findByCredentialIdOrderByFullName(UUID credentialId);

  Optional<GitHubRepoCache> findFirstByCredentialIdOrderBySyncedAtDesc(UUID credentialId);

  long countByCredentialId(UUID credentialId);

  @Modifying
  @Query("DELETE FROM GitHubRepoCache r WHERE r.credentialId = :credentialId")
  void deleteByCredentialId(UUID credentialId);
}
