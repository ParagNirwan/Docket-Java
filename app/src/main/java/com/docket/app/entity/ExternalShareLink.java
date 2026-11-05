package com.docket.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_share_links")
@Getter
@Setter
@NoArgsConstructor
public class ExternalShareLink {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    private Instant expiresAt;

    @Column(length = 100)
    private String passwordHash;

    private Integer maxDownloads;

    @Column(nullable = false)
    private int downloadCount = 0;

    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isDownloadLimitReached() {
        return maxDownloads != null && downloadCount >= maxDownloads;
    }
}

