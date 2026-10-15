package com.docket.app.repository;

import com.docket.app.entity.TenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {
    List<TenantMembership> findByUserId(UUID userId);
}
