package com.docket.app.service;

import com.docket.app.entity.Folder;
import com.docket.app.enums.Roles;
import com.docket.app.enums.Visibility;
import com.docket.app.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderAccessServiceTest {
    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @Mock
    private FolderRepository folderRepository;

    @InjectMocks
    private FolderAccessService folderAccessService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    private Folder saveFolder(UUID ownerTenantId, UUID rootTrayId, Visibility visibility) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setTenantId(ownerTenantId);
        folder.setRootTrayId(rootTrayId);
        folder.setVisibility(visibility);
        when(folderRepository.findById(folder.getId())).thenReturn(Optional.of(folder));
        return folder;
    }

    @ParameterizedTest
    @CsvSource({
            "OWNER,   ALL_MEMBERS, true",
            "OWNER,   ADMIN_ONLY,  true",
            "OWNER,   CUSTOM,      true",
            "ADMIN,   ALL_MEMBERS, true",
            "ADMIN,   ADMIN_ONLY,  true",
            "ADMIN,   CUSTOM,      true",
            "MANAGER, ALL_MEMBERS, true",
            "MANAGER, ADMIN_ONLY,  false",
            "MANAGER, CUSTOM,      false",
            "MEMBER,  ALL_MEMBERS, true",
            "MEMBER,  ADMIN_ONLY,  false",
            "MEMBER,  CUSTOM,      false"
    })
    void trayAccessFollowsRoleAndVisibility(Roles role, Visibility visibility, boolean expected) {
        when(tenantAuthorizationService.getMembershipRole(authentication, tenantId)).thenReturn(role);
        Folder tray = saveFolder(tenantId, null, visibility);

        assertEquals(expected, folderAccessService.canAccessFolder(authentication, tenantId, tray.getId()));
    }

    @Test
    void subFolderFollowsItsTrayEvenWhenItsOwnVisibilityIsStale() {
        when(tenantAuthorizationService.getMembershipRole(authentication, tenantId)).thenReturn(Roles.MEMBER);
        // The tray was made admin-only after this sub-folder copied its old visibility.
        Folder tray = saveFolder(tenantId, null, Visibility.ADMIN_ONLY);
        Folder subFolder = saveFolder(tenantId, tray.getId(), Visibility.ALL_MEMBERS);

        assertFalse(folderAccessService.canAccessFolder(authentication, tenantId, subFolder.getId()));
    }

    @Test
    void subFolderOfOpenTrayIsAccessibleToMember() {
        when(tenantAuthorizationService.getMembershipRole(authentication, tenantId)).thenReturn(Roles.MEMBER);
        Folder tray = saveFolder(tenantId, null, Visibility.ALL_MEMBERS);
        Folder subFolder = saveFolder(tenantId, tray.getId(), Visibility.ALL_MEMBERS);

        assertTrue(folderAccessService.canAccessFolder(authentication, tenantId, subFolder.getId()));
    }

    @Test
    void folderOfAnotherTenantIsNotAccessibleEvenToOwner() {
        when(tenantAuthorizationService.getMembershipRole(authentication, tenantId)).thenReturn(Roles.OWNER);
        Folder otherTenantTray = saveFolder(UUID.randomUUID(), null, Visibility.ALL_MEMBERS);

        assertFalse(folderAccessService.canAccessFolder(authentication, tenantId, otherTenantTray.getId()));
    }

    @Test
    void missingFolderIsNotAccessible() {
        UUID missingId = UUID.randomUUID();
        when(tenantAuthorizationService.getMembershipRole(authentication, tenantId)).thenReturn(Roles.OWNER);
        when(folderRepository.findById(missingId)).thenReturn(Optional.empty());

        assertFalse(folderAccessService.canAccessFolder(authentication, tenantId, missingId));
    }

    @ParameterizedTest
    @CsvSource({"OWNER, true", "ADMIN, true", "MANAGER, false", "MEMBER, false"})
    void onlyOwnersAndAdminsCanAccessRestrictedTrays(Roles role, boolean expected) {
        when(tenantAuthorizationService.getMembershipRole(authentication, tenantId)).thenReturn(role);

        assertEquals(expected, folderAccessService.canAccessRestrictedTrays(authentication, tenantId));
    }
}
