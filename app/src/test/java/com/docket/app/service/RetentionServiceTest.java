package com.docket.app.service;

import com.docket.app.entity.Document;
import com.docket.app.entity.Folder;
import com.docket.app.entity.RetentionPolicy;
import com.docket.app.enums.ClockStart;
import com.docket.app.enums.LockMode;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.FolderRepository;
import com.docket.app.repository.RetentionPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetentionServiceTest {
    @Mock
    private RetentionPolicyRepository policyRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private RetentionService retentionService;

    private Folder tray;
    private Folder subFolder;
    private Document document;

    @BeforeEach
    void setUp() {
        tray = folder(null, false);
        subFolder = folder(tray.getId(), false);
        document = new Document();
        document.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
    }

    private Folder folder(UUID parentId, boolean archive) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setParentFolderId(parentId);
        folder.setIsArchive(archive);
        when(folderRepository.findById(folder.getId())).thenReturn(Optional.of(folder));
        return folder;
    }

    private RetentionPolicy policy(Folder on, int days, LockMode mode, ClockStart clock, boolean subfolders) {
        RetentionPolicy policy = new RetentionPolicy();
        policy.setId(UUID.randomUUID());
        policy.setFolderId(on.getId());
        policy.setRetentionDays(days);
        policy.setLockMode(mode);
        policy.setClockStart(clock);
        policy.setAppliesToSubfolders(subfolders);
        return policy;
    }

    private void policies(RetentionPolicy... policies) {
        when(policyRepository.findByFolderIdIn(anyCollection())).thenReturn(List.of(policies));
    }

    @Test
    void noPolicy_leavesTheDocumentUnlocked() {
        policies();

        retentionService.applyOnEntry(document, tray.getId());

        assertNull(document.getRetainUntil());
        assertNull(document.getLockMode());
    }

    @Test
    void uploadedAtClock_countsFromUpload() {
        RetentionPolicy policy = policy(tray, 10, LockMode.GOVERNANCE, ClockStart.UPLOADED_AT, true);
        policies(policy);

        retentionService.applyOnEntry(document, tray.getId());

        assertEquals(Instant.parse("2020-01-11T00:00:00Z"), document.getRetainUntil());
        assertEquals(policy.getId(), document.getRetentionPolicyId());
    }

    @Test
    void archivedAtClock_countsFromWhenItArrives() {
        policies(policy(tray, 10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true));
        Instant before = Instant.now();

        retentionService.applyOnEntry(document, tray.getId());

        assertTrue(document.getRetainUntil().isAfter(before.plus(Duration.ofDays(10)).minusSeconds(1)));
        assertNotNull(document.getRetentionClockAt());
    }

    @Test
    void aDateNeverMovesEarlier() {
        Instant far = Instant.now().plus(Duration.ofDays(5000));
        document.setRetainUntil(far);
        document.setLockMode(LockMode.GOVERNANCE);
        policies(policy(tray, 10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true));

        retentionService.applyOnEntry(document, tray.getId());

        assertEquals(far, document.getRetainUntil());
    }

    @Test
    void theStrictestPolicyOverAFolderWins() {
        RetentionPolicy trayPolicy = policy(tray, 3650, LockMode.COMPLIANCE, ClockStart.ARCHIVED_AT, true);
        RetentionPolicy subPolicy = policy(subFolder, 10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true);
        policies(subPolicy, trayPolicy);

        retentionService.applyOnEntry(document, subFolder.getId());

        assertEquals(LockMode.COMPLIANCE, document.getLockMode());
        assertEquals(trayPolicy.getId(), document.getRetentionPolicyId());
        assertTrue(document.getRetainUntil().isAfter(Instant.now().plus(Duration.ofDays(3000))));
    }

    @Test
    void aPolicyThatDoesntReachDownIsIgnoredBelowItsFolder() {
        policies(policy(tray, 10, LockMode.COMPLIANCE, ClockStart.ARCHIVED_AT, false));

        retentionService.applyOnEntry(document, subFolder.getId());

        assertNull(document.getRetainUntil());
    }

    @Test
    void anExpiredComplianceLockDoesntMakeTheNextOneCompliance() {
        document.setRetainUntil(Instant.now().minus(Duration.ofDays(1)));
        document.setLockMode(LockMode.COMPLIANCE);
        policies(policy(tray, 10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true));

        retentionService.applyOnEntry(document, tray.getId());

        assertEquals(LockMode.GOVERNANCE, document.getLockMode());
    }

    @Test
    void aNewLaterDateMeansPeopleGetWarnedAgain() {
        document.setRetainUntil(Instant.now().minus(Duration.ofDays(1)));
        document.setExpiringNotifiedAt(Instant.now());
        document.setExpiryHandledAt(Instant.now());
        policies(policy(tray, 10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true));

        retentionService.applyOnEntry(document, tray.getId());

        assertNull(document.getExpiringNotifiedAt());
        assertNull(document.getExpiryHandledAt());
    }

    @Test
    void arrivingInAnArchiveTray_marksTheDocumentArchived() {
        Folder archive = folder(null, true);
        Folder inside = folder(archive.getId(), false);
        policies();

        retentionService.applyOnEntry(document, inside.getId());

        assertNotNull(document.getArchivedAt());
    }

    @Test
    void movedFolder_getsOnlyPoliciesThatReachDown() {
        RetentionPolicy reaches = policy(tray, 10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, true);
        RetentionPolicy doesnt = policy(subFolder, 10, LockMode.GOVERNANCE, ClockStart.ARCHIVED_AT, false);
        policies(reaches, doesnt);
        UUID moved = UUID.randomUUID();

        retentionService.applyToMovedFolder(moved, subFolder.getId());

        verify(documentRepository).applyRetentionPolicy(moved, true, reaches.getId(), 10, "GOVERNANCE", "ARCHIVED_AT");
        verify(documentRepository, never()).applyRetentionPolicy(any(), eq(true), eq(doesnt.getId()), eq(10), any(), any());
        verify(documentRepository, never()).stampArchivedUnder(any(), any());
    }

    @Test
    void movedFolder_intoAnArchiveTrayMarksItsDocumentsArchived() {
        Folder archive = folder(null, true);
        policies();
        UUID moved = UUID.randomUUID();

        retentionService.applyToMovedFolder(moved, archive.getId());

        verify(documentRepository).stampArchivedUnder(eq(moved), any());
    }
}
