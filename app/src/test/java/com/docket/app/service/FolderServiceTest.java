package com.docket.app.service;

import com.docket.app.dto.BreadcrumbDTO;
import com.docket.app.dto.CreateFolderRequestDTO;
import com.docket.app.dto.UpdateFolderRequestDTO;
import com.docket.app.entity.Folder;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.Permission;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Mock
    private AuditService auditService;

    @Mock
    private JdbcTemplate jdbcTemplate;

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

    private Folder folder(UUID id, UUID parentId, UUID rootTrayId, boolean isTray) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setTenantId(tenantId);
        folder.setParentFolderId(parentId);
        folder.setRootTrayId(rootTrayId);
        folder.setName(isTray ? "Tray" : "Contracts");
        folder.setIsTray(isTray);
        folder.setIsDefault(false);
        folder.setVisibility(Visibility.ALL_MEMBERS);
        when(folderRepository.findById(id)).thenReturn(Optional.of(folder));
        lenient().when(folderAccessService.canAccessFolder(authentication, tenantId, id)).thenReturn(true);
        return folder;
    }

    private void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable call) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, call).getStatusCode());
    }

    @Test
    void updateFolder_defaultTray_isForbidden() {
        Folder inbox = folder(folderId, null, null, true);
        inbox.setIsDefault(true);

        assertStatus(HttpStatus.FORBIDDEN, () -> folderService.updateFolder(SLUG, folderId,
                new UpdateFolderRequestDTO("Renamed", null), authentication));
        verify(folderRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateFolder_movingATray_isRejected() {
        folder(folderId, null, null, true);

        assertStatus(HttpStatus.BAD_REQUEST, () -> folderService.updateFolder(SLUG, folderId,
                new UpdateFolderRequestDTO(null, UUID.randomUUID()), authentication));
    }

    @Test
    void updateFolder_trayNeedsTrayPermissionAndFolderNeedsFolderPermission() {
        folder(folderId, null, null, true);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(tenantAuthorizationService)
                .requirePermission(authentication, tenantId, Permission.TRAYS_UPDATE);

        assertStatus(HttpStatus.FORBIDDEN, () -> folderService.updateFolder(SLUG, folderId,
                new UpdateFolderRequestDTO("Renamed", null), authentication));
    }

    @Test
    void updateFolder_hiddenFolder_isNotFound() {
        when(folderAccessService.canAccessFolder(authentication, tenantId, folderId)).thenReturn(false);

        assertStatus(HttpStatus.NOT_FOUND, () -> folderService.updateFolder(SLUG, folderId,
                new UpdateFolderRequestDTO("Renamed", null), authentication));
    }

    @Test
    void updateFolder_rename_savesAndAudits() {
        UUID trayId = UUID.randomUUID();
        Folder sub = folder(folderId, trayId, trayId, false);
        when(folderRepository.saveAndFlush(sub)).thenReturn(sub);

        Folder saved = folderService.updateFolder(SLUG, folderId, new UpdateFolderRequestDTO(" Renamed ", null), authentication);

        assertEquals("Renamed", saved.getName());
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.FOLDER_RENAMED), any(), any(), any());
        verify(folderRepository, never()).updateDescendantsTray(any(), any(), any());
    }

    @Test
    void updateFolder_nameTaken_conflicts() {
        UUID trayId = UUID.randomUUID();
        folder(folderId, trayId, trayId, false);
        when(folderRepository.existsByTenantIdAndParentFolderIdAndNameIgnoreCaseAndIdNot(tenantId, trayId, "Taken", folderId))
                .thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> folderService.updateFolder(SLUG, folderId,
                new UpdateFolderRequestDTO("Taken", null), authentication));
    }

    @Test
    void updateFolder_losingANameRaceToTheDatabase_conflicts() {
        UUID trayId = UUID.randomUUID();
        Folder sub = folder(folderId, trayId, trayId, false);
        when(folderRepository.saveAndFlush(sub)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertStatus(HttpStatus.CONFLICT, () -> folderService.updateFolder(SLUG, folderId,
                new UpdateFolderRequestDTO("Renamed", null), authentication));
    }

    @Test
    void updateFolder_moveIntoItsOwnDescendant_isRejected() {
        UUID trayId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        folder(folderId, trayId, trayId, false);
        folder(childId, folderId, trayId, false);
        when(folderRepository.isSameOrDescendant(folderId, childId)).thenReturn(true);

        assertStatus(HttpStatus.BAD_REQUEST, () -> folderService.updateFolder(SLUG, folderId,
                new UpdateFolderRequestDTO(null, childId), authentication));
        verify(folderRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateFolder_moveIntoHiddenFolder_isNotFoundBeforeAnyNameCheck() {
        UUID trayId = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        folder(folderId, trayId, trayId, false);
        when(folderAccessService.canAccessFolder(authentication, tenantId, hidden)).thenReturn(false);

        assertStatus(HttpStatus.NOT_FOUND, () -> folderService.updateFolder(SLUG, folderId,
                new UpdateFolderRequestDTO(null, hidden), authentication));
        verify(folderRepository, never()).existsByTenantIdAndParentFolderIdAndNameIgnoreCaseAndIdNot(any(), any(), any(), any());
    }

    @Test
    void updateFolder_moveToAnotherTray_takesTheTrayAndItsVisibilityAndUpdatesDescendants() {
        UUID oldTrayId = UUID.randomUUID();
        UUID newTrayId = UUID.randomUUID();
        Folder sub = folder(folderId, oldTrayId, oldTrayId, false);
        Folder newTray = folder(newTrayId, null, null, true);
        newTray.setVisibility(Visibility.ADMIN_ONLY);
        when(folderRepository.saveAndFlush(sub)).thenReturn(sub);

        Folder saved = folderService.updateFolder(SLUG, folderId, new UpdateFolderRequestDTO(null, newTrayId), authentication);

        assertEquals(newTrayId, saved.getParentFolderId());
        assertEquals(newTrayId, saved.getRootTrayId());
        assertEquals(Visibility.ADMIN_ONLY, saved.getVisibility());
        verify(folderRepository).updateDescendantsTray(folderId, newTrayId, "ADMIN_ONLY");
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.FOLDER_MOVED), any(), any(), any());
    }

    @Test
    void updateFolder_moveUnderASubFolder_usesThatSubFoldersTray() {
        UUID oldTrayId = UUID.randomUUID();
        UUID newTrayId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Folder sub = folder(folderId, oldTrayId, oldTrayId, false);
        folder(parentId, newTrayId, newTrayId, false);
        folder(newTrayId, null, null, true);
        when(folderRepository.saveAndFlush(sub)).thenReturn(sub);

        Folder saved = folderService.updateFolder(SLUG, folderId, new UpdateFolderRequestDTO(null, parentId), authentication);

        assertEquals(parentId, saved.getParentFolderId());
        assertEquals(newTrayId, saved.getRootTrayId());
    }

    @Test
    void getBreadcrumb_runsFromTheTrayDownToTheFolder() {
        UUID trayId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        folder(trayId, null, null, true);
        folder(parentId, trayId, trayId, false);
        folder(folderId, parentId, trayId, false);

        var path = folderService.getBreadcrumb(SLUG, folderId, authentication);

        assertEquals(List.of(trayId, parentId, folderId), path.stream().map(BreadcrumbDTO::id).toList());
        assertEquals(true, path.getFirst().isTray());
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
