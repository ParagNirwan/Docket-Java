package com.docket.app.repository;

import com.docket.app.entity.ServiceAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceAccountRepository extends JpaRepository<ServiceAccount, UUID> {
    List<ServiceAccount> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<ServiceAccount> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<ServiceAccount> findByClientId(String clientId);
}