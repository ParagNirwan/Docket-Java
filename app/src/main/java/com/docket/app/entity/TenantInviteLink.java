package com.docket.app.entity;

import com.docket.app.enums.Roles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// A link anyone can use to join an organization with a set role. See InviteLinkService.
@Entity
@Table(name = "tenant_invite_links")
@Getter
@Setter
@NoArgsConstructor
public class TenantInviteLink {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID tenantId;

    private String tokenHash;

    @Enumerated(EnumType.STRING)
    private Roles role;

    // Null means any number of people can use it
    private Integer maxUses;

    @Column(nullable = false)
    private int uses;

    // Null means it never expires
    private Instant expiresAt;

    private UUID createdBy;

    private Instant createdAt;

    private Instant revokedAt;
}
