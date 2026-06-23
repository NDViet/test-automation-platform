package com.platform.core.repository;

import com.platform.core.domain.GithubManagedRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GithubManagedRepoRepository extends JpaRepository<GithubManagedRepo, UUID> {

    List<GithubManagedRepo> findByCredentialIdOrderByFullName(UUID credentialId);

    void deleteByCredentialId(UUID credentialId);
}
