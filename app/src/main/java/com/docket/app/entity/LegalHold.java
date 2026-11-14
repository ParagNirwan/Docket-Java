package com.docket.app.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "legal_holds")
@Getter
@Setter
@NoArgsConstructor
public class LegalHold {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private String name;
    private String reason;
    private UUID createdBy;
    private Instant createdAt;
    private Instant releasedAt;
    private UUID releasedBy;
}
