package com.docket.app.repository;

import com.docket.app.service.ai.AiSearchScope;
import com.docket.app.service.ai.RetrievedChunk;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

// The searches behind AI answers. Plain SQL because the WHERE clause depends on which
// scope filters are set, and every filter has to stay a bound parameter.
//
// Each search joins up to the document's root tray, because the tray's visibility decides
// who can see it. When includeRestricted is false, only ALL_MEMBERS trays can be returned.
// A scope can only add conditions to that, never replace it.
@Repository
public class ChunkSearchRepository {

    private static final String CHUNK_COLUMNS = """
            SELECT dc.document_id, d.original_file_name AS document_name, dc.chunk_index,
                   dc.chunk_text, dc.page_start, dc.page_end, dc.heading,
            """;

    private static final String CHUNKS = """
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            JOIN folders f ON d.folder_id = f.id
            JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
            WHERE dc.tenant_id = :tenantId
                AND d.status = 'ACTIVE'
                AND d.indexing_status = 'COMPLETED'
                AND (:includeRestricted OR tray.visibility = 'ALL_MEMBERS')
            """;

    // The keyword search matches any word of the query, because websearch_to_tsquery alone
    // would require every word of a question to be in the same chunk. exact_match says
    // whether the chunk does have all of them. Those rank first, or a chunk repeating one
    // common word would beat a chunk with the whole question.
    private static final String KEYWORD_QUERY = """
            WITH parsed AS (
                SELECT websearch_to_tsquery('english', :keywordQuery) AS all_words
            ), q AS (
                SELECT all_words,
                       CASE WHEN numnode(all_words) = 0 THEN CAST(NULL AS tsquery)
                            ELSE CAST(replace(CAST(all_words AS text), ' & ', ' | ') AS tsquery)
                       END AS any_word
                FROM parsed
            )
            """;

    private static final String KEYWORD_CHUNKS = CHUNKS.replace(
            "FROM document_chunks dc\n", "FROM document_chunks dc\nCROSS JOIN q\n");

    private static final String FOLDER_AND_BELOW = """
             AND d.folder_id IN (
                WITH RECURSIVE subtree AS (
                    SELECT id FROM folders WHERE id = :folderId AND tenant_id = :tenantId
                    UNION ALL
                    SELECT child.id FROM folders child JOIN subtree ON child.parent_folder_id = subtree.id
                )
                SELECT id FROM subtree)
            """;

