package com.docket.app.repository;

import com.docket.app.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    Optional<UserIdentity> findByProviderAndSubject(String provider, String subject);

    Optional<UserIdentity> findByUserIdAndProvider(UUID userId, String provider);

    List<UserIdentity> findByUserId(UUID userId);
}
