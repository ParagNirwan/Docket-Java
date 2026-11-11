package com.docket.app.repository;

import com.docket.app.service.ai.AiSearchScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

// The SQL itself needs PostgreSQL with pgvector to run. These check how it is put together:
// that visibility is always applied, and that nothing a user chose is written into the SQL.
@ExtendWith(MockitoExtension.class)
class ChunkSearchRepositoryTest {

    private static final String VISIBILITY = "(:includeRestricted OR tray.visibility = 'ALL_MEMBERS')";

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private ChunkSearchRepository repository;

    private final UUID tenantId = UUID.randomUUID();
    private final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);

    @SuppressWarnings("unchecked")
    private void captureQuery() {
        verify(jdbcTemplate).query(sql.capture(), params.capture(), any(RowMapper.class));
    }

    @Test
    void noScope_addsNoFiltersButKeepsTenantStatusAndVisibility() {
        repository.findNearest(tenantId, "[0.1]", false, AiSearchScope.NONE, 30);

        captureQuery();
        assertTrue(sql.getValue().contains("dc.tenant_id = :tenantId"));
        assertTrue(sql.getValue().contains("d.status = 'ACTIVE'"));
        assertTrue(sql.getValue().contains(VISIBILITY));
        assertFalse(sql.getValue().contains(":trayId"));
        assertFalse(sql.getValue().contains("subtree"));
        assertEquals(false, params.getValue().getValue("includeRestricted"));
    }

    @Test
    void fullScope_everyFilterIsABoundParameter() {
        UUID trayId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        Instant after = Instant.parse("2026-01-01T00:00:00Z");
        Instant before = Instant.parse("2026-06-01T00:00:00Z");
        AiSearchScope scope = new AiSearchScope(trayId, folderId, List.of(documentId),
                List.of("Application/PDF"), after, before, List.of(tagId));

        repository.findByKeywords(tenantId, "lease", "[0.1]", true, scope, 30);

        captureQuery();
        String query = sql.getValue();
        assertTrue(query.contains("tray.id = :trayId"));
        assertTrue(query.contains("child.parent_folder_id = subtree.id"));
        assertTrue(query.contains("d.id IN (:documentIds)"));
        assertTrue(query.contains("lower(d.content_type) IN (:contentTypes)"));
        assertTrue(query.contains("d.created_at >= :uploadedAfter"));
        assertTrue(query.contains("d.created_at < :uploadedBefore"));
        assertTrue(query.contains("dt.tag_id IN (:tagIds)"));
        assertTrue(query.contains(VISIBILITY));
        assertTrue(query.contains("CROSS JOIN q"));
        assertFalse(query.contains(trayId.toString()));
        assertFalse(query.toLowerCase().contains("application/pdf"));

        assertEquals(trayId, params.getValue().getValue("trayId"));
        assertEquals(folderId, params.getValue().getValue("folderId"));
        assertEquals(List.of("application/pdf"), params.getValue().getValue("contentTypes"));
        assertEquals(Timestamp.from(after), params.getValue().getValue("uploadedAfter"));
        assertEquals(List.of(tagId), params.getValue().getValue("tagIds"));
    }

    @Test
    void documentOrder_isLimitedToThatDocumentAndStillChecksVisibility() {
        UUID documentId = UUID.randomUUID();

        repository.findInDocumentOrder(tenantId, documentId, false, 200);

        captureQuery();
        assertTrue(sql.getValue().contains("d.id IN (:documentIds)"));
        assertTrue(sql.getValue().contains(VISIBILITY));
        assertEquals(List.of(documentId), params.getValue().getValue("documentIds"));
    }

    @Test
    void summaries_applyVisibilityScopeAndTheDistanceLimit() {
        UUID trayId = UUID.randomUUID();
        AiSearchScope scope = new AiSearchScope(trayId, null, null, null, null, null, null);

        repository.findNearestSummaries(tenantId, "[0.1]", false, scope, 0.45, 6);

        captureQuery();
        assertTrue(sql.getValue().contains("d.tenant_id = :tenantId"));
        assertTrue(sql.getValue().contains(VISIBILITY));
        assertTrue(sql.getValue().contains("tray.id = :trayId"));
        assertTrue(sql.getValue().contains("<= :maxDistance"));
        assertTrue(sql.getValue().contains("-1 AS chunk_index"));
    }
}
