package com.docket.app.repository;

import com.docket.app.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<WebhookEndpoint> findByTenantIdAndId(UUID tenantId, UUID id);
}
