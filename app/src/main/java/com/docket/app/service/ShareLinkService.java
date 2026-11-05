package com.docket.app.service;

import com.docket.app.dto.CreateShareLinkRequestDTO;
import com.docket.app.dto.PublicShareInfoDTO;
import com.docket.app.dto.ShareLinkDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.ExternalShareLink;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.enums.ScanStatus;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.ExternalShareLinkRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShareLinkService {
    private final ExternalShareLinkRepository shareLinkRepository;
    private final DocumentRepository documentRepository;
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderAccessService folderAccessService;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimiter authRateLimiter;

    @Value("${docket.app.base-url}")
    private String baseUrl;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public ShareLinkDTO createShareLink(String slug, UUID docId, CreateShareLinkRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        Document document = findAccessibleDocument(docId, tenant.getId(), authentication);

        if (document.getFileStatus() != FileStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.GONE, "Document has been deleted");
        }
        if (document.getScanStatus() != null && document.getScanStatus() != ScanStatus.CLEAN) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "This document is awaiting a security scan");
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = HexFormat.of().formatHex(bytes);
        String tokenHash = TokenHasher.hash(rawToken);

        ExternalShareLink link = new ExternalShareLink();
        link.setDocumentId(docId);
        link.setTokenHash(tokenHash);
        link.setExpiresAt(request.expiresAt());
        link.setMaxDownloads(request.maxDownloads() != null && request.maxDownloads() > 0 ? request.maxDownloads() : null);

        if (request.password() != null && !request.password().isBlank()) {
            link.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        }

        ExternalShareLink saved = shareLinkRepository.save(link);

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_SHARED, "DOCUMENT", docId,
                Map.of("shareId", saved.getId().toString(), "hasPassword", String.valueOf(saved.getPasswordHash() != null)));

        String shareUrl = baseUrl + "/share/" + rawToken;
        return toDTO(saved, rawToken, shareUrl);
    }

    public List<ShareLinkDTO> listShareLinks(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        findAccessibleDocument(docId, tenant.getId(), authentication);

        return shareLinkRepository.findByDocumentIdOrderByCreatedAtDesc(docId).stream()
                .map(l -> toDTO(l, null, null))
                .toList();
    }

    @Transactional
    public void revokeShareLink(String slug, UUID docId, UUID shareId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        findAccessibleDocument(docId, tenant.getId(), authentication);

        ExternalShareLink link = shareLinkRepository.findById(shareId)
                .filter(l -> l.getDocumentId().equals(docId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found"));

        link.setRevokedAt(Instant.now());
        shareLinkRepository.save(link);

        auditService.record(authentication, tenant.getId(), AuditAction.SHARE_LINK_REVOKED, "SHARE_LINK", shareId,
                Map.of("documentId", docId.toString()));
    }

    // ─── PUBLIC ACCESS METHODS ──────────────────────────────────────────

    public PublicShareInfoDTO getPublicShareInfo(String rawToken) {
        ExternalShareLink link = findActiveLinkOrThrow(rawToken);
        Document document = findDocumentForShareOrThrow(link.getDocumentId());

        Instant now = Instant.now();
        boolean expired = link.isExpired(now);
        boolean limitReached = link.isDownloadLimitReached();

        return new PublicShareInfoDTO(
                document.getOriginalFileName(),
                document.getFileSize(),
                document.getContentType(),
                link.getPasswordHash() != null,
                expired,
                limitReached,
                link.getExpiresAt()
        );
    }

    @Transactional
    public DocumentService.DocumentDownload downloadSharedDocument(String rawToken, String password, String clientIp) {
        String tokenHash = TokenHasher.hash(rawToken);
        authRateLimiter.check("share:" + tokenHash + ":" + (clientIp != null ? clientIp : "unknown"), 20, Duration.ofMinutes(1));

        ExternalShareLink link = findActiveLinkOrThrow(rawToken);
        Instant now = Instant.now();

        if (link.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This share link has expired");
        }
        if (link.isDownloadLimitReached()) {
            throw new ResponseStatusException(HttpStatus.GONE, "The download limit for this link has been reached");
        }

        if (link.getPasswordHash() != null) {
            if (password == null || password.isBlank() || !passwordEncoder.matches(password.trim(), link.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password for this share link");
            }
        }

        Document document = findDocumentForShareOrThrow(link.getDocumentId());
        shareLinkRepository.incrementDownloadCount(link.getId());

        // Notify document uploader
        if (document.getUploadedBy() != null) {
            notificationService.notify(document.getTenantId(), document.getUploadedBy(),
                    NotificationType.SHARED_DOCUMENT_ACCESSED,
                    "Shared document downloaded",
                    "'" + document.getOriginalFileName() + "' was downloaded via external share link.",
                    NotificationService.workspaceLink(findSlug(document.getTenantId()), "review", null));
        }

        Resource resource = new InputStreamResource(storageService.retrieve(document.getStoredFileName()));
        return new DocumentService.DocumentDownload(resource, document);
    }

    private ExternalShareLink findActiveLinkOrThrow(String rawToken) {
        String tokenHash = TokenHasher.hash(rawToken);
        ExternalShareLink link = shareLinkRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found"));
        if (link.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.GONE, "This share link has been revoked");
        }
        return link;
    }

    private Document findDocumentForShareOrThrow(UUID docId) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (document.getFileStatus() != FileStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.GONE, "Document is no longer available");
        }
        if (document.getScanStatus() != null && document.getScanStatus() != ScanStatus.CLEAN) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Document is awaiting security scan");
        }
        return document;
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private String findSlug(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::getSlug).orElse("unknown");
    }

    private Document findAccessibleDocument(UUID docId, UUID tenantId, Authentication auth) {
        return documentRepository.findByIdAndTenantId(docId, tenantId)
                .filter(doc -> folderAccessService.canAccessFolder(auth, tenantId, doc.getFolderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private ShareLinkDTO toDTO(ExternalShareLink link, String rawToken, String shareUrl) {
        return new ShareLinkDTO(
                link.getId(),
                link.getDocumentId(),
                rawToken,
                shareUrl,
                link.getExpiresAt(),
                link.getMaxDownloads(),
                link.getDownloadCount(),
                link.getPasswordHash() != null,
                link.isRevoked(),
                link.getCreatedAt()
        );
    }
}

