package com.docket.app.entity;

import com.docket.app.enums.ClockStart;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.OnExpiry;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retention_policies")
@Getter
@Setter
@NoArgsConstructor
public class RetentionPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID folderId;
    private Integer retentionDays;
    @Enumerated(EnumType.STRING)
    private LockMode lockMode;
    @Enumerated(EnumType.STRING)
    private ClockStart clockStart;
    private Boolean appliesToSubfolders;
    @Enumerated(EnumType.STRING)
    private OnExpiry onExpiry;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
