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

    // Find all active documents in a specific folder, with pagination.
    // Used by the "list documents" endpoint.
    Page<Document> findByTenantIdAndFolderIdAndFileStatus(UUID tenantId, UUID folderId, FileStatus status, Pageable page);

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
