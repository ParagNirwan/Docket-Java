package com.docket.app.entity;

import com.docket.app.enums.FileStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
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
    @Column(name = "status")
    private FileStatus fileStatus;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

}
