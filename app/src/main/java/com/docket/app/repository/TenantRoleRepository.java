package com.docket.app.repository;

import com.docket.app.entity.TenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {
    List<TenantRole> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<TenantRole> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    @Query("select count(m) from TenantMembership m where m.customRoleId = :roleId and m.memberStatus = com.docket.app.enums.MemberStatus.ACTIVE")
    long countActiveMembers(@Param("roleId") UUID roleId);
}
