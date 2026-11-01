package com.docket.app.repository;

import com.docket.app.entity.Document;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.IndexingStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    String SUBTREE = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM folders WHERE id = :folderId
                UNION ALL
                SELECT child.id FROM folders child JOIN subtree ON child.parent_folder_id = subtree.id
            )
            """;

    // Find all active documents in a specific folder, with pagination.
    // Used by the "list documents" endpoint.
    Page<Document> findByTenantIdAndFolderIdAndFileStatus(UUID tenantId, UUID folderId, FileStatus status, Pageable page);

    Page<Document> findByTenantIdAndFileStatus(UUID tenantId, FileStatus status, Pageable page);

    // Find one specific document by its ID, but only if it belongs to
    // the given tenant. This prevents users in one organization from
    // accessing documents that belong to a different organization.
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndFolderIdAndFileStatusAndOriginalFileNameIgnoreCase(
            UUID tenantId, UUID folderId, FileStatus status, String originalFileName);

    boolean existsByTenantIdAndFolderIdAndFileStatusAndOriginalFileNameIgnoreCaseAndIdNot(
            UUID tenantId, UUID folderId, FileStatus status, String originalFileName, UUID id);

    // Changes only these columns, so a rename can't undo an indexing status written while
    // the document was being processed.
    @Transactional
    @Modifying
    @Query("UPDATE Document d SET d.originalFileName = :name, d.folderId = :folderId, d.updatedAt = :now WHERE d.id = :id")
    void updateNameAndFolder(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("folderId") UUID folderId,
            @Param("now") Instant now);

    @Query(value = SUBTREE + """
            SELECT EXISTS (
                SELECT 1 FROM documents d JOIN subtree ON d.folder_id = subtree.id
                WHERE d.legal_hold OR d.retain_until > now()
            )
            """, nativeQuery = true)
    boolean existsLockedUnder(@Param("folderId") UUID folderId);

    @Query(value = SUBTREE + """
            SELECT EXISTS (
                SELECT 1 FROM documents d JOIN subtree ON d.folder_id = subtree.id
                WHERE d.status = 'ACTIVE' AND d.archived_at IS NOT NULL
            )
            """, nativeQuery = true)
    boolean existsArchivedUnder(@Param("folderId") UUID folderId);

    @Query(value = SUBTREE + """
            SELECT count(*) FROM documents d JOIN subtree ON d.folder_id = subtree.id
            WHERE d.status = 'ACTIVE'
            """, nativeQuery = true)
    long countActiveUnder(@Param("folderId") UUID folderId);

    @Transactional
    @Modifying
    @Query(value = SUBTREE + """
            UPDATE documents
            SET status = 'SOFT_DELETED', original_folder_id = folder_id, folder_id = :trashId,
                deleted_at = :now, deleted_by = :actorId, updated_at = :now
            WHERE status = 'ACTIVE' AND folder_id IN (SELECT id FROM subtree)
            """, nativeQuery = true)
    int trashSubtree(
            @Param("folderId") UUID folderId,
            @Param("trashId") UUID trashId,
            @Param("actorId") UUID actorId,
            @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query(value = SUBTREE + """
            UPDATE documents SET archived_at = :now
            WHERE status = 'ACTIVE' AND archived_at IS NULL AND folder_id IN (SELECT id FROM subtree)
            """, nativeQuery = true)
    int stampArchivedUnder(@Param("folderId") UUID folderId, @Param("now") Instant now);

    // The set-based form of RetentionService.applyOnEntry. GREATEST ignores nulls, so a
    // date can only move later, and Compliance is never downgraded. Keep the two in step.
    @Transactional
    @Modifying
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM folders WHERE id = :folderId
                UNION ALL
                SELECT child.id FROM folders child JOIN subtree ON child.parent_folder_id = subtree.id
                WHERE :includeSubfolders
            ), terms AS (
                SELECT d.id,
                       COALESCE(d.archived_at, d.retention_clock_at, now()) AS clock,
                       CASE WHEN :clockStart = 'UPLOADED_AT' THEN d.created_at
                            ELSE COALESCE(d.archived_at, d.retention_clock_at, now())
                       END + make_interval(days => :days) AS until
                FROM documents d JOIN subtree ON d.folder_id = subtree.id
                WHERE d.status = 'ACTIVE'
            )
            UPDATE documents d
            SET retain_until = GREATEST(d.retain_until, t.until),
                lock_mode = CASE WHEN (d.lock_mode = 'COMPLIANCE' AND d.retain_until > now()) OR :lockMode = 'COMPLIANCE' THEN 'COMPLIANCE'
                                 ELSE 'GOVERNANCE' END,
                retention_policy_id = CASE WHEN d.retain_until IS NULL OR t.until >= d.retain_until THEN :policyId
                                           ELSE d.retention_policy_id END,
                retention_clock_at = t.clock,
                expiring_notified_at = CASE WHEN d.retain_until IS NULL OR t.until > d.retain_until THEN NULL
                                            ELSE d.expiring_notified_at END,
                expiry_handled_at = CASE WHEN d.retain_until IS NULL OR t.until > d.retain_until THEN NULL
                                         ELSE d.expiry_handled_at END
            FROM terms t
            WHERE d.id = t.id
            """, nativeQuery = true)
    int applyRetentionPolicy(
            @Param("folderId") UUID folderId,
            @Param("includeSubfolders") boolean includeSubfolders,
            @Param("policyId") UUID policyId,
            @Param("days") int days,
            @Param("lockMode") String lockMode,
            @Param("clockStart") String clockStart);

    @Query("""
            SELECT count(d) FROM Document d
            WHERE d.tenantId = :tenantId AND d.fileStatus = com.docket.app.enums.FileStatus.ACTIVE
                AND (d.legalHold = true OR d.retainUntil > :now)
            """)
    long countLocked(@Param("tenantId") UUID tenantId, @Param("now") Instant now);

    @Query("""
            SELECT d.retentionPolicyId, count(d) FROM Document d
            WHERE d.tenantId = :tenantId AND d.fileStatus = com.docket.app.enums.FileStatus.ACTIVE
                AND d.retentionPolicyId IS NOT NULL AND d.retainUntil > :now
            GROUP BY d.retentionPolicyId
            """)
    List<Object[]> countLockedByPolicy(@Param("tenantId") UUID tenantId, @Param("now") Instant now);

    @Query("""
            SELECT count(d) FROM Document d
            WHERE d.tenantId = :tenantId AND d.fileStatus = com.docket.app.enums.FileStatus.ACTIVE
                AND d.retentionPolicyId = :policyId AND d.retainUntil > :now
            """)
    long countLockedForPolicy(@Param("tenantId") UUID tenantId, @Param("policyId") UUID policyId, @Param("now") Instant now);

    @Query("""
            SELECT d FROM Document d
            WHERE d.tenantId = :tenantId AND d.fileStatus = com.docket.app.enums.FileStatus.ACTIVE
                AND d.retainUntil > :now AND d.retainUntil <= :horizon
            ORDER BY d.retainUntil
            """)
    List<Document> findExpiringBetween(
            @Param("tenantId") UUID tenantId,
            @Param("now") Instant now,
            @Param("horizon") Instant horizon,
            Pageable page);

    @Query("""
            SELECT count(d) FROM Document d
            WHERE d.tenantId = :tenantId AND d.fileStatus = com.docket.app.enums.FileStatus.ACTIVE
                AND d.retainUntil > :now AND d.retainUntil <= :horizon
            """)
    long countExpiringBetween(@Param("tenantId") UUID tenantId, @Param("now") Instant now, @Param("horizon") Instant horizon);

    // Processing can take minutes. These two updates change only the indexing columns, so
    // they can't undo a delete or a move that happened to the document in the meantime.
    @Transactional
    @Modifying
    @Query("UPDATE Document d SET d.indexingStatus = :status WHERE d.id = :id")
    void updateIndexingStatus(@Param("id") UUID id, @Param("status") IndexingStatus status);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE documents
            SET indexing_status = :status, embedding_version = :version,
                reindex_attempts = 0, reindex_claimed_at = NULL
            WHERE id = :id
            """, nativeQuery = true)
    void markIndexed(@Param("id") UUID id, @Param("status") String status, @Param("version") int version);

    // Claims the next documents below the current embedding version. SKIP LOCKED lets several
    // pods work side by side, and if a pod dies the claim is free again once it passes
    // claimedBefore. Documents still waiting for their first processing are left alone.
    @Transactional
    @Query(value = """
            UPDATE documents
            SET reindex_attempts = reindex_attempts + 1, reindex_claimed_at = now()
            WHERE id IN (
                SELECT id FROM documents
                WHERE status = 'ACTIVE'
                    AND embedding_version < :version
                    AND indexing_status IN ('COMPLETED', 'FAILED', 'NOT_PROCESSABLE')
                    AND reindex_attempts < :maxAttempts
                    AND (reindex_claimed_at IS NULL OR reindex_claimed_at < :claimedBefore)
                ORDER BY created_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id
            """, nativeQuery = true)
    List<UUID> claimForReindex(
            @Param("version") int version,
            @Param("maxAttempts") int maxAttempts,
            @Param("claimedBefore") Instant claimedBefore,
            @Param("batchSize") int batchSize);
}
