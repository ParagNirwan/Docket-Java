package com.docket.app.repository;

import com.docket.app.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<ApiKey> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<ApiKey> findByKeyHash(String keyHash);
}
