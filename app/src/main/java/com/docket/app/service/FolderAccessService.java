package com.docket.app.service;

import com.docket.app.entity.Folder;
import com.docket.app.enums.Roles;
import com.docket.app.enums.Visibility;
import com.docket.app.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// Decides who can see inside a tray. A sub-folder and its documents always follow
// the visibility of the tray at the top of their tree.
// Callers should answer "not found" when access is denied, so that people without
// access can't tell whether a folder or document exists.
@Service
@RequiredArgsConstructor
public class FolderAccessService {
    private final TenantAuthorizationService tenantAuthorizationService;
    private final FolderRepository folderRepository;

    // Owners and Admins see everything. Everyone else only sees ALL_MEMBERS trays.
    // CUSTOM trays have no sharing rules yet (#62), so they stay restricted for now.
    public static boolean isVisibleTo(Roles role, Visibility trayVisibility) {
        return role == Roles.OWNER || role == Roles.ADMIN || trayVisibility == Visibility.ALL_MEMBERS;
    }

    // "Can this person see inside this folder or tray?"
    // A folder that doesn't exist or belongs to another organization is a "no".
    public boolean canAccessFolder(Authentication authentication, UUID tenantId, UUID folderId) {
        Roles role = tenantAuthorizationService.getMembershipRole(authentication, tenantId);

        return findFolder(tenantId, folderId)
                .flatMap(folder -> folder.getRootTrayId() == null
                        ? Optional.of(folder)
                        : findFolder(tenantId, folder.getRootTrayId()))
                .map(tray -> isVisibleTo(role, tray.getVisibility()))
                .orElse(false);
    }

    // "Can this person see trays that aren't open to all members?"
    // Used by AI search, which filters by tray visibility inside its SQL queries.
    public boolean canAccessRestrictedTrays(Authentication authentication, UUID tenantId) {
        Roles role = tenantAuthorizationService.getMembershipRole(authentication, tenantId);
        return isVisibleTo(role, Visibility.ADMIN_ONLY);
    }

    private Optional<Folder> findFolder(UUID tenantId, UUID folderId) {
        return folderRepository.findById(folderId)
                .filter(folder -> tenantId.equals(folder.getTenantId()));
    }
}
