package com.docket.app.service;

import com.docket.app.dto.BulkDocumentRequestDTO;
import com.docket.app.dto.BulkMoveRequestDTO;
import com.docket.app.dto.BulkOperationResultDTO;
import com.docket.app.dto.BulkTagRequestDTO;
import com.docket.app.dto.UpdateDocumentRequestDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Tag;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TagRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BulkDocumentService {
    private final DocumentService documentService;
    private final ArchiveService archiveService;
    private final DocumentRepository documentRepository;
    private final TagRepository tagRepository;
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderAccessService folderAccessService;
    private final AuditService auditService;
    private final RetentionService retentionService;
    private final com.docket.app.repository.RetentionPolicyRepository retentionPolicyRepository;
    private final com.docket.app.service.billing.PlanLimitService planLimitService;
    private final StorageService storageService;

    // A zip is built while streaming, so these bound how long one request can run.
    static final long MAX_ZIP_BYTES = 1024L * 1024 * 1024;

    public BulkOperationResultDTO bulkDelete(String slug, BulkDocumentRequestDTO request, Authentication authentication) {
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (UUID docId : request.documentIds()) {
            try {
                documentService.softDeleteDocument(slug, docId, authentication);
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add("Document " + docId + ": " + (e.getMessage() != null ? e.getMessage() : "Failed to delete"));
            }
        }

        return new BulkOperationResultDTO(succeeded, failed, errors);
    }

    public BulkOperationResultDTO bulkMove(String slug, BulkMoveRequestDTO request, Authentication authentication) {
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (UUID docId : request.documentIds()) {
            try {
                documentService.updateDocument(slug, docId, new UpdateDocumentRequestDTO(null, request.targetFolderId()), authentication);
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add("Document " + docId + ": " + (e.getMessage() != null ? e.getMessage() : "Failed to move"));
            }
        }

        return new BulkOperationResultDTO(succeeded, failed, errors);
    }

    public BulkOperationResultDTO bulkArchive(String slug, BulkDocumentRequestDTO request, Authentication authentication) {
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (UUID docId : request.documentIds()) {
            try {
                archiveService.archive(slug, docId, null, authentication);
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add("Document " + docId + ": " + (e.getMessage() != null ? e.getMessage() : "Failed to archive"));
            }
        }

        return new BulkOperationResultDTO(succeeded, failed, errors);
    }

    @Transactional
    public BulkOperationResultDTO bulkTag(String slug, BulkTagRequestDTO request, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);

        List<Tag> toAdd = request.tagIdsToAdd() == null || request.tagIdsToAdd().isEmpty()
                ? List.of()
                : tagRepository.findAllById(request.tagIdsToAdd()).stream().filter(t -> t.getTenantId().equals(tenant.getId())).toList();

        Set<UUID> toRemoveIds = request.tagIdsToRemove() == null ? Set.of() : new HashSet<>(request.tagIdsToRemove());

        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (UUID docId : request.documentIds()) {
            try {
                Document doc = documentRepository.findByIdAndTenantId(docId, tenant.getId())
                        .filter(d -> folderAccessService.canAccessFolder(authentication, tenant.getId(), d.getFolderId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

                if (!toRemoveIds.isEmpty()) {
                    doc.getTags().removeIf(t -> toRemoveIds.contains(t.getId()));
                }
                if (!toAdd.isEmpty()) {
                    doc.getTags().addAll(toAdd);
                }
                documentRepository.save(doc);
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add("Document " + docId + ": " + (e.getMessage() != null ? e.getMessage() : "Failed to update tags"));
            }
        }

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_TAGGED, "BULK", tenant.getId(),
                Map.of("count", succeeded));

        return new BulkOperationResultDTO(succeeded, failed, errors);
    }

    // Puts the selected documents under one retention policy's lock (extend-only, see
    // RetentionService.applyPolicyToDocument). Needs retention.manage.
    @org.springframework.transaction.annotation.Transactional
    public BulkOperationResultDTO bulkAssignRetention(String slug, BulkDocumentRequestDTO request, UUID policyId,
                                                      Authentication authentication) {
        com.docket.app.entity.Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), com.docket.app.enums.Permission.RETENTION_MANAGE);
        com.docket.app.entity.RetentionPolicy policy = retentionPolicyRepository.findById(policyId)
                .filter(p -> tenant.getId().equals(p.getTenantId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Retention policy not found"));
        planLimitService.requireLockMode(tenant, policy.getLockMode());

        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (UUID docId : request.documentIds()) {
            var document = documentRepository.findByIdAndTenantId(docId, tenant.getId())
                    .filter(d -> folderAccessService.canAccessFolder(authentication, tenant.getId(), d.getFolderId()));
            if (document.isEmpty()) {
                failed++;
                errors.add("Document " + docId + ": not found");
                continue;
            }
            try {
                retentionService.applyPolicyToDocument(document.get(), policy);
                documentRepository.save(document.get());
                auditService.record(authentication, tenant.getId(), com.docket.app.enums.AuditAction.RETENTION_POLICY_APPLIED,
                        "DOCUMENT", docId, java.util.Map.of("policyId", policyId.toString(), "source", "bulk"));
                succeeded++;
            } catch (Exception e) {
                failed++;
                errors.add("Document " + docId + ": " + (e.getMessage() != null ? e.getMessage() : "Failed to apply retention"));
            }
        }
        return new BulkOperationResultDTO(succeeded, failed, errors);
    }

    // Streams the selected documents as one zip. Each file goes through the same access and
    // malware-scan checks as a single download; ones that fail are listed in _skipped.txt
    // instead of failing the whole archive.
    public void writeZip(String slug, BulkDocumentRequestDTO request, Authentication authentication,
                         java.io.OutputStream out) throws java.io.IOException {
        java.util.Set<String> usedNames = new java.util.HashSet<>();
        List<String> skipped = new ArrayList<>();
        long total = 0;
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            for (UUID docId : request.documentIds()) {
                com.docket.app.entity.Document document;
                try {
                    document = documentService.resolveStoredFile(slug, docId, null, authentication);
                } catch (Exception e) {
                    skipped.add(docId + ": " + (e.getMessage() != null ? e.getMessage() : "not available"));
                    continue;
                }
                long size = document.getFileSize() == null ? 0 : document.getFileSize();
                if (total + size > MAX_ZIP_BYTES) {
                    skipped.add(document.getOriginalFileName() + ": the zip would exceed 1 GB");
                    continue;
                }
                zip.putNextEntry(new java.util.zip.ZipEntry(uniqueName(document.getOriginalFileName(), usedNames)));
                try (java.io.InputStream in = storageService.retrieve(document.getStoredFileName())) {
                    total += in.transferTo(zip);
                }
                zip.closeEntry();
                auditService.record(authentication, document.getTenantId(), com.docket.app.enums.AuditAction.DOCUMENT_DOWNLOADED,
                        "DOCUMENT", docId, java.util.Map.of("source", "bulk_zip"));
            }
            if (!skipped.isEmpty()) {
                zip.putNextEntry(new java.util.zip.ZipEntry("_skipped.txt"));
                zip.write(String.join("\n", skipped).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    // Zip entry names come from user file names: strip paths (zip-slip) and de-duplicate.
    static String uniqueName(String original, java.util.Set<String> used) {
        String name = original == null || original.isBlank() ? "document" : original;
        name = name.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.isBlank() || name.equals(".") || name.equals("..")) name = "document";
        String candidate = name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; !used.add(candidate.toLowerCase(java.util.Locale.ROOT)); i++) {
            candidate = base + " (" + i + ")" + ext;
        }
        return candidate;
    }
}
