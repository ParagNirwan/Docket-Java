package com.docket.app.repository;

import com.docket.app.entity.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {
    List<RetentionPolicy> findByTenantId(UUID tenantId);

    Optional<RetentionPolicy> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByFolderId(UUID folderId);

    List<RetentionPolicy> findByFolderIdIn(Collection<UUID> folderIds);

    // A Compliance policy can't be removed, and deleting its folder would remove it.
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM folders WHERE id = :folderId
                UNION ALL
                SELECT child.id FROM folders child JOIN subtree ON child.parent_folder_id = subtree.id
            )
            SELECT EXISTS (
                SELECT 1 FROM retention_policies p JOIN subtree ON p.folder_id = subtree.id
                WHERE p.lock_mode = 'COMPLIANCE'
            )
            """, nativeQuery = true)
    boolean existsComplianceUnder(@Param("folderId") UUID folderId);
}
