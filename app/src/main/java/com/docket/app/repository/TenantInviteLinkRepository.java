package com.docket.app.repository;

import com.docket.app.entity.TenantInviteLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantInviteLinkRepository extends JpaRepository<TenantInviteLink, UUID> {
    Optional<TenantInviteLink> findByTokenHash(String tokenHash);

    Optional<TenantInviteLink> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TenantInviteLink> findByTenantIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    // Takes one use, but only while the link is still good. Doing the check and the count
    // in one statement means the last use can't be taken twice.
    @Modifying
    @Query("""
            update TenantInviteLink l set l.uses = l.uses + 1
            where l.id = :id and l.revokedAt is null
                and (l.maxUses is null or l.uses < l.maxUses)
                and (l.expiresAt is null or l.expiresAt > :now)
            """)
    int claimUse(@Param("id") UUID id, @Param("now") Instant now);
}
