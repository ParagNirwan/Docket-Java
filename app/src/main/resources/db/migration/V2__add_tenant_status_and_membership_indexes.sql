-- Add a "status" column to organizations so we can mark them as active or inactive.
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS status VARCHAR(255);

-- Any existing organizations that don't have a status yet? Mark them as active.
UPDATE tenants
SET status = 'ACTIVE'
WHERE status IS NULL;

-- From now on, every new org gets "ACTIVE" by default, and status can never be blank.
ALTER TABLE tenants
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ALTER COLUMN status SET NOT NULL;

-- Database shortcuts (indexes) to make common lookups fast:

-- "Is this user a member of this org?" — used by the bouncer on every request.
CREATE INDEX IF NOT EXISTS idx_tenant_membership_user_tenant_status
    ON tenant_membership (user_id, tenant_id, status);

-- "Which orgs does this user belong to?" — used when listing a user's organizations.
CREATE INDEX IF NOT EXISTS idx_tenant_membership_user_status
    ON tenant_membership (user_id, status);
