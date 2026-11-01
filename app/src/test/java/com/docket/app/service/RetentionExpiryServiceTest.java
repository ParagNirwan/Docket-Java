package com.docket.app.service;

import com.docket.app.entity.Document;
import com.docket.app.entity.Tenant;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.repository.RetentionExpiryRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.service.email.EmailTemplate;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetentionExpiryServiceTest {
    @Mock private RetentionExpiryRepository expiryRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TrashService trashService;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    @InjectMocks
    private RetentionExpiryService service;

    private UUID tenantId;
    private Document document;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Acme");
        tenant.setSlug("acme");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        document = new Document();
        document.setId(UUID.randomUUID());
        document.setTenantId(tenantId);
        document.setFileStatus(FileStatus.ACTIVE);
        document.setRetainUntil(Instant.now().minus(Duration.ofDays(1)));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
    }

    private RetentionExpiryRepository.TenantExpiry expiry() {
        return new RetentionExpiryRepository.TenantExpiry(tenantId, 3, Instant.now());
    }

    @Test
    void warnAboutExpiring_tellsPeopleThenMarksThem() {
        when(expiryRepository.findExpiring(any(), any())).thenReturn(List.of(expiry()));

        service.warnAboutExpiring();

        verify(notificationService).notifyPermissionHolders(eq(tenantId), eq(Permission.RETENTION_READ),
                eq(NotificationType.RETENTION_EXPIRING), eq("3 documents reaching the end of retention"), any(), any(),
                eq(EmailTemplate.RETENTION_EXPIRING), any());
        verify(expiryRepository).markExpiringNotified(eq(tenantId), any(), any());
    }

    @Test
    void warnAboutExpiring_nothingDueTellsNobody() {
        service.warnAboutExpiring();

        verifyNoInteractions(notificationService);
    }

    @Test
    void handleExpired_notifiesAndClosesOffTheRest() {
        when(expiryRepository.findExpiredToNotify(any())).thenReturn(List.of(expiry()));

        service.handleExpired();

        verify(notificationService).notifyPermissionHolders(eq(tenantId), eq(Permission.RETENTION_READ),
                eq(NotificationType.RETENTION_EXPIRED), any(), any(), any());
        verify(expiryRepository).markExpiredNotified(eq(tenantId), any());
        verify(expiryRepository).markNoActionNeeded(any());
    }

    @Test
    void autoDelete_goesToTrashAndIsAuditedAsTheSystem() {
        service.autoDelete(document.getId());

        assertNotNull(document.getExpiryHandledAt());
        verify(trashService).moveToTrash(document, null);
        verify(auditService).recordSystem(eq(tenantId), eq(AuditAction.DOCUMENT_AUTO_DELETED), eq("DOCUMENT"), eq(document.getId()), any());
    }

    @Test
    void autoDelete_skipsAHeldDocument() {
        document.setLegalHold(true);

        service.autoDelete(document.getId());

        verify(trashService, never()).moveToTrash(any(), any());
    }

    @Test
    void autoDelete_skipsAPolicyExtendedSinceItWasPicked() {
        document.setRetainUntil(Instant.now().plus(Duration.ofDays(1)));

        service.autoDelete(document.getId());

        verify(trashService, never()).moveToTrash(any(), any());
    }

    @Test
    void autoDelete_runningTwiceDoesNothingTheSecondTime() {
        document.setFileStatus(FileStatus.SOFT_DELETED);

        service.autoDelete(document.getId());

        verify(trashService, never()).moveToTrash(any(), any());
    }
}
