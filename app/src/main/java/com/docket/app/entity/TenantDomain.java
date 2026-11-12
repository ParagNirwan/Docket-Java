package com.docket.app.entity;

import com.docket.app.enums.DomainJoinMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "tenant_domains",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "domain"})
)
@Getter
@Setter
@NoArgsConstructor
public class TenantDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String domain;

    @Column(name = "verification_token", nullable = false)
    private String verificationToken;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_mode", nullable = false)
    private DomainJoinMode joinMode = DomainJoinMode.REQUEST;

    @Column(name = "sso_required", nullable = false)
    private boolean ssoRequired = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
