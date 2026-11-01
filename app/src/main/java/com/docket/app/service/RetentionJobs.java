package com.docket.app.service;

import com.docket.app.repository.NotificationRepository;
import com.docket.app.repository.RetentionExpiryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;

// The daily jobs for retention, trash and digests. Leases are a little under a day so
// a run that starts late can't make tomorrow's skip its turn. Documents are handled one
// transaction at a time, so one bad document can't undo or block the others.
@Component
@RequiredArgsConstructor
public class RetentionJobs {
    private static final Logger log = LoggerFactory.getLogger(RetentionJobs.class);
    private static final Duration LEASE = Duration.ofHours(20);

    private final JobLockService jobLockService;
    private final RetentionExpiryService retentionExpiryService;
    private final RetentionExpiryRepository retentionExpiryRepository;
    private final TrashService trashService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    @Value("${docket.retention.batch-size}")
    private int batchSize = 200;

    @Scheduled(cron = "${docket.retention.expiry-cron}")
    public void runRetentionExpiry() {
        if (!jobLockService.tryAcquire("retention-expiry", LEASE)) {
            return;
        }
        retentionExpiryService.warnAboutExpiring();
        retentionExpiryService.handleExpired();
        int deleted = processAll(retentionExpiryService::findDueForAutoDelete, retentionExpiryService::autoDelete);
        log.info("Retention expiry finished: {} documents moved to the Trash", deleted);
    }

    @Scheduled(cron = "${docket.retention.trash-purge-cron}")
    public void runTrashPurge() {
        if (!jobLockService.tryAcquire("trash-purge", LEASE)) {
            return;
        }
        int purged = processAll(retentionExpiryRepository::findDueForPurge, trashService::purgeExpired);
        log.info("Trash purge finished: {} documents removed", purged);
    }

    @Scheduled(cron = "${docket.notifications.digest-cron}")
    public void runDailyDigest() {
        if (!jobLockService.tryAcquire("notification-digest", LEASE)) {
            return;
        }
        for (UUID userId : notificationRepository.findUsersWithPendingDigest()) {
            try {
                notificationService.sendDigest(userId);
            } catch (RuntimeException e) {
                log.error("Could not send the digest to user {}", userId, e);
            }
        }
    }

    // Keeps taking batches until one makes no progress, which also stops a document that
    // always fails from keeping this running forever.
    int processAll(IntFunction<List<UUID>> nextBatch, Consumer<UUID> action) {
        int done = 0;
        while (true) {
            List<UUID> batch = nextBatch.apply(batchSize);
            int succeeded = 0;
            for (UUID id : batch) {
                try {
                    action.accept(id);
                    succeeded++;
                } catch (RuntimeException e) {
                    log.error("Retention job could not process document {}", id, e);
                }
            }
            done += succeeded;
            if (succeeded == 0 || batch.size() < batchSize) {
                return done;
            }
        }
    }
}
