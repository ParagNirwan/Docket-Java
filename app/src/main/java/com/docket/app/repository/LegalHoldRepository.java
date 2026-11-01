package com.docket.app.repository;

import com.docket.app.entity.Document;
import com.docket.app.entity.LegalHold;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// A hold covers deleted documents too: evidence sitting in the Trash must not be purged.
public interface LegalHoldRepository extends JpaRepository<LegalHold, UUID> {
    List<LegalHold> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<LegalHold> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(value = "SELECT count(*) FROM legal_hold_documents WHERE legal_hold_id = :holdId", nativeQuery = true)
    long countDocuments(@Param("holdId") UUID holdId);

    @Modifying
    @Query(value = """
            INSERT INTO legal_hold_documents (legal_hold_id, document_id, added_at)
            SELECT :holdId, d.id, :now FROM documents d
            WHERE d.tenant_id = :tenantId AND d.id IN (:documentIds) AND d.status IN ('ACTIVE', 'SOFT_DELETED')
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int addDocuments(
            @Param("holdId") UUID holdId,
            @Param("tenantId") UUID tenantId,
            @Param("documentIds") Collection<UUID> documentIds,
            @Param("now") Instant now);

    @Modifying
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM folders WHERE id = :folderId AND tenant_id = :tenantId
                UNION ALL
                SELECT child.id FROM folders child JOIN subtree ON child.parent_folder_id = subtree.id
            )
            INSERT INTO legal_hold_documents (legal_hold_id, document_id, added_at)
            SELECT :holdId, d.id, :now FROM documents d
            WHERE d.tenant_id = :tenantId AND d.status IN ('ACTIVE', 'SOFT_DELETED')
                AND (d.folder_id IN (SELECT id FROM subtree) OR d.original_folder_id IN (SELECT id FROM subtree))
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int addFolder(
            @Param("holdId") UUID holdId,
            @Param("tenantId") UUID tenantId,
            @Param("folderId") UUID folderId,
            @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE documents SET legal_hold = true
            WHERE id IN (SELECT document_id FROM legal_hold_documents WHERE legal_hold_id = :holdId)
            """, nativeQuery = true)
    int markDocumentsHeld(@Param("holdId") UUID holdId);

    // Run after the hold is marked released. A document stays held if another hold covers it.
    @Modifying
    @Query(value = """
            UPDATE documents d SET legal_hold = EXISTS (
                SELECT 1 FROM legal_hold_documents other
                JOIN legal_holds h ON h.id = other.legal_hold_id
                WHERE other.document_id = d.id AND h.released_at IS NULL
            )
            WHERE d.id IN (SELECT document_id FROM legal_hold_documents WHERE legal_hold_id = :holdId)
            """, nativeQuery = true)
    int refreshDocumentsHeld(@Param("holdId") UUID holdId);

    @Query(value = """
            SELECT d.* FROM documents d
            JOIN legal_hold_documents x ON x.document_id = d.id
            WHERE x.legal_hold_id = :holdId
            ORDER BY d.original_file_name, d.id
            """, countQuery = "SELECT count(*) FROM legal_hold_documents WHERE legal_hold_id = :holdId", nativeQuery = true)
    Page<Document> findDocuments(@Param("holdId") UUID holdId, Pageable pageable);
}
