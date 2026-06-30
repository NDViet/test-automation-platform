package com.platform.core.repository;

import com.platform.core.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsername(String username);

  boolean existsByUsername(String username);

  List<User> findAllByOrderByUsernameAsc();

  long countBySuperAdminTrue();
}
