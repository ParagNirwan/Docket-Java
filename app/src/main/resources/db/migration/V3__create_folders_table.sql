-- V3: Create the folders table for Tray-based hierarchical folder system (Issue #3)
--
-- A single "folders" table holds both Trays (root-level, shown in sidebar)
-- and sub-folders (nested inside trays). The difference:
--   - Tray:       parent_folder_id IS NULL  AND  is_tray = true
--   - Sub-folder:  parent_folder_id IS NOT NULL  AND  is_tray = false

CREATE TABLE folders (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    parent_folder_id UUID                  REFERENCES folders(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    description      VARCHAR(500),
    is_tray          BOOLEAN      NOT NULL DEFAULT false,
    is_default       BOOLEAN      NOT NULL DEFAULT false,
    visibility       VARCHAR(50)  NOT NULL DEFAULT 'ALL_MEMBERS',
    sort_order       INTEGER               DEFAULT 0,
    created_by       UUID                  REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- "Which trays does this tenant have?" — sidebar listing
CREATE INDEX idx_folders_tenant_tray ON folders (tenant_id, is_tray);

-- "What's inside this folder?" — listing children
CREATE INDEX idx_folders_tenant_parent ON folders (tenant_id, parent_folder_id);

-- No two folders/trays with the same name at the same level within a tenant
CREATE UNIQUE INDEX uk_folders_tenant_parent_name
    ON folders (tenant_id, COALESCE(parent_folder_id, '00000000-0000-0000-0000-000000000000'), name);
