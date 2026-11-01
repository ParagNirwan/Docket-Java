package com.docket.app.repository;

import com.docket.app.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

        @Transactional
        void deleteByDocumentId(UUID documentId);

        List<DocumentChunk> findByDocumentId(UUID documentId);

        // Each search below joins up to the document's root tray, because the tray's
        // visibility decides who can see it. When includeRestricted is false, only
        // chunks from ALL_MEMBERS trays can be returned.

        @Query(value = """
                        SELECT dc.*, d.original_file_name AS document_name,
                               dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance
                        FROM document_chunks dc
                        JOIN documents d ON dc.document_id = d.id
                        JOIN folders f ON d.folder_id = f.id
                        JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
                        WHERE dc.tenant_id = :tenantId
                                AND d.status = 'ACTIVE'
                                AND d.indexing_status = 'COMPLETED'
                                AND (:includeRestricted OR tray.visibility = 'ALL_MEMBERS')
                        ORDER BY dc.embedding <=> CAST(:queryEmbedding AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findNearestByTenant(
                        @Param("tenantId") UUID tenantId,
                        @Param("queryEmbedding") String queryEmbedding,
                        @Param("includeRestricted") boolean includeRestricted,
                        @Param("limit") int limit);

        @Query(value = """
                        SELECT dc.*, d.original_file_name AS document_name,
                               dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance
                        FROM document_chunks dc
                        JOIN documents d ON dc.document_id = d.id
                        JOIN folders f ON d.folder_id = f.id
                        JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
                        WHERE dc.tenant_id = :tenantId
                                AND dc.document_id = :documentId
                                AND d.status = 'ACTIVE'
                                AND d.indexing_status = 'COMPLETED'
                                AND (:includeRestricted OR tray.visibility = 'ALL_MEMBERS')
                        ORDER BY dc.embedding <=> CAST(:queryEmbedding AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findNearestByTenantAndDocument(
                        @Param("tenantId") UUID tenantId,
                        @Param("documentId") UUID documentId,
                        @Param("queryEmbedding") String queryEmbedding,
                        @Param("includeRestricted") boolean includeRestricted,
                        @Param("limit") int limit);

        @Query(value = """
                        SELECT dc.*, d.original_file_name AS document_name,
                               0.0 AS distance
                        FROM document_chunks dc
                        JOIN documents d ON dc.document_id = d.id
                        JOIN folders f ON d.folder_id = f.id
                        JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
                        WHERE dc.tenant_id = :tenantId
                                AND dc.document_id = :documentId
                                AND d.status = 'ACTIVE'
                                AND d.indexing_status = 'COMPLETED'
                                AND (:includeRestricted OR tray.visibility = 'ALL_MEMBERS')
                        ORDER BY dc.chunk_index
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findByDocumentOrdered(
                        @Param("tenantId") UUID tenantId,
                        @Param("documentId") UUID documentId,
                        @Param("includeRestricted") boolean includeRestricted,
                        @Param("limit") int limit);
}
