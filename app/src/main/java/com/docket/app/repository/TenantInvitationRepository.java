package com.docket.app.repository;

import com.docket.app.entity.TenantInvitation;
import com.docket.app.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID> {
    Optional<TenantInvitation> findByTokenHash(String tokenHash);

    Optional<TenantInvitation> findByIdAndTenantIdAndStatus(UUID id, UUID tenantId, InvitationStatus status);

    Optional<TenantInvitation> findByTenantIdAndEmailIgnoreCaseAndStatus(UUID tenantId, String email, InvitationStatus status);

    List<TenantInvitation> findByTenantIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID tenantId, InvitationStatus status, Instant now);

    // Only one request can move an invite out of PENDING, so a double click or a link
    // opened in two tabs can't accept it twice.
    @Modifying
    @Query("""
            update TenantInvitation i set i.status = com.docket.app.enums.InvitationStatus.ACCEPTED, i.acceptedAt = :now
            where i.id = :id and i.status = com.docket.app.enums.InvitationStatus.PENDING
            """)
    int markAccepted(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("""
            update TenantInvitation i set i.status = com.docket.app.enums.InvitationStatus.EXPIRED
            where i.status = com.docket.app.enums.InvitationStatus.PENDING and i.expiresAt < :now
            """)
    int expirePending(@Param("now") Instant now);
}
