package com.docket.app.repository;

import com.docket.app.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    void deleteByDocumentId(UUID documentId);

    List<DocumentChunk> findByDocumentId(UUID documentId);

    @Query(value = """
            SELECT dc.*, d.original_file_name AS document_name,
                   dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE dc.tenant_id = :tenantId
            ORDER BY dc.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findNearestByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);

    @Query(value = """
            SELECT dc.*, d.original_file_name AS document_name,
                   dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE dc.tenant_id = :tenantId
              AND dc.document_id = :documentId
            ORDER BY dc.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findNearestByTenantAndDocument(
            @Param("tenantId") UUID tenantId,
            @Param("documentId") UUID documentId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);
}
