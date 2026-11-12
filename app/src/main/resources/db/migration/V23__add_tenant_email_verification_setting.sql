ALTER TABLE tenants
    ADD COLUMN email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE;
