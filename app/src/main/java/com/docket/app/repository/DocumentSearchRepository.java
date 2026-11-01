package com.docket.app.repository;

import com.docket.app.dto.SearchResultDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Keyword and filename search. Plain SQL because the WHERE clause depends on which
// filters are set, and every filter has to stay a bound parameter.
@Repository
public class DocumentSearchRepository {
    // For each document, the chunk that best matches every word of the query.
    private static final String BEST_CHUNKS = """
            WITH q AS (SELECT websearch_to_tsquery('english', :q) AS query),
            best AS (
                SELECT DISTINCT ON (dc.document_id)
                       dc.document_id, dc.chunk_text, dc.page_start, ts_rank_cd(dc.tsv, q.query) AS rank
                FROM document_chunks dc
                CROSS JOIN q
                WHERE dc.tenant_id = :tenantId AND dc.tsv @@ q.query
                ORDER BY dc.document_id, rank DESC
            )
            """;

    // Joins up to the root tray because its visibility decides who can see the document,
    // the same as AI search. Trash is left out for everyone: it isn't somewhere people
    // expect search results to come from.
    private static final String MATCHES = """
            FROM documents d
            JOIN folders f ON d.folder_id = f.id
            JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
            LEFT JOIN users u ON u.id = d.uploaded_by
            LEFT JOIN best ON best.document_id = d.id
            WHERE d.tenant_id = :tenantId
                AND d.status = 'ACTIVE'
                AND NOT (tray.is_default AND tray.name = 'Trash')
                AND (:includeRestricted OR tray.visibility = 'ALL_MEMBERS')
                AND (d.original_file_name ILIKE :contains
                     OR :q <% d.original_file_name
                     OR best.document_id IS NOT NULL)
            """;

    // chr(2) and chr(3) are the highlight markers described on SearchResultDTO.
    private static final String SELECT = """
            SELECT d.id, d.original_file_name, d.content_type, d.file_size, d.created_at, u.name AS uploader,
                   f.id AS folder_id, f.name AS folder_name, tray.id AS tray_id, tray.name AS tray_name,
                   best.page_start,
                   CASE WHEN best.document_id IS NOT NULL THEN
                       ts_headline('english', best.chunk_text, (SELECT query FROM q),
                                   'MaxWords=35, MinWords=15, StartSel=' || chr(2) || ', StopSel=' || chr(3))
                   END AS snippet
            """;

    // Filename matches first, closest spelling next, then the strongest content match.
    private static final String ORDER = """
            ORDER BY d.original_file_name ILIKE :contains DESC,
                     word_similarity(:q, d.original_file_name) DESC,
                     COALESCE(best.rank, 0) DESC,
                     d.created_at DESC,
                     d.id
            LIMIT :limit OFFSET :offset
            """;

    private static final RowMapper<SearchResultDTO> ROW_MAPPER = (rs, rowNum) -> new SearchResultDTO(
            rs.getObject("id", UUID.class),
            rs.getString("original_file_name"),
            rs.getString("content_type"),
            rs.getLong("file_size"),
            rs.getString("uploader"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("folder_id", UUID.class),
            rs.getString("folder_name"),
            rs.getObject("tray_id", UUID.class),
            rs.getString("tray_name"),
            rs.getString("snippet"),
            rs.getObject("page_start", Integer.class));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DocumentSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<SearchResultDTO> search(Criteria criteria, int page, int size) {
        StringBuilder filters = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", criteria.tenantId())
                .addValue("q", criteria.query())
                .addValue("contains", "%" + escapeLike(criteria.query()) + "%")
                .addValue("includeRestricted", criteria.includeRestricted())
                .addValue("limit", size)
                .addValue("offset", (long) page * size);

        if (criteria.fileNamePattern() != null) {
            filters.append(" AND d.original_file_name ~* :fileNamePattern");
            params.addValue("fileNamePattern", criteria.fileNamePattern());
        }
        if (criteria.trayId() != null) {
            filters.append(" AND tray.id = :trayId");
            params.addValue("trayId", criteria.trayId());
        }
        if (criteria.from() != null) {
            filters.append(" AND d.created_at >= :from");
            params.addValue("from", Timestamp.from(criteria.from()));
        }
        if (criteria.to() != null) {
            filters.append(" AND d.created_at <= :to");
            params.addValue("to", Timestamp.from(criteria.to()));
        }

        long total = jdbcTemplate.queryForObject(
                BEST_CHUNKS + "SELECT count(*) " + MATCHES + filters, params, Long.class);
        List<SearchResultDTO> results = total == 0 ? List.of() : jdbcTemplate.query(
                BEST_CHUNKS + SELECT + MATCHES + filters + "\n" + ORDER, params, ROW_MAPPER);
        return new PageImpl<>(results, PageRequest.of(page, size), total);
    }

    // % and _ in what someone typed should match themselves, not act as wildcards.
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record Criteria(
            UUID tenantId,
            String query,
            boolean includeRestricted,
            String fileNamePattern,
            UUID trayId,
            Instant from,
            Instant to
    ) {
    }
}
