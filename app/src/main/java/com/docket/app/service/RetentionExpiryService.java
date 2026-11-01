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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// What the retention job does. Notifying and marking documents as notified share a
// transaction, so a crash can't tell people twice or lose the message.
@Service
@RequiredArgsConstructor
public class RetentionExpiryService {
    static final Duration WARNING_PERIOD = Duration.ofDays(30);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final RetentionExpiryRepository expiryRepository;
    private final DocumentRepository documentRepository;
    private final TenantRepository tenantRepository;
    private final TrashService trashService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public void warnAboutExpiring() {
        Instant now = Instant.now();
        Instant horizon = now.plus(WARNING_PERIOD);
        for (RetentionExpiryRepository.TenantExpiry expiry : expiryRepository.findExpiring(now, horizon)) {
            tenantRepository.findById(expiry.tenantId()).ifPresent(tenant -> {
                String date = DATE.format(expiry.earliest());
                notificationService.notifyPermissionHolders(tenant.getId(), Permission.RETENTION_READ,
                        NotificationType.RETENTION_EXPIRING,
                        count(expiry.documents()) + " reaching the end of retention",
                        "In " + tenant.getName() + ", starting " + date + ".",
                        retentionLink(tenant),
                        EmailTemplate.RETENTION_EXPIRING,
                        Map.of("documentCount", expiry.documents(), "tenantName", tenant.getName(), "expiryDate", date));
            });
            expiryRepository.markExpiringNotified(expiry.tenantId(), now, horizon);
        }
    }

    @Transactional
    public void handleExpired() {
        Instant now = Instant.now();
        for (RetentionExpiryRepository.TenantExpiry expiry : expiryRepository.findExpiredToNotify(now)) {
            tenantRepository.findById(expiry.tenantId()).ifPresent(tenant ->
                    notificationService.notifyPermissionHolders(tenant.getId(), Permission.RETENTION_READ,
                            NotificationType.RETENTION_EXPIRED,
                            count(expiry.documents()) + " reached the end of retention",
                            "In " + tenant.getName() + ". They can now be deleted.",
                            retentionLink(tenant)));
            expiryRepository.markExpiredNotified(expiry.tenantId(), now);
        }
        expiryRepository.markNoActionNeeded(now);
    }

    public List<UUID> findDueForAutoDelete(int limit) {
        return expiryRepository.findDueForAutoDelete(Instant.now(), limit);
    }

    // To the Trash rather than gone: the trash period still gives people time to notice.
    @Transactional
    public void autoDelete(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        Instant now = Instant.now();
        if (document == null || document.getFileStatus() != FileStatus.ACTIVE
                || document.isLegalHold() || document.isRetentionLocked(now)) {
            return;
        }
        document.setExpiryHandledAt(now);
        trashService.moveToTrash(document, null);
        auditService.recordSystem(document.getTenantId(), AuditAction.DOCUMENT_AUTO_DELETED, "DOCUMENT", documentId,
                Map.of("fileName", String.valueOf(document.getOriginalFileName()),
                        "retainUntil", String.valueOf(document.getRetainUntil())));
    }

    private static String retentionLink(Tenant tenant) {
        return NotificationService.workspaceLink(tenant.getSlug(), "settings", "retention");
    }

    private static String count(long documents) {
        return documents + (documents == 1 ? " document" : " documents");
    }
}
