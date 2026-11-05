package com.docket.app.entity;

import com.docket.app.enums.ScanStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private Long fileSize;

    private String checksumSha256;

    @Column(nullable = false)
    private String contentType;

    private UUID uploadedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus scanStatus = ScanStatus.PENDING;

    private Instant scannedAt;
}
