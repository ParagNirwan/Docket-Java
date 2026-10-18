package com.docket.app.repository;

import com.docket.app.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByTenantIdAndIsTrayIsTrue(UUID tenantId);
    Optional<Folder> findByTenantIdAndIsTrayIsTrueAndId(UUID tenantId, UUID id);
    List<Folder> findByTenantIdAndParentFolderId(UUID tenantId, UUID parentFolderId);
}
