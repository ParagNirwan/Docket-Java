package com.docket.app.entity;

import com.docket.app.enums.FileStatus;
import com.docket.app.enums.LockMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import com.docket.app.enums.IndexingStatus;

import java.time.Instant;
import java.util.UUID;

// Only changed columns are written. Processing updates the indexing status while other
// requests change a document, and saving the whole row would undo what they wrote.
@Entity
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID folderId;
    private UUID uploadedBy;
    private String originalFileName;
    private String storedFileName;
    private String contentType;
    private Long fileSize;
    private String checksumSha256;
    @Enumerated(EnumType.STRING)
    private com.docket.app.enums.ScanStatus scanStatus;
    private Instant scannedAt;
    private String scanDetail;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, Object> metadata = new java.util.HashMap<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "document_tags",
        joinColumns = @JoinColumn(name = "document_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private java.util.Set<Tag> tags = new java.util.HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private FileStatus fileStatus;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    @Enumerated(EnumType.STRING)
    private IndexingStatus indexingStatus;

    // Written by DocumentSummaryService after indexing, through its own queries. The embedding
    // of the summary is only ever read in SQL, so it isn't mapped here.
    @Column(columnDefinition = "TEXT")
    private String aiSummary;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.List<String> aiKeyPoints;
    private Instant aiSummaryGeneratedAt;

    // Retention: see RetentionService. A document is locked while retainUntil is in the future.
    private Instant retainUntil;
    @Enumerated(EnumType.STRING)
    private LockMode lockMode;
    private UUID retentionPolicyId;
    private Instant retentionClockAt;
    private Instant expiringNotifiedAt;
    private Instant expiryHandledAt;
    private boolean legalHold;

    private Instant archivedAt;
    private UUID archivedFromFolderId;

    // Trash: folderId points at the Trash tray while a document is deleted.
    private Instant deletedAt;
    private UUID deletedBy;
    private UUID originalFolderId;

    public boolean isRetentionLocked(Instant now) {
        return retainUntil != null && retainUntil.isAfter(now);
    }
}
