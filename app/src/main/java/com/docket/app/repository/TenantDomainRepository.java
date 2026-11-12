package com.docket.app.repository;

import com.docket.app.entity.TenantDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, UUID> {
    List<TenantDomain> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TenantDomain> findByTenantIdAndDomainIgnoreCase(UUID tenantId, String domain);

    List<TenantDomain> findByDomainIgnoreCaseAndVerifiedAtIsNotNull(String domain);

    Optional<TenantDomain> findByTenantIdAndDomainIgnoreCaseAndVerifiedAtIsNull(UUID tenantId, String domain);
}