    private static final RowMapper<RetrievedChunk> ROW_MAPPER = (rs, rowNum) -> new RetrievedChunk(
            rs.getObject("document_id", UUID.class),
            rs.getString("document_name"),
            rs.getInt("chunk_index"),
            rs.getString("chunk_text"),
            rs.getObject("page_start", Integer.class),
            rs.getObject("page_end", Integer.class),
            rs.getString("heading"),
            rs.getObject("distance", Double.class),
            rs.getBoolean("exact_match"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ChunkSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Closest in meaning first
    public List<RetrievedChunk> findNearest(UUID tenantId, String queryEmbedding, boolean includeRestricted,
                                            AiSearchScope scope, int limit) {
        MapSqlParameterSource params = baseParams(tenantId, includeRestricted, limit)
                .addValue("queryEmbedding", queryEmbedding);
        String sql = CHUNK_COLUMNS + """
                       dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance,
                       FALSE AS exact_match
                """ + CHUNKS + scopeFilters(scope, params) + """
                ORDER BY dc.embedding <=> CAST(:queryEmbedding AS vector)
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    public List<RetrievedChunk> findByKeywords(UUID tenantId, String keywordQuery, String queryEmbedding,
                                               boolean includeRestricted, AiSearchScope scope, int limit) {
        MapSqlParameterSource params = baseParams(tenantId, includeRestricted, limit)
                .addValue("keywordQuery", keywordQuery)
                .addValue("queryEmbedding", queryEmbedding);
        String sql = KEYWORD_QUERY + CHUNK_COLUMNS + """
                       dc.embedding <=> CAST(:queryEmbedding AS vector) AS distance,
                       dc.tsv @@ q.all_words AS exact_match
                """ + KEYWORD_CHUNKS + " AND dc.tsv @@ q.any_word\n" + scopeFilters(scope, params) + """
                ORDER BY dc.tsv @@ q.all_words DESC, ts_rank_cd(dc.tsv, q.any_word) DESC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    // A document from its start, for summarizing it. There is no distance to report.
    public List<RetrievedChunk> findInDocumentOrder(UUID tenantId, UUID documentId, boolean includeRestricted,
                                                    int limit) {
        MapSqlParameterSource params = baseParams(tenantId, includeRestricted, limit);
        String sql = CHUNK_COLUMNS + """
                       CAST(NULL AS double precision) AS distance,
                       FALSE AS exact_match
                """ + CHUNKS + scopeFilters(AiSearchScope.ofDocument(documentId), params) + """
                ORDER BY dc.chunk_index
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    // Documents whose AI summary is close to the question. Each comes back shaped like a
    // chunk, so that it can be cited like one.
    public List<RetrievedChunk> findNearestSummaries(UUID tenantId, String queryEmbedding, boolean includeRestricted,
                                                     AiSearchScope scope, double maxDistance, int limit) {
        MapSqlParameterSource params = baseParams(tenantId, includeRestricted, limit)
                .addValue("queryEmbedding", queryEmbedding)
                .addValue("maxDistance", maxDistance);
        String sql = "SELECT d.id AS document_id, d.original_file_name AS document_name, "
                + RetrievedChunk.SUMMARY_INDEX + " AS chunk_index, d.ai_summary AS chunk_text, '"
                + RetrievedChunk.SUMMARY_HEADING + "' AS heading,\n" + """
                       CAST(NULL AS integer) AS page_start, CAST(NULL AS integer) AS page_end,
                       d.ai_summary_embedding <=> CAST(:queryEmbedding AS vector) AS distance,
                       FALSE AS exact_match
                FROM documents d
                JOIN folders f ON d.folder_id = f.id
                JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
                WHERE d.tenant_id = :tenantId
                    AND d.status = 'ACTIVE'
                    AND d.indexing_status = 'COMPLETED'
                    AND d.ai_summary_embedding IS NOT NULL
                    AND (:includeRestricted OR tray.visibility = 'ALL_MEMBERS')
                    AND d.ai_summary_embedding <=> CAST(:queryEmbedding AS vector) <= :maxDistance
                """ + scopeFilters(scope, params) + """
                ORDER BY d.ai_summary_embedding <=> CAST(:queryEmbedding AS vector)
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    private static MapSqlParameterSource baseParams(UUID tenantId, boolean includeRestricted, int limit) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("includeRestricted", includeRestricted)
                .addValue("limit", limit);
    }

    // Only fixed text is added to the SQL. Everything the user chose is a bound parameter.
    private static String scopeFilters(AiSearchScope scope, MapSqlParameterSource params) {
        StringBuilder filters = new StringBuilder();
        if (scope.trayId() != null) {
            filters.append(" AND tray.id = :trayId\n");
            params.addValue("trayId", scope.trayId());
        }
        if (scope.folderId() != null) {
            filters.append(FOLDER_AND_BELOW);
            params.addValue("folderId", scope.folderId());
        }
        if (!scope.documentIds().isEmpty()) {
            filters.append(" AND d.id IN (:documentIds)\n");
            params.addValue("documentIds", scope.documentIds());
        }
        if (!scope.contentTypes().isEmpty()) {
            filters.append(" AND lower(d.content_type) IN (:contentTypes)\n");
            params.addValue("contentTypes", scope.contentTypes());
        }
        if (scope.uploadedAfter() != null) {
            filters.append(" AND d.created_at >= :uploadedAfter\n");
            params.addValue("uploadedAfter", Timestamp.from(scope.uploadedAfter()));
        }
        if (scope.uploadedBefore() != null) {
            filters.append(" AND d.created_at < :uploadedBefore\n");
            params.addValue("uploadedBefore", Timestamp.from(scope.uploadedBefore()));
        }
        if (!scope.tagIds().isEmpty()) {
            filters.append("""
                     AND EXISTS (SELECT 1 FROM document_tags dt
                                 WHERE dt.document_id = d.id AND dt.tag_id IN (:tagIds))
                    """);
            params.addValue("tagIds", scope.tagIds());
        }
        return filters.toString();
    }
}
