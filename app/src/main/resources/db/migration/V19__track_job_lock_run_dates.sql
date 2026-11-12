ALTER TABLE job_locks
    ADD COLUMN last_completed_date DATE NOT NULL DEFAULT DATE '1970-01-01',
    ADD COLUMN lock_token UUID;
