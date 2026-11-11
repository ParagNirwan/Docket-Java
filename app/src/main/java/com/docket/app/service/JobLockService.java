package com.docket.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

// Lets one pod run a scheduled job. The first to arrive takes a lease and the rest skip
// their turn. A completed run releases its lease, while the run date prevents another pod
// from repeating that day's schedule. If a pod dies, the lease expires for recovery.
@Service
@RequiredArgsConstructor
public class JobLockService {
    private final JdbcTemplate jdbcTemplate;

    public boolean tryAcquire(String jobName, Duration lease, LocalDate runDate, UUID lockToken) {
        return jdbcTemplate.update("""
                INSERT INTO job_locks (name, locked_until, last_completed_date, lock_token)
                VALUES (?, now() + make_interval(secs => ?), DATE '1970-01-01', ?)
                ON CONFLICT (name) DO UPDATE
                SET locked_until = EXCLUDED.locked_until, lock_token = EXCLUDED.lock_token
                WHERE job_locks.locked_until <= now()
                  AND job_locks.last_completed_date < ?
                """, jobName, (double) lease.toSeconds(), lockToken, runDate) == 1;
    }

    public void release(String jobName, LocalDate runDate, UUID lockToken, boolean completed) {
        jdbcTemplate.update("""
                UPDATE job_locks
                SET locked_until = now(),
                    last_completed_date = CASE WHEN ? THEN ? ELSE last_completed_date END
                WHERE name = ? AND lock_token = ?
                """, completed, runDate, jobName, lockToken);
    }
}
