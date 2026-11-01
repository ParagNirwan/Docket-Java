package com.docket.app.service;

import com.docket.app.dto.CopyDocumentRequestDTO;
import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.dto.UpdateDocumentRequestDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.ApplicationEventPublisher;
import com.docket.app.service.ai.DocumentUploadedEvent;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final FolderAccessService folderAccessService;
    private final AuditService auditService;
    private final RetentionGuard retentionGuard;
    private final RetentionService retentionService;
    private final TrashService trashService;

    // These are the file types users are allowed to upload.
    // Anything not on this list will be rejected.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".csv", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".webp", ".tif", ".tiff", ".zip", ".rar", ".7z");

    // Maximum file size: 50 megabytes (matching the Spring config)
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    // ─── LIST DOCUMENTS ─────────────────────────────────────────────────
    // Returns a paginated list of active (not deleted) documents in a folder.
    // Only shows documents belonging to the user's tenant (organization).
    public Page<DocumentResponseDTO> getDocuments(String slug, UUID folderId, Authentication authentication,
            Pageable pageable) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        requireFolderAccess(authentication, tenant.getId(), folderId);

        Page<Document> documents = documentRepository.findByTenantIdAndFolderIdAndFileStatus(
                tenant.getId(), folderId, FileStatus.ACTIVE, pageable);

        Set<UUID> userIds = documents.getContent().stream()
                .map(Document::getUploadedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> userNames = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName));

        return documents.map(doc -> toDTO(doc, userNames.get(doc.getUploadedBy())));
    }

    // ─── UPLOAD A DOCUMENT ──────────────────────────────────────────────
    // This is the main upload pipeline. It validates the file, saves it to
    // disk, computes a checksum for integrity, and records it in the database.
    public DocumentResponseDTO uploadDocument(String slug, UUID folderId, MultipartFile file,
            Authentication authentication) {
        // Step 1: Find the tenant and verify the user has upload permission
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_CREATE);
        requireFolderAccess(authentication, tenant.getId(), folderId);
        trashService.requireNotInTrash(folderId);

        // Step 2: Validate the file (not empty, within size limit, allowed type)
        validateFile(file);

        // Step 3: Figure out who is uploading (we need their user ID)
        User uploader = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Step 4: Generate a safe internal filename using a random UUID.
        // We never use the user's original filename for storage because it
        // could contain dangerous characters or path traversal tricks.
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        String storedFilename = buildStoragePath(tenant.getId(), UUID.randomUUID() + extension);

        // Step 5: Save the file to disk and compute its SHA-256 checksum
        // at the same time (we do both in one pass to avoid reading the file twice)
        String checksum;
        try {
            checksum = storeWithChecksum(file.getInputStream(), storedFilename, file.getContentType());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read uploaded file");
        }

        // Step 6: Create a database record for this document
        Document document = new Document();
        document.setTenantId(tenant.getId());
        document.setFolderId(folderId);
        document.setUploadedBy(uploader.getId());
        document.setOriginalFileName(originalFilename);
        document.setStoredFileName(storedFilename);
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setChecksumSha256(checksum);
        document.setFileStatus(FileStatus.ACTIVE);
        document.setVersion(1);
        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        retentionService.applyOnEntry(document, folderId);

        Document saved = documentRepository.save(document);

        eventPublisher.publishEvent(new DocumentUploadedEvent(saved.getId(), saved.getTenantId()));
        auditService.record(tenant.getId(), uploader.getId(), AuditAction.DOCUMENT_UPLOADED, "DOCUMENT", saved.getId(),
                Map.of("fileName", originalFilename));

        return toDTO(saved, uploader.getName());

    }

    // ─── GET SINGLE DOCUMENT METADATA ───────────────────────────────────
    // Returns details about one specific document (without the file itself).
    public DocumentResponseDTO getDocumentById(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);

        Document document = findAccessibleDocumentOrThrow(docId, tenant.getId(), authentication);

        String uploaderName = document.getUploadedBy() != null
                ? userRepository.findById(document.getUploadedBy()).map(User::getName).orElse(null)
                : null;

        return toDTO(document, uploaderName);
    }

    // ─── CHECK A DOCUMENT CAN BE RE-INDEXED ──────────────────────────────
    // Re-indexing costs money (OCR and embeddings), so being able to read
    // a document isn't enough to trigger it.
    public DocumentResponseDTO getDocumentForReindex(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        return getDocumentById(slug, docId, authentication);
    }

    // ─── VIEW A DOCUMENT ────────────────────────────────────────────────
    // The same as getDocumentById, but counted in the audit log. Kept separate
    // because the frontend polls getDocumentById for indexing status, and
    // polling isn't someone looking at a document.
    public DocumentResponseDTO viewDocument(String slug, UUID docId, Authentication authentication) {
        DocumentResponseDTO document = getDocumentById(slug, docId, authentication);
        auditService.record(authentication, findTenantOrThrow(slug).getId(), AuditAction.DOCUMENT_VIEWED, "DOCUMENT", docId,
                Map.of("fileName", String.valueOf(document.getOriginalFileName())));
        return document;
    }

    // ─── DOWNLOAD A DOCUMENT ────────────────────────────────────────────
    // Streams the actual file data back to the user's browser.
    // Returns the file as a Resource along with the document metadata
    // (the controller needs both to set the right download headers).
    public DocumentDownload downloadDocument(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);

        Document document = findAccessibleDocumentOrThrow(docId, tenant.getId(), authentication);

        // Only allow downloading active files (not deleted ones)
        if (document.getFileStatus() != FileStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.GONE, "This document has been deleted");
        }

        InputStream stream = storageService.retrieve(document.getStoredFileName());
        Resource resource = new InputStreamResource(stream);

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_DOWNLOADED, "DOCUMENT", docId,
                Map.of("fileName", String.valueOf(document.getOriginalFileName())));

        return new DocumentDownload(resource, document);
    }

    // ─── SOFT-DELETE A DOCUMENT ─────────────────────────────────────────
    // Moves a document to the Trash, but does NOT remove the actual file
    // from disk. This allows recovery if someone accidentally deletes something.
    public void softDeleteDocument(String slug, UUID docId, Authentication authentication) {
        softDeleteDocument(slug, docId, RetentionGuard.Override.NONE, authentication);
    }

    @Transactional
    public void softDeleteDocument(String slug, UUID docId, RetentionGuard.Override override, Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_DELETE);

        Document document = findActiveDocumentOrThrow(docId, tenant.getId(), authentication);
        if (retentionGuard.assertMutable(document, RetentionGuard.Action.DELETE, override, authentication)) {
            retentionService.clearLock(document);
        }

        User actor = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        trashService.moveToTrash(document, actor.getId());
        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_DELETED, "DOCUMENT", docId,
                Map.of("fileName", String.valueOf(document.getOriginalFileName())));
    }

    // ─── RENAME OR MOVE A DOCUMENT ──────────────────────────────────────
    // Who can see a document follows from its folder's tray, so a move changes that
    // by itself. A name that's taken in the destination is a 409, not a silent change.
    public DocumentResponseDTO updateDocument(String slug, UUID docId, UpdateDocumentRequestDTO request,
            Authentication authentication) {
        return updateDocument(slug, docId, request, RetentionGuard.Override.NONE, authentication);
    }

    @Transactional
    public DocumentResponseDTO updateDocument(String slug, UUID docId, UpdateDocumentRequestDTO request,
            RetentionGuard.Override override, Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);

        Document document = findActiveDocumentOrThrow(docId, tenant.getId(), authentication);
        String previousName = document.getOriginalFileName();
        UUID previousFolderId = document.getFolderId();

        String name = request.getName() == null ? previousName : cleanNewName(previousName, request.getName());
        UUID folderId = request.getFolderId() == null ? previousFolderId : request.getFolderId();
        boolean renamed = !name.equals(previousName);
        boolean moved = !folderId.equals(previousFolderId);
        if (!renamed && !moved) {
            return toDTO(document);
        }

        if (renamed) {
            retentionGuard.assertMutable(document, RetentionGuard.Action.RENAME, override, authentication);
        }
        if (moved) {
            requireFolderAccess(authentication, tenant.getId(), folderId);
            trashService.requireNotInTrash(folderId);
            if (retentionGuard.assertMutable(document, RetentionGuard.Action.MOVE, override, authentication)) {
                retentionService.clearLock(document);
            }
        }
        if (documentRepository.existsByTenantIdAndFolderIdAndFileStatusAndOriginalFileNameIgnoreCaseAndIdNot(
                tenant.getId(), folderId, FileStatus.ACTIVE, name, docId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A document with this name already exists in the folder");
        }

        Instant now = Instant.now();
        documentRepository.updateNameAndFolder(docId, name, folderId, now);
        document.setOriginalFileName(name);
        document.setFolderId(folderId);
        document.setUpdatedAt(now);
        if (moved) {
            retentionService.applyOnEntry(document, folderId);
        }

        if (renamed) {
            auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_RENAMED, "DOCUMENT", docId,
                    Map.of("from", previousName, "to", name));
        }
        if (moved) {
            auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_MOVED, "DOCUMENT", docId,
                    Map.of("fileName", name, "fromFolderId", previousFolderId.toString(), "toFolderId", folderId.toString()));
        }
        return toDTO(document);
    }

    // ─── COPY A DOCUMENT ────────────────────────────────────────────────
    // A copy is a new document with its own stored file, so deleting one can never
    // affect the other. It's indexed like an upload. A name that's taken gets a
    // suffix, like "report (1).pdf".
    public DocumentResponseDTO copyDocument(String slug, UUID docId, CopyDocumentRequestDTO request,
            Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_CREATE);

        Document source = findActiveDocumentOrThrow(docId, tenant.getId(), authentication);
        UUID folderId = request.getFolderId() == null ? source.getFolderId() : request.getFolderId();
        requireFolderAccess(authentication, tenant.getId(), folderId);
        trashService.requireNotInTrash(folderId);

        User copier = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String name = DocumentNames.available(documentRepository, tenant.getId(), folderId, source.getOriginalFileName());
        String storedFilename = buildStoragePath(tenant.getId(), UUID.randomUUID() + getExtension(name));
        String checksum;
        try (InputStream data = storageService.retrieve(source.getStoredFileName())) {
            checksum = storeWithChecksum(data, storedFilename, source.getContentType());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to copy the file");
        }

        Instant now = Instant.now();
        Document copy = new Document();
        copy.setTenantId(tenant.getId());
        copy.setFolderId(folderId);
        copy.setUploadedBy(copier.getId());
        copy.setOriginalFileName(name);
        copy.setStoredFileName(storedFilename);
        copy.setContentType(source.getContentType());
        copy.setFileSize(source.getFileSize());
        copy.setChecksumSha256(checksum);
        copy.setFileStatus(FileStatus.ACTIVE);
        copy.setVersion(1);
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        retentionService.applyOnEntry(copy, folderId);
        Document saved = documentRepository.save(copy);

        eventPublisher.publishEvent(new DocumentUploadedEvent(saved.getId(), saved.getTenantId()));
        auditService.record(tenant.getId(), copier.getId(), AuditAction.DOCUMENT_COPIED, "DOCUMENT", saved.getId(),
                Map.of("fileName", name, "sourceDocumentId", docId.toString()));
        return toDTO(saved, copier.getName());
    }

    // ─── HELPER: Find an active document the user can see or throw 404 ──
    private Document findActiveDocumentOrThrow(UUID docId, UUID tenantId, Authentication authentication) {
        Document document = findAccessibleDocumentOrThrow(docId, tenantId, authentication);
        if (document.getFileStatus() != FileStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return document;
    }

    // ─── HELPER: Check a new name for a document ────────────────────────
    // Uploads are checked against a list of file types, and changing the extension
    // would be a way around that.
    private String cleanNewName(String currentName, String requestedName) {
        if (requestedName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        String name = sanitizeFilename(requestedName.trim());
        if (!getExtension(name).equalsIgnoreCase(getExtension(currentName))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The file extension can't be changed");
        }
        return name;
    }

    // ─── HELPER: Find tenant or throw 404 ───────────────────────────────
    private Tenant findTenantOrThrow(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    // ─── HELPER: Check the user can see inside a folder or throw 404 ────
    private void requireFolderAccess(Authentication authentication, UUID tenantId, UUID folderId) {
        if (!folderAccessService.canAccessFolder(authentication, tenantId, folderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
    }

    // ─── HELPER: Find a document the user can see or throw 404 ──────────
    // A document in a tray the user can't see looks the same as one that
    // doesn't exist, so they can't confirm it's there by guessing its ID.
    private Document findAccessibleDocumentOrThrow(UUID docId, UUID tenantId, Authentication authentication) {
        return documentRepository.findByIdAndTenantId(docId, tenantId)
                .filter(doc -> folderAccessService.canAccessFolder(authentication, tenantId, doc.getFolderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    // ─── HELPER: Validate the uploaded file ─────────────────────────────
    // Checks three things before we accept a file:
    // 1. The file is not empty (0 bytes)
    // 2. The file is within our size limit (50 MB)
    // 3. The file type is on our allowed list
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload an empty file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File exceeds the maximum allowed size (50 MB)");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "File type '" + extension + "' is not allowed");
        }
    }

    // ─── HELPER: Clean up the filename ──────────────────────────────────
    // Removes dangerous characters and path traversal attempts from
    // the user's original filename. We only use this for display —
    // the actual stored file gets a UUID name.
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }
        // Remove any path components (e.g., "C:\Users\file.pdf" -> "file.pdf")
        filename = filename.replace("\\", "/");
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }
        // Remove null bytes and control characters
        filename = filename.replaceAll("[\\x00-\\x1f]", "");
        return filename.isBlank() ? "unnamed" : filename;
    }

    // ─── HELPER: Get file extension ─────────────────────────────────────
    // Extracts ".pdf" from "report.pdf", or "" if there's no extension.
    private String getExtension(String filename) {
        return DocumentNames.extensionOf(filename);
    }

    // ─── HELPER: Build storage path ─────────────────────────────────────
    // Organizes files on disk by tenant and date:
    // uploads/{tenant-id}/2026/10/{uuid-filename}.pdf
    // This prevents any single folder from having millions of files.
    private String buildStoragePath(UUID tenantId, String filename) {
        LocalDate now = LocalDate.now();
        return tenantId + "/" + now.getYear() + "/" + now.getMonthValue() + "/" + filename;
    }

    // ─── HELPER: Store file and compute SHA-256 checksum at the same time ─
    // A checksum is like a "fingerprint" for the file — if even one byte
    // changes, the checksum will be completely different. This lets us
    // verify later that the file wasn't corrupted during upload.
    private String storeWithChecksum(InputStream inputStream, String storedFilename, String contentType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // DigestInputStream computes the checksum as data flows through
            // it, so we don't need to read the file a second time.
            try (DigestInputStream digestStream = new DigestInputStream(inputStream, digest)) {
                storageService.store(digestStream, storedFilename, contentType);
            }
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to compute file checksum", e);
        }
    }

    // ─── HELPER: Convert a Document entity to a response DTO ──
    // This controls exactly which fields are sent to the frontend.
    // Internal fields like storedFileName and checksumSha256 are excluded
    // because the frontend doesn't need them and exposing them would be
    // a security concern.
    public DocumentResponseDTO toDTO(Document doc) {
        String uploaderName = doc.getUploadedBy() != null
                ? userRepository.findById(doc.getUploadedBy()).map(User::getName).orElse(null)
                : null;
        return toDTO(doc, uploaderName);
    }

    private DocumentResponseDTO toDTO(Document doc, String uploaderName) {
        DocumentResponseDTO dto = new DocumentResponseDTO();
        dto.setId(doc.getId());
        dto.setOriginalFileName(doc.getOriginalFileName());
        dto.setContentType(doc.getContentType());
        dto.setUploadedBy(doc.getUploadedBy());
        dto.setUploadedByName(uploaderName);
        dto.setFileSize(doc.getFileSize());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setUpdatedAt(doc.getUpdatedAt());
        dto.setIndexingStatus(doc.getIndexingStatus());
        dto.setFolderId(doc.getFolderId());
        dto.setRetainUntil(doc.getRetainUntil());
        dto.setLockMode(doc.getLockMode());
        dto.setLegalHold(doc.isLegalHold());
        dto.setArchivedAt(doc.getArchivedAt());
        return dto;
    }

    // A simple container to return both the file data and the document
    // metadata together from the download method. The controller uses
    // the metadata to set HTTP headers (filename, content type).
    public record DocumentDownload(Resource resource, Document document) {
    }
}
