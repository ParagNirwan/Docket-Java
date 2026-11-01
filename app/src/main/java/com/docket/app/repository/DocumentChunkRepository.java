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
        //
        // They all return the same columns in the same order, which HybridRetriever reads
        // by position: document_id, document_name, chunk_index, chunk_text, page_start,
        // page_end, heading, distance, exact_match.

        @Query(value = """
                        SELECT dc.document_id, d.original_file_name AS document_name, dc.chunk_index,
                               dc.chunk_text, dc.page_start, dc.page_end, dc.heading,
                               dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance,
                               FALSE AS exact_match
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
                        SELECT dc.document_id, d.original_file_name AS document_name, dc.chunk_index,
                               dc.chunk_text, dc.page_start, dc.page_end, dc.heading,
                               dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance,
                               FALSE AS exact_match
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

        // The keyword searches match any word of the query, because websearch_to_tsquery
        // alone would require every word of a question to be in the same chunk.
        // exact_match says whether the chunk does have all of them. Those rank first, or
        // a chunk repeating one common word would beat a chunk with the whole question.

        @Query(value = """
                        WITH q AS (
                            SELECT websearch_to_tsquery('english', :keywordQuery) AS all_words,
                                   CAST(replace(CAST(websearch_to_tsquery('english', :keywordQuery) AS text),
                                                ' & ', ' | ') AS tsquery) AS any_word
                        )
                        SELECT dc.document_id, d.original_file_name AS document_name, dc.chunk_index,
                               dc.chunk_text, dc.page_start, dc.page_end, dc.heading,
                               dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance,
                               dc.tsv @@ q.all_words AS exact_match
                        FROM document_chunks dc
                        CROSS JOIN q
                        JOIN documents d ON dc.document_id = d.id
                        JOIN folders f ON d.folder_id = f.id
                        JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
                        WHERE dc.tenant_id = :tenantId
                                AND dc.tsv @@ q.any_word
                                AND d.status = 'ACTIVE'
                                AND d.indexing_status = 'COMPLETED'
                                AND (:includeRestricted OR tray.visibility = 'ALL_MEMBERS')
                        ORDER BY dc.tsv @@ q.all_words DESC, ts_rank_cd(dc.tsv, q.any_word) DESC
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findByKeywordsInTenant(
                        @Param("tenantId") UUID tenantId,
                        @Param("keywordQuery") String keywordQuery,
                        @Param("queryEmbedding") String queryEmbedding,
                        @Param("includeRestricted") boolean includeRestricted,
                        @Param("limit") int limit);

        @Query(value = """
                        WITH q AS (
                            SELECT websearch_to_tsquery('english', :keywordQuery) AS all_words,
                                   CAST(replace(CAST(websearch_to_tsquery('english', :keywordQuery) AS text),
                                                ' & ', ' | ') AS tsquery) AS any_word
                        )
                        SELECT dc.document_id, d.original_file_name AS document_name, dc.chunk_index,
                               dc.chunk_text, dc.page_start, dc.page_end, dc.heading,
                               dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance,
                               dc.tsv @@ q.all_words AS exact_match
                        FROM document_chunks dc
                        CROSS JOIN q
                        JOIN documents d ON dc.document_id = d.id
                        JOIN folders f ON d.folder_id = f.id
                        JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
                        WHERE dc.tenant_id = :tenantId
                                AND dc.document_id = :documentId
                                AND dc.tsv @@ q.any_word
                                AND d.status = 'ACTIVE'
                                AND d.indexing_status = 'COMPLETED'
                                AND (:includeRestricted OR tray.visibility = 'ALL_MEMBERS')
                        ORDER BY dc.tsv @@ q.all_words DESC, ts_rank_cd(dc.tsv, q.any_word) DESC
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findByKeywordsInDocument(
                        @Param("tenantId") UUID tenantId,
                        @Param("documentId") UUID documentId,
                        @Param("keywordQuery") String keywordQuery,
                        @Param("queryEmbedding") String queryEmbedding,
                        @Param("includeRestricted") boolean includeRestricted,
                        @Param("limit") int limit);

        @Query(value = """
                        SELECT dc.document_id, d.original_file_name AS document_name, dc.chunk_index,
                               dc.chunk_text, dc.page_start, dc.page_end, dc.heading,
                               CAST(NULL AS double precision) AS distance,
                               FALSE AS exact_match
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
