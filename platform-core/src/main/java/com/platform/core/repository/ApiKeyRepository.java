package com.platform.core.repository;

import com.platform.core.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByTeamIdAndRevokedFalseOrderByCreatedAtDesc(UUID teamId);

    List<ApiKey> findByTeamIdOrderByCreatedAtDesc(UUID teamId);
}
