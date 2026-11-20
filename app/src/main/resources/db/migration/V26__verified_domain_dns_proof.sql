-- Domain verification used to accept the token the API itself returned, so no existing
-- verification proves DNS control. Reset them; admins re-verify with a TXT record.
UPDATE tenant_domains SET verified_at = NULL, join_mode = 'REQUEST' WHERE verified_at IS NOT NULL;

-- A domain can be verified by only one organization, otherwise auto-join is ambiguous.
CREATE UNIQUE INDEX ux_tenant_domains_verified_domain
    ON tenant_domains (lower(domain))
    WHERE verified_at IS NOT NULL;
