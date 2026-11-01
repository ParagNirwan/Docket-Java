package com.docket.app.repository;

import com.docket.app.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByTenantIdAndIsTrayIsTrue(UUID tenantId);
    Optional<Folder> findByTenantIdAndIsTrayIsTrueAndId(UUID tenantId, UUID id);
    List<Folder> findByTenantIdAndParentFolderId(UUID tenantId, UUID parentFolderId);
    Optional<Folder> findByTenantIdAndIsTrayIsTrueAndIsDefaultIsTrueAndName(UUID tenantId, String name);
    Optional<Folder> findFirstByTenantIdAndIsArchiveIsTrueAndIsTrayIsTrue(UUID tenantId);

    boolean existsByTenantIdAndParentFolderIdAndNameIgnoreCaseAndIdNot(
            UUID tenantId, UUID parentFolderId, String name, UUID id);

    // Is candidateId this folder, or anywhere underneath it?
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM folders WHERE id = :folderId
                UNION ALL
                SELECT child.id FROM folders child JOIN subtree ON child.parent_folder_id = subtree.id
            )
            SELECT EXISTS (SELECT 1 FROM subtree WHERE id = :candidateId)
            """, nativeQuery = true)
    boolean isSameOrDescendant(@Param("folderId") UUID folderId, @Param("candidateId") UUID candidateId);

    // A folder's tray decides who can see it, so everything below a moved folder has to
    // point at the new tray too. The visibility column is only a copy, kept for display.
    @Modifying
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM folders WHERE parent_folder_id = :folderId
                UNION ALL
                SELECT child.id FROM folders child JOIN subtree ON child.parent_folder_id = subtree.id
            )
            UPDATE folders
            SET root_tray_id = :rootTrayId, visibility = :visibility, updated_at = now()
            WHERE id IN (SELECT id FROM subtree)
            """, nativeQuery = true)
    int updateDescendantsTray(
            @Param("folderId") UUID folderId,
            @Param("rootTrayId") UUID rootTrayId,
            @Param("visibility") String visibility);
}
