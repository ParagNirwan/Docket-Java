package com.docket.app.service;

import com.docket.app.entity.Document;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.FileStatus;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    private static final String SLUG = "acme";

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FolderAccessService folderAccessService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DocumentService documentService;

    private UUID tenantId;
    private UUID folderId;
    private Document document;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        folderId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));

        document = new Document();
        document.setId(UUID.randomUUID());
        document.setTenantId(tenantId);
        document.setFolderId(folderId);
        document.setFileStatus(FileStatus.ACTIVE);
        document.setStoredFileName("stored.pdf");
    }

    private void folderIsHidden() {
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(false);
    }

    private void documentExists() {
        when(documentRepository.findByIdAndTenantId(document.getId(), tenantId)).thenReturn(Optional.of(document));
    }

    private void assertNotFound(Executable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getDocuments_hiddenFolder_throws404() {
        folderIsHidden();

        assertNotFound(() -> documentService.getDocuments(SLUG, folderId, authentication, Pageable.unpaged()));
        verifyNoInteractions(documentRepository);
    }

    @Test
    void uploadDocument_hiddenFolder_throws404AndStoresNothing() {
        folderIsHidden();
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1});

        assertNotFound(() -> documentService.uploadDocument(SLUG, folderId, file, authentication));
        verifyNoInteractions(storageService, documentRepository);
    }

    @Test
    void getDocumentById_hiddenFolder_throws404() {
        documentExists();
        folderIsHidden();

        assertNotFound(() -> documentService.getDocumentById(SLUG, document.getId(), authentication));
    }

    @Test
    void downloadDocument_hiddenFolder_throws404AndReadsNoFile() {
        documentExists();
        folderIsHidden();

        assertNotFound(() -> documentService.downloadDocument(SLUG, document.getId(), authentication));
        verifyNoInteractions(storageService);
    }

    @Test
    void softDeleteDocument_hiddenFolder_throws404AndDeletesNothing() {
        documentExists();
        folderIsHidden();

        assertNotFound(() -> documentService.softDeleteDocument(SLUG, document.getId(), authentication));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void downloadDocument_visibleFolder_returnsFile() {
        documentExists();
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(storageService.retrieve("stored.pdf")).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        assertSame(document, documentService.downloadDocument(SLUG, document.getId(), authentication).document());
    }
}
