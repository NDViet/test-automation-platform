package com.platform.core.repository;

import com.platform.core.domain.GithubManagedRepo;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GithubManagedRepoRepository extends JpaRepository<GithubManagedRepo, UUID> {

  List<GithubManagedRepo> findByCredentialIdOrderByFullName(UUID credentialId);

  void deleteByCredentialId(UUID credentialId);
}
