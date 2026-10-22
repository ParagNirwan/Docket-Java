package com.docket.app.service;

import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
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

    // These are the file types users are allowed to upload.
    // Anything not on this list will be rejected.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".csv", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".webp", ".zip", ".rar", ".7z");

    // Maximum file size: 50 megabytes (matching the Spring config)
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    // ─── LIST DOCUMENTS ─────────────────────────────────────────────────
    // Returns a paginated list of active (not deleted) documents in a folder.
    // Only shows documents belonging to the user's tenant (organization).
    public Page<DocumentResponseDTO> getDocuments(String slug, UUID folderId, Authentication authentication,
            Pageable pageable) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);

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

        Document saved = documentRepository.save(document);

        eventPublisher.publishEvent(new DocumentUploadedEvent(saved.getId(), saved.getTenantId()));

        return toDTO(saved, uploader.getName());

    }

    // ─── GET SINGLE DOCUMENT METADATA ───────────────────────────────────
    // Returns details about one specific document (without the file itself).
    public DocumentResponseDTO getDocumentById(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);

        Document document = documentRepository.findByIdAndTenantId(docId, tenant.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        String uploaderName = document.getUploadedBy() != null
                ? userRepository.findById(document.getUploadedBy()).map(User::getName).orElse(null)
                : null;

        return toDTO(document, uploaderName);
    }

    // ─── DOWNLOAD A DOCUMENT ────────────────────────────────────────────
    // Streams the actual file data back to the user's browser.
    // Returns the file as a Resource along with the document metadata
    // (the controller needs both to set the right download headers).
    public DocumentDownload downloadDocument(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);

        Document document = documentRepository.findByIdAndTenantId(docId, tenant.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        // Only allow downloading active files (not deleted ones)
        if (document.getFileStatus() != FileStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.GONE, "This document has been deleted");
        }

        InputStream stream = storageService.retrieve(document.getStoredFileName());
        Resource resource = new InputStreamResource(stream);

        return new DocumentDownload(resource, document);
    }

    // ─── SOFT-DELETE A DOCUMENT ─────────────────────────────────────────
    // Marks a document as deleted in the database, but does NOT remove
    // the actual file from disk. This allows recovery if someone
    // accidentally deletes something.
    public void softDeleteDocument(String slug, UUID docId, Authentication authentication) {
        Tenant tenant = findTenantOrThrow(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_DELETE);

        Document document = documentRepository.findByIdAndTenantId(docId, tenant.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        document.setFileStatus(FileStatus.SOFT_DELETED);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
    }

    // ─── HELPER: Find tenant or throw 404 ───────────────────────────────
    private Tenant findTenantOrThrow(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
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
        if (filename == null)
            return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
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
    private DocumentResponseDTO toDTO(Document doc) {
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
        return dto;
    }

    // A simple container to return both the file data and the document
    // metadata together from the download method. The controller uses
    // the metadata to set HTTP headers (filename, content type).
    public record DocumentDownload(Resource resource, Document document) {
    }
}
