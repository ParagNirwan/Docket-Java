package com.docket.app.service;

import com.docket.app.dto.CreateTagRequestDTO;
import com.docket.app.dto.TagDTO;
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
public class TagService {
    private final TagRepository tagRepository;
    private final TenantRepository tenantRepository;
    private final DocumentRepository documentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderAccessService folderAccessService;
    private final AuditService auditService;

    public List<TagDTO> getTags(String slug, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        return tagRepository.findByTenantIdOrderByNameAsc(tenant.getId())
                .stream()
                .map(t -> new TagDTO(t.getId(), t.getName(), t.getColor()))
                .toList();
    }

    @Transactional
    public TagDTO createTag(String slug, CreateTagRequestDTO request, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);

        String trimmedName = request.name().trim();
        if (tagRepository.existsByTenantIdAndNameIgnoreCase(tenant.getId(), trimmedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A tag with this name already exists");
        }

        Tag tag = new Tag(tenant.getId(), trimmedName, request.color());
        Tag saved = tagRepository.save(tag);

        auditService.record(authentication, tenant.getId(), AuditAction.TAG_CREATED, "TAG", saved.getId(),
                Map.of("name", saved.getName(), "color", String.valueOf(saved.getColor())));

        return new TagDTO(saved.getId(), saved.getName(), saved.getColor());
    }

    @Transactional
    public void deleteTag(String slug, UUID tagId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);

        Tag tag = tagRepository.findById(tagId)
                .filter(t -> t.getTenantId().equals(tenant.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));

        tagRepository.delete(tag);

        auditService.record(authentication, tenant.getId(), AuditAction.TAG_DELETED, "TAG", tagId,
                Map.of("name", tag.getName()));
    }

    @Transactional
    public List<TagDTO> addTagToDocument(String slug, UUID docId, UUID tagId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Document document = findAccessibleDocument(docId, tenant.getId(), authentication);

        Tag tag = tagRepository.findById(tagId)
                .filter(t -> t.getTenantId().equals(tenant.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));

        document.getTags().add(tag);
        documentRepository.save(document);

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_TAGGED, "DOCUMENT", docId,
                Map.of("tagId", tagId.toString(), "tagName", tag.getName()));

        return document.getTags().stream().map(t -> new TagDTO(t.getId(), t.getName(), t.getColor())).toList();
    }

    @Transactional
    public List<TagDTO> removeTagFromDocument(String slug, UUID docId, UUID tagId, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Document document = findAccessibleDocument(docId, tenant.getId(), authentication);

        document.getTags().removeIf(t -> t.getId().equals(tagId));
        documentRepository.save(document);

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_UNTAGGED, "DOCUMENT", docId,
                Map.of("tagId", tagId.toString()));

        return document.getTags().stream().map(t -> new TagDTO(t.getId(), t.getName(), t.getColor())).toList();
    }

    @Transactional
    public List<TagDTO> setDocumentTags(String slug, UUID docId, List<UUID> tagIds, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Document document = findAccessibleDocument(docId, tenant.getId(), authentication);

        document.getTags().clear();
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(tagIds).stream()
                    .filter(t -> t.getTenantId().equals(tenant.getId()))
                    .toList();
            document.getTags().addAll(tags);
        }
        documentRepository.save(document);

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_TAGGED, "DOCUMENT", docId,
                Map.of("tagCount", document.getTags().size()));

        return document.getTags().stream().map(t -> new TagDTO(t.getId(), t.getName(), t.getColor())).toList();
    }

    @Transactional
    public Map<String, Object> updateDocumentMetadata(String slug, UUID docId, Map<String, Object> metadata, Authentication authentication) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.DOCUMENTS_UPDATE);
        Document document = findAccessibleDocument(docId, tenant.getId(), authentication);

        document.setMetadata(metadata != null ? metadata : new HashMap<>());
        documentRepository.save(document);

        auditService.record(authentication, tenant.getId(), AuditAction.DOCUMENT_METADATA_UPDATED, "DOCUMENT", docId,
                Map.of("keys", document.getMetadata().keySet().toString()));

        return document.getMetadata();
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private Document findAccessibleDocument(UUID docId, UUID tenantId, Authentication auth) {
        return documentRepository.findByIdAndTenantId(docId, tenantId)
                .filter(doc -> folderAccessService.canAccessFolder(auth, tenantId, doc.getFolderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }
}

