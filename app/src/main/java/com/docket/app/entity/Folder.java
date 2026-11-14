package com.docket.app.entity;

import com.docket.app.enums.Visibility;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "folders")
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @NotNull
    private UUID tenantId;
    private UUID parentFolderId;
    private UUID rootTrayId;
    @NotNull
    private String name;
    private String description;
    @NotNull
    private Boolean isTray;
    @NotNull
    private Boolean isDefault;
    @NotNull
    private Boolean isArchive = false;
    @NotNull
    @Enumerated(EnumType.STRING)
    private Visibility visibility;
    private Integer sortOrder;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

}
