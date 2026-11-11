package com.docket.app.entity;

import com.docket.app.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private String slug;
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    private TenantStatus status;
    private Integer trashRetentionDays = 30;
    private Long storageQuotaBytes;
    // Whether the AI may write a summary of each document. See DocumentSummaryService.
    private boolean aiSummariesEnabled = true;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
