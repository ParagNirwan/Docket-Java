package com.docket.app.service;

import com.docket.app.dto.CreateShareLinkRequestDTO;
import com.docket.app.dto.PublicShareInfoDTO;
import com.docket.app.dto.ShareLinkDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.ExternalShareLink;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.Permission;
import com.docket.app.enums.ScanStatus;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.ExternalShareLinkRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceTest {
    private static final String SLUG = "acme";
    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private ExternalShareLinkRepository shareLinkRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private FolderAccessService folderAccessService;
    @Mock
    private StorageService storageService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthRateLimiter authRateLimiter;

    @InjectMocks
    private ShareLinkService shareLinkService;

    private UUID tenantId;
    private Tenant tenant;
    private UUID docId;
    private Document document;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug(SLUG);

        docId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        document = new Document();
        document.setId(docId);
        document.setTenantId(tenantId);
        document.setFolderId(folderId);
        document.setFileStatus(FileStatus.ACTIVE);
        document.setScanStatus(ScanStatus.CLEAN);
        document.setOriginalFileName("contract.pdf");
        document.setFileSize(2048L);

        ReflectionTestUtils.setField(shareLinkService, "baseUrl", "https://docket.test");
    }

    @Test
    void createShareLink_generatesSecureTokenAndPersists() {
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        when(documentRepository.findByIdAndTenantId(docId, tenantId)).thenReturn(Optional.of(document));
        when(folderAccessService.canAccessFolder(authentication, tenantId, document.getFolderId())).thenReturn(true);
        when(shareLinkRepository.save(any(ExternalShareLink.class))).thenAnswer(i -> {
            ExternalShareLink link = i.getArgument(0);
            link.setId(UUID.randomUUID());
            return link;
        });

        ShareLinkDTO result = shareLinkService.createShareLink(SLUG, docId,
                new CreateShareLinkRequestDTO(Instant.now().plusSeconds(3600), null, 5), authentication);

        assertNotNull(result.token());
        assertTrue(result.shareUrl().contains("/share/"));
        verify(shareLinkRepository).save(any(ExternalShareLink.class));
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.DOCUMENT_SHARED), eq("DOCUMENT"), eq(docId), any());
    }

    @Test
    void getPublicInfo_returnsFileInfoWithoutDownloading() {
        String rawToken = "sample-token-123";
        String tokenHash = TokenHasher.hash(rawToken);

        ExternalShareLink link = new ExternalShareLink();
        link.setId(UUID.randomUUID());
        link.setDocumentId(docId);
        link.setTokenHash(tokenHash);
        link.setPasswordHash("hashed-pw");

        when(shareLinkRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(link));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        PublicShareInfoDTO info = shareLinkService.getPublicShareInfo(rawToken);

        assertEquals("contract.pdf", info.documentName());
        assertEquals(2048L, info.fileSize());
        assertTrue(info.requiresPassword());
    }

    @Test
    void revokeShareLink_marksRevoked() {
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        UUID linkId = UUID.randomUUID();
        ExternalShareLink link = new ExternalShareLink();
        link.setId(linkId);
        link.setDocumentId(docId);

        when(documentRepository.findByIdAndTenantId(docId, tenantId)).thenReturn(Optional.of(document));
        when(folderAccessService.canAccessFolder(authentication, tenantId, document.getFolderId())).thenReturn(true);
        when(shareLinkRepository.findById(linkId)).thenReturn(Optional.of(link));

        shareLinkService.revokeShareLink(SLUG, docId, linkId, authentication);

        assertTrue(link.isRevoked());
        verify(shareLinkRepository).save(link);
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.SHARE_LINK_REVOKED), eq("SHARE_LINK"), eq(linkId), any());
    }
}