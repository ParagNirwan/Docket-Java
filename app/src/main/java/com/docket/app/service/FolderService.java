package com.docket.app.service;

import com.docket.app.dto.BreadcrumbDTO;
import com.docket.app.dto.UpdateFolderRequestDTO;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderService {
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderRepository folderRepository;
    private final FolderAccessService folderAccessService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

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

    // The path from the tray down to this folder.
    public List<BreadcrumbDTO> getBreadcrumb(String slug, UUID folderId, Authentication auth) {
        Tenant tenant = findTenant(slug);
        tenantAuthorizationService.requirePermission(auth, tenant.getId(), Permission.DOCUMENTS_READ);
        Folder folder = findAccessibleFolder(tenant.getId(), folderId, auth);

        List<BreadcrumbDTO> path = new ArrayList<>();
        while (folder != null) {
            path.addFirst(new BreadcrumbDTO(folder.getId(), folder.getName(), folder.getIsTray()));
            folder = folder.getParentFolderId() == null ? null
                    : folderRepository.findById(folder.getParentFolderId()).orElse(null);
        }
        return path;
    }

    // Rename and/or move. Trays can be renamed by people who can update trays, but they
    // sit at the top of the tree, so they can't move. The default trays can't change at all.
    @Transactional
    public Folder updateFolder(String slug, UUID folderId, UpdateFolderRequestDTO request, Authentication auth) {
        Tenant tenant = findTenant(slug);
        Folder folder = findAccessibleFolder(tenant.getId(), folderId, auth);

        tenantAuthorizationService.requirePermission(auth, tenant.getId(),
                folder.getIsTray() ? Permission.TRAYS_UPDATE : Permission.FOLDERS_UPDATE);
        if (folder.getIsDefault()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Default trays cannot be modified");
        }
        if (folder.getIsTray() && request.getParentFolderId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trays can't be moved");
        }

        String previousName = folder.getName();
        UUID previousParentId = folder.getParentFolderId();
        String name = previousName;
        if (request.getName() != null) {
            name = request.getName().trim();
            if (name.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder name is required");
            }
        }
        UUID parentId = request.getParentFolderId() == null ? previousParentId : request.getParentFolderId();
        boolean renamed = !name.equals(previousName);
        boolean moved = !Objects.equals(parentId, previousParentId);
        if (!renamed && !moved) {
            return folder;
        }

        // Checked before the name, or a 409 would confirm what's inside a hidden folder.
        if (moved) {
            findAccessibleFolder(tenant.getId(), parentId, auth);
        }
        if (folderRepository.existsByTenantIdAndParentFolderIdAndNameIgnoreCaseAndIdNot(
                tenant.getId(), parentId, name, folderId)) {
            throw nameTaken();
        }

        if (moved) {
            moveUnder(folder, parentId, auth);
        }
        folder.setName(name);
        folder.setUpdatedAt(Instant.now());
        // The database has the final say on names, for two requests racing each other.
        Folder saved;
        try {
            saved = folderRepository.saveAndFlush(folder);
        } catch (DataIntegrityViolationException e) {
            throw nameTaken();
        }
        if (moved) {
            folderRepository.updateDescendantsTray(saved.getId(), saved.getRootTrayId(), saved.getVisibility().name());
        }

        if (renamed) {
            auditService.record(auth, tenant.getId(),
                    saved.getIsTray() ? AuditAction.TRAY_RENAMED : AuditAction.FOLDER_RENAMED,
                    saved.getIsTray() ? "TRAY" : "FOLDER", saved.getId(),
                    Map.of("from", previousName, "to", name));
        }
        if (moved) {
            auditService.record(auth, tenant.getId(), AuditAction.FOLDER_MOVED, "FOLDER", saved.getId(),
                    Map.of("name", name, "fromFolderId", previousParentId.toString(), "toFolderId", parentId.toString()));
        }
        return saved;
    }

    // Two moves running side by side (A into B, B into A) could each pass the cycle check
    // and cut both folders off from their tray for good, so moves in an organization take
    // turns. The lock is released when the transaction ends.
    private void moveUnder(Folder folder, UUID parentId, Authentication auth) {
        UUID tenantId = folder.getTenantId();
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))::text", String.class, "folder-move:" + tenantId);

        Folder parent = findAccessibleFolder(tenantId, parentId, auth);
        if (folderRepository.isSameOrDescendant(folder.getId(), parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A folder can't be moved into itself");
        }

        UUID trayId = parent.getRootTrayId() != null ? parent.getRootTrayId() : parent.getId();
        Folder tray = trayId.equals(parent.getId()) ? parent : folderRepository.findById(trayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));

        folder.setParentFolderId(parentId);
        folder.setRootTrayId(trayId);
        folder.setVisibility(tray.getVisibility());
    }

    private Folder findAccessibleFolder(UUID tenantId, UUID folderId, Authentication auth) {
        if (!folderAccessService.canAccessFolder(auth, tenantId, folderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
    }

    private Tenant findTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private ResponseStatusException nameTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A folder with this name already exists here");
    }
}
