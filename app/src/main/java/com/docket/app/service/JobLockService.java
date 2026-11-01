package com.docket.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Lets one pod run a scheduled job. The first to arrive takes a lease and the rest skip
// their turn. If that pod dies, the job simply runs at the next schedule after the lease
// ends, which is safe because every job here is idempotent.
@Service
@RequiredArgsConstructor
public class JobLockService {
    private final JdbcTemplate jdbcTemplate;

    public boolean tryAcquire(String jobName, Duration lease) {
        return jdbcTemplate.update("""
                INSERT INTO job_locks (name, locked_until)
                VALUES (?, now() + make_interval(secs => ?))
                ON CONFLICT (name) DO UPDATE SET locked_until = EXCLUDED.locked_until
                WHERE job_locks.locked_until <= now()
                """, jobName, (double) lease.toSeconds()) == 1;
    }
}
