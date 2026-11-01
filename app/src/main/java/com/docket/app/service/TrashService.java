package com.docket.app.service;

import com.docket.app.dto.TrashDocumentDTO;
import com.docket.app.dto.TrashSettingsDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// A deleted document moves into the organization's Trash tray, which is admin-only, and
// stays there for the organization's trash period before the purge job removes it.
@Service
@RequiredArgsConstructor
public class TrashService {
    static final String TRASH_TRAY = "Trash";
    static final String INBOX_TRAY = "Inbox";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int EMPTY_TRASH_BATCH = 1000;

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderAccessService folderAccessService;
    private final RetentionGuard retentionGuard;
    private final RetentionService retentionService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public Page<TrashDocumentDTO> list(String slug, int page, int size, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_DELETE);

        Page<Document> documents = documentRepository.findByTenantIdAndFileStatus(tenant.getId(), FileStatus.SOFT_DELETED,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "deletedAt")));

        Set<UUID> userIds = documents.stream().map(Document::getDeletedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        // Either key can be null below, which Map.of() refuses to look up.
        Map<UUID, String> userNames = userIds.isEmpty() ? Collections.emptyMap()
                : userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getName));
        Set<UUID> folderIds = documents.stream().map(Document::getOriginalFolderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> folderNames = folderIds.isEmpty() ? Collections.emptyMap()
                : folderRepository.findAllById(folderIds).stream().collect(Collectors.toMap(Folder::getId, Folder::getName));

        return documents.map(document -> new TrashDocumentDTO(
                document.getId(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getDeletedAt(),
                userNames.get(document.getDeletedBy()),
                folderNames.get(document.getOriginalFolderId()),
                document.getDeletedAt() == null ? null : document.getDeletedAt().plus(Duration.ofDays(tenant.getTrashRetentionDays())),
                document.getRetainUntil(),
                document.getLockMode(),
                document.isLegalHold()));
    }

    // Callers have already checked the document can be deleted.
    public void moveToTrash(Document document, UUID actorId) {
        Folder trash = requireTray(document.getTenantId(), TRASH_TRAY);
        document.setOriginalFolderId(document.getFolderId());
        document.setFolderId(trash.getId());
        document.setFileStatus(FileStatus.SOFT_DELETED);
        document.setDeletedAt(Instant.now());
        document.setDeletedBy(actorId);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
    }

    // Every active document under a folder that's about to be deleted. The folder won't be
    // there to go back to, so restoring these sends them to the Inbox.
    public int moveSubtreeToTrash(UUID tenantId, UUID folderId, UUID actorId) {
        Folder trash = requireTray(tenantId, TRASH_TRAY);
        return documentRepository.trashSubtree(folderId, trash.getId(), actorId, Instant.now());
    }

    // A document back in its folder is under that folder's policies again. If the name's
    // been taken in the meantime it gets a suffix rather than failing the restore.
    @Transactional
    public void restore(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_DELETE);
        Document document = findInTrash(docId, tenant.getId(), authentication);

        UUID folderId = document.getOriginalFolderId() != null
                ? document.getOriginalFolderId()
                : requireTray(tenant.getId(), INBOX_TRAY).getId();

        document.setOriginalFileName(DocumentNames.available(documentRepository, tenant.getId(), folderId, document.getOriginalFileName()));
        document.setFolderId(folderId);
        document.setFileStatus(FileStatus.ACTIVE);
        document.setOriginalFolderId(null);
        document.setDeletedAt(null);
        document.setDeletedBy(null);
        document.setUpdatedAt(Instant.now());
        retentionService.applyOnEntry(document, folderId);
        documentRepository.save(document);

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_RESTORED, "DOCUMENT", docId,
                Map.of("fileName", document.getOriginalFileName(), "toFolderId", folderId.toString()));
    }

    @Transactional
    public void purge(String slug, UUID docId, RetentionGuard.Override override, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_DELETE);
        Document document = findInTrash(docId, tenant.getId(), authentication);

        if (retentionGuard.assertMutable(document, RetentionGuard.Action.PURGE, override, authentication)) {
            retentionService.clearLock(document);
            documentRepository.saveAndFlush(document);
        }
        deleteRowAndFile(document);
        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_PURGED, "DOCUMENT", docId,
                Map.of("fileName", String.valueOf(document.getOriginalFileName())));
    }

    // Locked documents are left in place and counted, so the person knows why some remain.
    @Transactional
    public EmptyTrashResult emptyTrash(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_DELETE);

        Instant now = Instant.now();
        List<Document> documents = documentRepository
                .findByTenantIdAndFileStatus(tenant.getId(), FileStatus.SOFT_DELETED, PageRequest.of(0, EMPTY_TRASH_BATCH))
                .getContent();
        int purged = 0;
        for (Document document : documents) {
            if (document.isLegalHold() || document.isRetentionLocked(now)) {
                continue;
            }
            deleteRowAndFile(document);
            auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_PURGED, "DOCUMENT", document.getId(),
                    Map.of("fileName", String.valueOf(document.getOriginalFileName())));
            purged++;
        }
        return new EmptyTrashResult(purged, documents.size() - purged);
    }

    // For the purge job. Checked again here: a hold may have arrived since the document was picked.
    @Transactional
    public boolean purgeExpired(UUID docId) {
        Document document = documentRepository.findById(docId).orElse(null);
        if (document == null || document.getFileStatus() != FileStatus.SOFT_DELETED
                || document.isLegalHold() || document.isRetentionLocked(Instant.now())) {
            return false;
        }
        deleteRowAndFile(document);
        auditService.recordSystem(document.getTenantId(), AuditAction.DOCUMENT_PURGED, "DOCUMENT", docId,
                Map.of("fileName", String.valueOf(document.getOriginalFileName()), "reason", "Trash period ended"));
        return true;
    }

    public TrashSettingsDTO getSettings(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_READ);
        return new TrashSettingsDTO(tenant.getTrashRetentionDays());
    }

    @Transactional
    public TrashSettingsDTO updateSettings(String slug, TrashSettingsDTO settings, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.RETENTION_MANAGE);
        int previous = tenant.getTrashRetentionDays();
        tenant.setTrashRetentionDays(settings.trashRetentionDays());
        tenantRepository.save(tenant);
        auditService.record(authentication, tenant.getId(), AuditAction.TRASH_SETTINGS_UPDATED, "TENANT", tenant.getId(),
                Map.of("from", previous, "to", settings.trashRetentionDays()));
        return settings;
    }

    // Nothing is filed in the Trash by hand: it's only reached by deleting.
    public void requireNotInTrash(UUID folderId) {
        folderRepository.findById(folderId)
                .map(folder -> folder.getRootTrayId() == null ? folder
                        : folderRepository.findById(folder.getRootTrayId()).orElse(folder))
                .filter(tray -> Boolean.TRUE.equals(tray.getIsDefault()) && TRASH_TRAY.equals(tray.getName()))
                .ifPresent(tray -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing can be filed in the Trash");
                });
    }

    private void deleteRowAndFile(Document document) {
        documentRepository.delete(document);
        documentRepository.flush();
        eventPublisher.publishEvent(new StoredFileDeletedEvent(document.getStoredFileName()));
    }

    private Document findInTrash(UUID docId, UUID tenantId, Authentication authentication) {
        return documentRepository.findByIdAndTenantId(docId, tenantId)
                .filter(document -> document.getFileStatus() == FileStatus.SOFT_DELETED)
                .filter(document -> folderAccessService.canAccessFolder(authentication, tenantId, document.getFolderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private Folder requireTray(UUID tenantId, String name) {
        return folderRepository.findByTenantIdAndIsTrayIsTrueAndIsDefaultIsTrueAndName(tenantId, name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Default tray is missing: " + name));
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    public record EmptyTrashResult(int purged, int skipped) {
    }
}
