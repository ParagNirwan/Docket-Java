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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;

// The daily jobs for retention, trash and digests. Successful runs release their lease,
// while the lock records each completed date to prevent duplicate runs across pods.
// Documents are handled one transaction at a time, so one bad document can't block others.
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
        LocalDate runDate = LocalDate.now();
        UUID lockToken = UUID.randomUUID();
        if (!jobLockService.tryAcquire("retention-expiry", LEASE, runDate, lockToken)) {
            return;
        }
        boolean completed = false;
        try {
            retentionExpiryService.warnAboutExpiring();
            retentionExpiryService.handleExpired();
            int deleted = processAll(retentionExpiryService::findDueForAutoDelete, retentionExpiryService::autoDelete);
            log.info("Retention expiry finished: {} documents moved to the Trash", deleted);
            completed = true;
        } finally {
            jobLockService.release("retention-expiry", runDate, lockToken, completed);
        }
    }

    @Scheduled(cron = "${docket.retention.trash-purge-cron}")
    public void runTrashPurge() {
        LocalDate runDate = LocalDate.now();
        UUID lockToken = UUID.randomUUID();
        if (!jobLockService.tryAcquire("trash-purge", LEASE, runDate, lockToken)) {
            return;
        }
        boolean completed = false;
        try {
            int purged = processAll(retentionExpiryRepository::findDueForPurge, trashService::purgeExpired);
            log.info("Trash purge finished: {} documents removed", purged);
            completed = true;
        } finally {
            jobLockService.release("trash-purge", runDate, lockToken, completed);
        }
    }

    @Scheduled(cron = "${docket.notifications.digest-cron}")
    public void runDailyDigest() {
        LocalDate runDate = LocalDate.now();
        UUID lockToken = UUID.randomUUID();
        if (!jobLockService.tryAcquire("notification-digest", LEASE, runDate, lockToken)) {
            return;
        }
        boolean completed = false;
        try {
            for (UUID userId : notificationRepository.findUsersWithPendingDigest()) {
                try {
                    notificationService.sendDigest(userId);
                } catch (RuntimeException e) {
                    log.error("Could not send the digest to user {}", userId, e);
                }
            }
            completed = true;
        } finally {
            jobLockService.release("notification-digest", runDate, lockToken, completed);
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
