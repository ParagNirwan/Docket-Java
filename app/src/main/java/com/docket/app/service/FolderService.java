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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderRepository folderRepository;

    public List<Folder> getChildren(String slug, UUID parentId, Authentication auth) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(auth, tenant.getId(), Permission.DOCUMENTS_READ);
        return folderRepository.findByTenantIdAndParentFolderId(tenant.getId(), parentId);
    }
}
