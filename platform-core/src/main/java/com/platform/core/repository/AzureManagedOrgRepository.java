package com.platform.core.repository;

import com.platform.core.domain.AzureManagedOrg;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AzureManagedOrgRepository extends JpaRepository<AzureManagedOrg, UUID> {

  List<AzureManagedOrg> findByCredentialIdOrderByAccountName(UUID credentialId);

  void deleteByCredentialId(UUID credentialId);
}
