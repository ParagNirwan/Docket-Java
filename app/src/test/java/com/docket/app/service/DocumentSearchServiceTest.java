package com.docket.app.service;

import com.docket.app.entity.Tenant;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentSearchRepository;
import com.docket.app.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentSearchServiceTest {
    private static final String SLUG = "acme";

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private FolderAccessService folderAccessService;
    @Mock
    private DocumentSearchRepository documentSearchRepository;

    @InjectMocks
    private DocumentSearchService service;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
    }

    private DocumentSearchService.Filter filter(String query, String type) {
        return new DocumentSearchService.Filter(query, type, null, null, null);
    }

    private DocumentSearchRepository.Criteria searchedWith() {
        ArgumentCaptor<DocumentSearchRepository.Criteria> criteria =
                ArgumentCaptor.forClass(DocumentSearchRepository.Criteria.class);
        verify(documentSearchRepository).search(criteria.capture(), anyInt(), anyInt());
        return criteria.getValue();
    }

    @Test
    void requiresDocumentsRead() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requirePermission(authentication, tenantId, Permission.DOCUMENTS_READ);

        assertThrows(ResponseStatusException.class, () -> service.search(SLUG, filter("report", null), 0, 20, authentication));
        verifyNoInteractions(documentSearchRepository);
    }

    @Test
    void peopleWhoCannotSeeRestrictedTraysDoNotSearchThem() {
        when(folderAccessService.canAccessRestrictedTrays(authentication, tenantId)).thenReturn(false);

        service.search(SLUG, filter(" report ", null), 0, 20, authentication);

        DocumentSearchRepository.Criteria criteria = searchedWith();
        assertEquals("report", criteria.query());
        assertEquals(tenantId, criteria.tenantId());
        assertFalse(criteria.includeRestricted());
        assertNull(criteria.fileNamePattern());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a"})
    void rejectsQueriesTooShortToMatchUsefully(String query) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.search(SLUG, filter(query, null), 0, 20, authentication));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void rejectsAnUnknownFileType() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.search(SLUG, filter("report", "exe"), 0, 20, authentication));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void translatesAFileTypeIntoAFixedPattern() {
        service.search(SLUG, filter("report", "word"), 0, 20, authentication);

        assertEquals("\\.docx?$", searchedWith().fileNamePattern());
    }

    @Test
    void capsThePageSize() {
        service.search(SLUG, filter("report", null), -1, 5000, authentication);

        verify(documentSearchRepository).search(any(), eq(0), eq(50));
    }
}
