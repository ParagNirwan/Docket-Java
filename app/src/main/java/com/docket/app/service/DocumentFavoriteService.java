package com.docket.app.service;

import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.dto.FavoriteResultDTO;
import com.docket.app.dto.TagDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.DocumentFavourite;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.repository.AuditEventRepository;
import com.docket.app.repository.DocumentFavouriteRepository;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentFavoriteService {
    private final TenantRepository tenantRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFavouriteRepository documentFavouriteRepository;
    private final UserRepository userRepository;
    private final FolderAccessService folderAccessService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;
    private final AuditEventRepository auditEventRepository;

    public List<DocumentResponseDTO> listFavorites(String slug, Authentication authentication, Pageable pageable) {
        Tenant tenant = requireTenant(slug, authentication);
        User currentUser = currentUser(authentication);

        List<DocumentFavourite> favourites = documentFavouriteRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenant.getId(), currentUser.getId(), pageable);
        List<DocumentResponseDTO> documents = new ArrayList<>();
        for (DocumentFavourite favourite : favourites) {
            Document document = documentRepository.findByIdAndTenantId(favourite.getDocumentId(), tenant.getId())
                    .filter(doc -> folderAccessService.canAccessFolder(authentication, tenant.getId(), doc.getFolderId()))
                    .orElse(null);
            if (document != null) {
                documents.add(toDto(document, userName(document.getUploadedBy())));
            }
        }
        return documents;
    }

    public List<DocumentResponseDTO> recentDocuments(String slug, Authentication authentication, int limit) {
        Tenant tenant = requireTenant(slug, authentication);
        List<com.docket.app.entity.AuditEvent> events = auditEventRepository.findTop20ByTenantIdAndActionInOrderByCreatedAtDesc(
                tenant.getId(), List.of(com.docket.app.enums.AuditAction.DOCUMENT_VIEWED, com.docket.app.enums.AuditAction.DOCUMENT_DOWNLOADED));

        LinkedHashMap<UUID, Document> recent = new LinkedHashMap<>();
        for (var event : events) {
            if (event.getTargetId() == null || event.getTargetType() == null || !"DOCUMENT".equalsIgnoreCase(event.getTargetType())) {
                continue;
            }
            if (recent.size() >= limit) {
                break;
            }
            if (!recent.containsKey(event.getTargetId())) {
                documentRepository.findByIdAndTenantId(event.getTargetId(), tenant.getId())
                        .filter(doc -> folderAccessService.canAccessFolder(authentication, tenant.getId(), doc.getFolderId()))
                        .ifPresent(doc -> recent.put(doc.getId(), doc));
            }
        }

        return recent.values().stream().map(doc -> toDto(doc, userName(doc.getUploadedBy()))).toList();
    }

    @Transactional
    public FavoriteResultDTO toggleFavorite(String slug, UUID documentId, Authentication authentication) {
        Tenant tenant = requireTenant(slug, authentication);
        User currentUser = currentUser(authentication);

        Document document = documentRepository.findByIdAndTenantId(documentId, tenant.getId())
                .filter(doc -> folderAccessService.canAccessFolder(authentication, tenant.getId(), doc.getFolderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        return documentFavouriteRepository.findByTenantIdAndUserIdAndDocumentId(tenant.getId(), currentUser.getId(), document.getId())
                .map(existing -> {
                    documentFavouriteRepository.delete(existing);
                    auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_UNFAVORED, "DOCUMENT", document.getId(), Map.of("favorite", false));
                    return new FavoriteResultDTO(document.getId(), false);
                })
                .orElseGet(() -> {
                    DocumentFavourite favourite = new DocumentFavourite();
                    favourite.setTenantId(tenant.getId());
                    favourite.setUserId(currentUser.getId());
                    favourite.setDocumentId(document.getId());
                    documentFavouriteRepository.save(favourite);
                    auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_FAVORED, "DOCUMENT", document.getId(), Map.of("favorite", true));
                    return new FavoriteResultDTO(document.getId(), true);
                });
    }

    @Transactional
    public FavoriteResultDTO removeFavorite(String slug, UUID documentId, Authentication authentication) {
        Tenant tenant = requireTenant(slug, authentication);
        User currentUser = currentUser(authentication);
        Document document = documentRepository.findByIdAndTenantId(documentId, tenant.getId())
                .filter(doc -> folderAccessService.canAccessFolder(authentication, tenant.getId(), doc.getFolderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        boolean removed = documentFavouriteRepository.findByTenantIdAndUserIdAndDocumentId(tenant.getId(), currentUser.getId(), document.getId())
                .map(existing -> {
                    documentFavouriteRepository.delete(existing);
                    auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_UNFAVORED, "DOCUMENT", document.getId(), Map.of("favorite", false));
                    return true;
                })
                .orElse(false);

        return new FavoriteResultDTO(document.getId(), false);
    }

    private Tenant requireTenant(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        return tenant;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private String userName(UUID userId) {
        return userId == null ? null : userRepository.findById(userId).map(User::getName).orElse(null);
    }

    private DocumentResponseDTO toDto(Document document, String uploadedByName) {
        return new DocumentResponseDTO(
                document.getId(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getUploadedBy(),
                uploadedByName,
                document.getFileSize(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getIndexingStatus(),
                document.getFolderId(),
                document.getRetainUntil(),
                document.getLockMode(),
                document.isLegalHold(),
                document.getArchivedAt(),
                document.getVersion(),
                document.getScanStatus(),
                document.getTags() == null ? List.of() : document.getTags().stream()
                        .map(tag -> new TagDTO(tag.getId(), tag.getName(), tag.getColor())).toList(),
                document.getMetadata() == null ? Map.of() : document.getMetadata()
        );
    }
}
