package com.docket.app.service;

import com.docket.app.dto.DuplicateDocumentGroupDTO;
import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.dto.TagDTO;
import com.docket.app.entity.Document;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentDuplicateService {
    private final TenantRepository tenantRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderAccessService folderAccessService;

    public List<DuplicateDocumentGroupDTO> findDuplicates(String slug, Authentication authentication, Pageable pageable) {
        Tenant tenant = requireTenant(slug, authentication);
        List<Document> documents = documentRepository.findByTenantIdAndFileStatus(tenant.getId(), FileStatus.ACTIVE, pageable).getContent();

        LinkedHashMap<String, List<Document>> groups = new LinkedHashMap<>();
        for (Document document : documents) {
            if (document.getChecksumSha256() == null || document.getChecksumSha256().isBlank()) {
                continue;
            }
            if (folderAccessService.canAccessFolder(authentication, tenant.getId(), document.getFolderId())) {
                groups.computeIfAbsent(document.getChecksumSha256(), key -> new ArrayList<>()).add(document);
            }
        }

        List<DuplicateDocumentGroupDTO> results = new ArrayList<>();
        for (Map.Entry<String, List<Document>> entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<DocumentResponseDTO> docs = entry.getValue().stream()
                        .map(doc -> toDto(doc, userName(doc.getUploadedBy())))
                        .toList();
                results.add(new DuplicateDocumentGroupDTO(entry.getKey(), docs));
            }
        }
        return results;
    }

    private Tenant requireTenant(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        return tenant;
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
