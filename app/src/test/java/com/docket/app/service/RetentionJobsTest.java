package com.docket.app.service;

import com.docket.app.repository.NotificationRepository;
import com.docket.app.repository.RetentionExpiryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionJobsTest {
    @Mock private JobLockService jobLockService;
    @Mock private RetentionExpiryService retentionExpiryService;
    @Mock private RetentionExpiryRepository retentionExpiryRepository;
    @Mock private TrashService trashService;
    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private RetentionJobs jobs;

    @Test
    void anotherPodHoldingTheLease_meansThisOneSkipsItsTurn() {
        when(jobLockService.tryAcquire(any(), any(), any(), any())).thenReturn(false);

        jobs.runRetentionExpiry();
        jobs.runTrashPurge();
        jobs.runDailyDigest();

        verifyNoInteractions(retentionExpiryService, retentionExpiryRepository, trashService, notificationRepository);
    }

    @Test
    void holdingTheLease_runsEverything() {
        when(jobLockService.tryAcquire(eq("retention-expiry"), any(), any(), any())).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(retentionExpiryService.findDueForAutoDelete(200)).thenReturn(List.of(id));

        jobs.runRetentionExpiry();

        verify(retentionExpiryService).warnAboutExpiring();
        verify(retentionExpiryService).handleExpired();
        verify(retentionExpiryService).autoDelete(id);
        verify(jobLockService).release(eq("retention-expiry"), any(), any(), eq(true));
    }

    @Test
    void failingBeforeCompletion_releasesLeaseWithoutMarkingTheRunComplete() {
        when(jobLockService.tryAcquire(eq("retention-expiry"), any(), any(), any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(retentionExpiryService).warnAboutExpiring();

        assertThrows(RuntimeException.class, jobs::runRetentionExpiry);

        verify(jobLockService).release(eq("retention-expiry"), any(), any(), eq(false));
    }

    @Test
    void oneDigestFailing_doesntStopTheOthers() {
        when(jobLockService.tryAcquire(eq("notification-digest"), any(), any(), any())).thenReturn(true);
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        when(notificationRepository.findUsersWithPendingDigest()).thenReturn(List.of(bad, good));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(notificationService).sendDigest(bad);

        jobs.runDailyDigest();

        verify(notificationService).sendDigest(good);
        verify(jobLockService).release(eq("notification-digest"), any(), any(), eq(true));
    }

    @Test
    void processAll_keepsGoingUntilABatchComesUpShort() {
        ReflectionTestUtils.setField(jobs, "batchSize", 2);
        List<List<UUID>> batches = new ArrayList<>(List.of(
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                List.of(UUID.randomUUID())));

        int done = jobs.processAll(limit -> batches.removeFirst(), id -> { });

        assertEquals(3, done);
    }

    @Test
    void processAll_stopsWhenNothingSucceeds_ratherThanLoopingForever() {
        ReflectionTestUtils.setField(jobs, "batchSize", 1);
        UUID stuck = UUID.randomUUID();

        int done = jobs.processAll(limit -> List.of(stuck), id -> {
            throw new RuntimeException("boom");
        });

        assertEquals(0, done);
        verify(trashService, never()).purgeExpired(any());
    }
}
