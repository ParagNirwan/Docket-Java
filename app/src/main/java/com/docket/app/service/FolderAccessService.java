package com.docket.app.service;

import com.docket.app.entity.Folder;
import com.docket.app.enums.Roles;
import com.docket.app.enums.Visibility;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TrayPermissionRepository;
import com.docket.app.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final TrayPermissionRepository trayPermissionRepository;

    // Owners and Admins see everything. Guests only see trays explicitly shared with them.
    // Everyone else sees ALL_MEMBERS trays.
    public static boolean isVisibleTo(Roles role, Visibility trayVisibility) {
        if (role == Roles.OWNER || role == Roles.ADMIN) {
            return true;
        }
        if (role == Roles.GUEST) {
            return false;
        }
        return trayVisibility == Visibility.ALL_MEMBERS;
    }

    public boolean canAccessTray(Authentication authentication, UUID tenantId, Folder tray) {
        Roles role = tenantAuthorizationService.getMembershipRole(authentication, tenantId);
        if (role == Roles.OWNER || role == Roles.ADMIN) {
            return true;
        }
        if (tray.getVisibility() == Visibility.ALL_MEMBERS) {
            return role != Roles.GUEST;
        }
        if (tray.getVisibility() == Visibility.CUSTOM) {
            return userRepository.findByEmailIgnoreCase(authentication.getName())
                    .map(user -> trayPermissionRepository.existsByTrayIdAndUserId(tray.getId(), user.getId()))
                    .orElse(false);
        }
        return false;
    }

    // "Can this person see inside this folder or tray?"
    // A folder that doesn't exist or belongs to another organization is a "no".
    public boolean canAccessFolder(Authentication authentication, UUID tenantId, UUID folderId) {
        return findFolder(tenantId, folderId)
                .flatMap(folder -> folder.getRootTrayId() == null
                        ? Optional.of(folder)
                        : findFolder(tenantId, folder.getRootTrayId()))
                .map(tray -> canAccessTray(authentication, tenantId, tray))
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
