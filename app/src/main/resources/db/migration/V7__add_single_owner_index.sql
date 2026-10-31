-- An organization can only have one active Owner at a time.
-- Enforced here as well as in the service, so two ownership transfers running
-- at the same moment can never leave an organization with two Owners.
CREATE UNIQUE INDEX uk_tenant_membership_single_active_owner
    ON tenant_membership (tenant_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';
