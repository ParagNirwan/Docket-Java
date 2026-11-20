-- Custom roles: a named set of extra permissions an organization defines. A member keeps a
-- built-in role (which also decides tray visibility) and may hold one custom role on top.
CREATE TABLE tenant_roles (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(255),
    permissions JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX uk_tenant_roles_name ON tenant_roles (tenant_id, LOWER(name));

ALTER TABLE tenant_membership
    ADD COLUMN custom_role_id UUID REFERENCES tenant_roles(id) ON DELETE SET NULL;
CREATE INDEX idx_tenant_membership_custom_role ON tenant_membership (custom_role_id);
