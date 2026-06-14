package com.platform.core.repository;

import com.platform.core.domain.ScopedSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScopedSettingRepository extends JpaRepository<ScopedSetting, UUID> {

    Optional<ScopedSetting> findByScopeAndScopeIdAndKey(String scope, UUID scopeId, String key);

    List<ScopedSetting> findByScopeAndScopeId(String scope, UUID scopeId);
}
