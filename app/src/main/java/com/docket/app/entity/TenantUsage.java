package com.docket.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantUsage {
    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private Long storageBytesUsed = 0L;

    @Column(nullable = false)
    private Long documentCount = 0L;

    private Instant updatedAt = Instant.now();
}

