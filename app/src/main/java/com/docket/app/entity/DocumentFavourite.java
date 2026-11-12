package com.docket.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "document_favourites",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tenant_id", "document_id"}),
        indexes = {
                @Index(name = "idx_document_favourites_user_tenant", columnList = "user_id, tenant_id, created_at DESC"),
                @Index(name = "idx_document_favourites_document", columnList = "document_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class DocumentFavourite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
