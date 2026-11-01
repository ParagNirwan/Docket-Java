package com.docket.app.service;

import com.docket.app.dto.ArchivePreviewDTO;
import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Moves a document into an archive tray, which makes it read-only and starts its retention clock.
@Service
@RequiredArgsConstructor
public class ArchiveService {
    private static final int MAX_BULK = 100;

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderAccessService folderAccessService;
    private final RetentionGuard retentionGuard;
    private final RetentionService retentionService;
    private final DocumentService documentService;
    private final AuditService auditService;

    // What archiving would do, for the confirmation. Works on a stand-in, so nothing is saved.
    public ArchivePreviewDTO preview(String slug, UUID docId, UUID folderId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Document document = findActive(docId, tenant.getId(), authentication);
        Folder target = resolveTarget(tenant.getId(), folderId, authentication);

        Document standIn = new Document();
        standIn.setCreatedAt(document.getCreatedAt());
        standIn.setRetainUntil(document.getRetainUntil());
        standIn.setLockMode(document.getLockMode());
        retentionService.applyOnEntry(standIn, target.getId());
        return new ArchivePreviewDTO(target.getId(), target.getName(), standIn.getRetainUntil(), standIn.getLockMode());
    }

    @Transactional
    public DocumentResponseDTO archive(String slug, UUID docId, UUID folderId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Folder target = resolveTarget(tenant.getId(), folderId, authentication);
        return documentService.toDTO(archiveOne(tenant.getId(), docId, target, authentication));
    }

    // All or nothing: one locked document stops the batch, and the error says why.
    @Transactional
    public List<DocumentResponseDTO> archiveAll(String slug, List<UUID> docIds, UUID folderId, Authentication authentication) {
        if (docIds.size() > MAX_BULK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Up to 100 documents can be archived at once");
        }
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Folder target = resolveTarget(tenant.getId(), folderId, authentication);
        return docIds.stream()
                .map(docId -> documentService.toDTO(archiveOne(tenant.getId(), docId, target, authentication)))
                .toList();
    }

    // Only once nothing is holding it. Goes back where it came from, or the Inbox if that's gone.
    @Transactional
    public DocumentResponseDTO unarchive(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Document document = findActive(docId, tenant.getId(), authentication);
        if (document.getArchivedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document isn't archived");
        }
        if (document.isLegalHold() || document.isRetentionLocked(Instant.now())) {
            throw DocumentLockedException.of(document, "This document can't be unarchived while it's locked");
        }

        UUID fromFolderId = document.getFolderId();
        UUID folderId = document.getArchivedFromFolderId() != null
                ? document.getArchivedFromFolderId()
                : folderRepository.findByTenantIdAndIsTrayIsTrueAndIsDefaultIsTrueAndName(tenant.getId(), TrashService.INBOX_TRAY)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Default tray is missing"))
                        .getId();

        document.setOriginalFileName(DocumentNames.available(documentRepository, tenant.getId(), folderId, document.getOriginalFileName()));
        document.setFolderId(folderId);
        document.setArchivedAt(null);
        document.setArchivedFromFolderId(null);
        document.setUpdatedAt(Instant.now());
        retentionService.clearLock(document);
        retentionService.applyOnEntry(document, folderId);
        documentRepository.save(document);

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_UNARCHIVED, "DOCUMENT", docId,
                Map.of("fileName", document.getOriginalFileName(), "fromFolderId", fromFolderId.toString(), "toFolderId", folderId.toString()));
        return documentService.toDTO(document);
    }

    private Document archiveOne(UUID tenantId, UUID docId, Folder target, Authentication authentication) {
        Document document = findActive(docId, tenantId, authentication);
        if (document.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document is already archived");
        }
        retentionGuard.assertMutable(document, RetentionGuard.Action.MOVE, RetentionGuard.Override.NONE, authentication);

        UUID fromFolderId = document.getFolderId();
        document.setOriginalFileName(DocumentNames.available(documentRepository, tenantId, target.getId(), document.getOriginalFileName()));
        document.setArchivedFromFolderId(fromFolderId);
        document.setFolderId(target.getId());
        document.setArchivedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        retentionService.applyOnEntry(document, target.getId());
        documentRepository.save(document);

        auditService.record(authentication, tenantId, AuditAction.DOCUMENT_ARCHIVED, "DOCUMENT", docId,
                Map.of("fileName", document.getOriginalFileName(),
                        "fromFolderId", fromFolderId.toString(),
                        "toFolderId", target.getId().toString(),
                        "retainUntil", String.valueOf(document.getRetainUntil())));
        return document;
    }

    // The organization's archive tray, or a chosen folder inside one.
    private Folder resolveTarget(UUID tenantId, UUID folderId, Authentication authentication) {
        if (folderId == null) {
            return folderRepository.findFirstByTenantIdAndIsArchiveIsTrueAndIsTrayIsTrue(tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Archive tray is missing"));
        }
        if (!folderAccessService.canAccessFolder(authentication, tenantId, folderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        Folder tray = folder.getRootTrayId() == null ? folder : folderRepository.findById(folder.getRootTrayId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (!Boolean.TRUE.equals(tray.getIsArchive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That folder isn't in an archive tray");
        }
        return folder;
    }

    private Document findActive(UUID docId, UUID tenantId, Authentication authentication) {
        return documentRepository.findByIdAndTenantId(docId, tenantId)
                .filter(document -> document.getFileStatus() == FileStatus.ACTIVE)
                .filter(document -> folderAccessService.canAccessFolder(authentication, tenantId, document.getFolderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
