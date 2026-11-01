package com.docket.app.entity;

import com.docket.app.enums.InvitationStatus;
import com.docket.app.enums.Roles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// An offer to join an organization, sent to an email address. See InvitationService.
@Entity
@Table(name = "tenant_invitations")
@Getter
@Setter
@NoArgsConstructor
public class TenantInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID tenantId;

    private String email;

    @Enumerated(EnumType.STRING)
    private Roles role;

    private String tokenHash;

    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    private InvitationStatus status;

    private Instant expiresAt;

    private Instant createdAt;

    private Instant acceptedAt;
}
