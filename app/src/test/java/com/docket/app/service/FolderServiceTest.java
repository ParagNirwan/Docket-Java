package com.docket.app.service;

import com.docket.app.dto.CreateFolderRequestDTO;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.Visibility;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {
    private static final String SLUG = "acme";

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FolderAccessService folderAccessService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FolderService folderService;

    private UUID tenantId;
    private UUID folderId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        folderId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
    }

    @Test
    void getChildren_hiddenFolder_throws404() {
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> folderService.getChildren(SLUG, folderId, authentication));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(folderRepository);
    }

    @Test
    void createSubFolder_hiddenParent_throws404() {
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> folderService.createSubFolder(SLUG, folderId, mock(CreateFolderRequestDTO.class), authentication));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(folderRepository);
    }

    @Test
    void createSubFolder_underTray_pointsAtTray() {
        Folder tray = parentFolder(null);

        assertEquals(tray.getId(), createUnder(tray).getRootTrayId());
    }

    @Test
    void createSubFolder_underSubFolder_pointsAtSameTray() {
        UUID trayId = UUID.randomUUID();
        Folder subFolder = parentFolder(trayId);

        assertEquals(trayId, createUnder(subFolder).getRootTrayId());
    }

    private Folder parentFolder(UUID rootTrayId) {
        Folder parent = new Folder();
        parent.setId(folderId);
        parent.setTenantId(tenantId);
        parent.setRootTrayId(rootTrayId);
        parent.setVisibility(Visibility.ALL_MEMBERS);
        return parent;
    }

    private Folder createUnder(Folder parent) {
        User user = new User("Ada Lovelace", "ada@example.com", "encoded-password");
        user.setId(UUID.randomUUID());
        CreateFolderRequestDTO request = mock(CreateFolderRequestDTO.class);
        when(request.getName()).thenReturn("Contracts");
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(true);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(parent));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Folder created = folderService.createSubFolder(SLUG, folderId, request, authentication);

        verify(folderRepository).save(created);
        return created;
    }
}
