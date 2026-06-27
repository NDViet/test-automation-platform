package com.platform.core.repository;

import com.platform.core.domain.CredKeySetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CredKeySettingRepository extends JpaRepository<CredKeySetting, UUID> {

  /** The single settings row, if the key has been initialized from a passphrase. */
  Optional<CredKeySetting> findFirstByOrderByCreatedAtAsc();
}
