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
}

