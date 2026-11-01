package com.docket.app.repository;

import com.docket.app.entity.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    // Takes a batch of due emails by pushing their next attempt into the future.
    // SKIP LOCKED lets several pods poll at once without taking the same email, and
    // if a pod dies mid-send the email simply comes due again when the lease runs out.
    @Transactional
    @Query(value = """
            UPDATE email_outbox
            SET attempts = attempts + 1, next_attempt_at = :leaseUntil
            WHERE id IN (
                SELECT id FROM email_outbox
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                ORDER BY next_attempt_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            """, nativeQuery = true)
    List<EmailOutbox> claimDue(@Param("leaseUntil") Instant leaseUntil, @Param("batchSize") int batchSize);

    @Transactional
    @Modifying
    @Query("delete from EmailOutbox e where e.finishedAt < :cutoff")
    int deleteFinishedBefore(@Param("cutoff") Instant cutoff);
}
