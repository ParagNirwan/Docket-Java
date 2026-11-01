package com.docket.app.service;

import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.Permission;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderRepository folderRepository;
    private final FolderAccessService folderAccessService;

    private final com.docket.app.repository.UserRepository userRepository;

    public List<Folder> getChildren(String slug, UUID parentId, Authentication auth) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(auth, tenant.getId(), Permission.DOCUMENTS_READ);
        if (!folderAccessService.canAccessFolder(auth, tenant.getId(), parentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
        return folderRepository.findByTenantIdAndParentFolderId(tenant.getId(), parentId);
    }

    public Folder createSubFolder(String slug, UUID parentFolderId, com.docket.app.dto.CreateFolderRequestDTO request, Authentication auth) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(auth, tenant.getId(), Permission.FOLDERS_CREATE);

        if (!folderAccessService.canAccessFolder(auth, tenant.getId(), parentFolderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent folder/tray not found");
        }

        Folder parent = folderRepository.findById(parentFolderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent folder/tray not found"));

        UUID userId = userRepository.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();

        Folder subFolder = new Folder();
        subFolder.setTenantId(tenant.getId());
        subFolder.setParentFolderId(parent.getId());
        // A tray is the root of its own tree, so its children point straight at it.
        subFolder.setRootTrayId(parent.getRootTrayId() != null ? parent.getRootTrayId() : parent.getId());
        subFolder.setName(request.getName().trim());
        subFolder.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        subFolder.setIsTray(false);
        subFolder.setIsDefault(false);
        // Inherit parent visibility
        subFolder.setVisibility(parent.getVisibility());
        subFolder.setSortOrder(0);
        subFolder.setCreatedBy(userId);
        subFolder.setCreatedAt(Instant.now());
        subFolder.setUpdatedAt(Instant.now());

        return folderRepository.save(subFolder);
    }
}
