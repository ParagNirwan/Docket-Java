-- V16: Retention, legal hold, trash, archive and notifications (Issues #47-#53, #86)

-- Behaviour hangs off this flag rather than the name "Archive"
ALTER TABLE folders
    ADD COLUMN is_archive BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE tenants
    ADD COLUMN trash_retention_days INTEGER NOT NULL DEFAULT 30
        CHECK (trash_retention_days BETWEEN 1 AND 3650);

-- Trash and archive now rely on these trays, so every organization needs them. A custom
-- tray already using one of the names takes on the role, as names are unique per level.
UPDATE folders SET is_default = true
WHERE is_tray AND parent_folder_id IS NULL AND name IN ('Inbox', 'Archive', 'Trash');

UPDATE folders SET visibility = 'ADMIN_ONLY'
WHERE is_tray AND is_default AND name = 'Trash';

INSERT INTO folders (id, tenant_id, name, description, is_tray, is_default, visibility, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), t.id, tray.name, tray.description, true, true, tray.visibility, tray.sort_order, now(), now()
FROM tenants t
CROSS JOIN (VALUES
    ('Inbox', 'Default Inbox Tray', 'ALL_MEMBERS', 1),
    ('Archive', 'Default Archive Tray', 'ALL_MEMBERS', 2),
    ('Trash', 'Default Trash Tray', 'ADMIN_ONLY', 3)
) AS tray(name, description, visibility, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM folders f
    WHERE f.tenant_id = t.id AND f.is_tray AND f.is_default AND f.name = tray.name
);

UPDATE folders SET is_archive = true
WHERE is_tray AND is_default AND name = 'Archive';

CREATE TABLE retention_policies (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    folder_id             UUID        NOT NULL UNIQUE REFERENCES folders(id) ON DELETE CASCADE,
    retention_days        INTEGER     NOT NULL CHECK (retention_days BETWEEN 1 AND 36500),
    lock_mode             VARCHAR(20) NOT NULL,
    clock_start           VARCHAR(20) NOT NULL DEFAULT 'ARCHIVED_AT',
    applies_to_subfolders BOOLEAN     NOT NULL DEFAULT true,
    on_expiry             VARCHAR(20) NOT NULL DEFAULT 'NOTIFY',
    created_by            UUID        REFERENCES users(id) ON DELETE SET NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_retention_policies_tenant ON retention_policies (tenant_id);

ALTER TABLE documents
    ADD COLUMN retain_until            TIMESTAMP WITH TIME ZONE,
    ADD COLUMN lock_mode               VARCHAR(20),
    ADD COLUMN retention_policy_id     UUID REFERENCES retention_policies(id) ON DELETE SET NULL,
    -- When the clock started for a document that wasn't archived, so editing a policy
    -- later can't quietly restart it
    ADD COLUMN retention_clock_at      TIMESTAMP WITH TIME ZONE,
    ADD COLUMN expiring_notified_at    TIMESTAMP WITH TIME ZONE,
    ADD COLUMN expiry_handled_at       TIMESTAMP WITH TIME ZONE,
    ADD COLUMN legal_hold              BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN archived_at             TIMESTAMP WITH TIME ZONE,
    ADD COLUMN archived_from_folder_id UUID REFERENCES folders(id) ON DELETE SET NULL,
    ADD COLUMN deleted_at              TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deleted_by              UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN original_folder_id      UUID REFERENCES folders(id) ON DELETE SET NULL;

-- A deleted document lives in the Trash tray. Its folder can then be deleted without taking
-- it along, and the tray's visibility decides who can reach it.
-- deleted_at starts today: using the old dates would purge them on the job's first run.
UPDATE documents d
SET original_folder_id = d.folder_id,
    deleted_at = now(),
    folder_id = trash.id
FROM folders trash
WHERE d.status = 'SOFT_DELETED'
  AND trash.tenant_id = d.tenant_id AND trash.is_tray AND trash.is_default AND trash.name = 'Trash';

-- The Trash tray only shows deleted documents from now on, so anything filed there is treated as deleted.
UPDATE documents d
SET status = 'SOFT_DELETED', deleted_at = now()
FROM folders f
JOIN folders tray ON tray.id = COALESCE(f.root_tray_id, f.id)
WHERE d.folder_id = f.id AND d.status = 'ACTIVE' AND tray.is_tray AND tray.is_default AND tray.name = 'Trash';

CREATE INDEX idx_documents_retain_until ON documents (retain_until)
    WHERE status = 'ACTIVE' AND retain_until IS NOT NULL;
CREATE INDEX idx_documents_trash ON documents (tenant_id, deleted_at DESC) WHERE status = 'SOFT_DELETED';

-- The last line of defence. Services check first and give a useful error; this catches
-- anything else, including the ON DELETE CASCADE from folders and tenants.
CREATE FUNCTION documents_retention_guard() RETURNS trigger AS $$
BEGIN
    IF OLD.legal_hold OR OLD.retain_until > now() THEN
        RAISE EXCEPTION 'Document % is under retention or legal hold', OLD.id;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER documents_retention_no_delete
    BEFORE DELETE ON documents
    FOR EACH ROW EXECUTE FUNCTION documents_retention_guard();

CREATE TABLE legal_holds (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(255)  NOT NULL,
    reason      VARCHAR(2000) NOT NULL,
    created_by  UUID          REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    released_by UUID          REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_legal_holds_tenant ON legal_holds (tenant_id, created_at DESC);

CREATE TABLE legal_hold_documents (
    legal_hold_id UUID NOT NULL REFERENCES legal_holds(id) ON DELETE CASCADE,
    document_id   UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    added_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (legal_hold_id, document_id)
);

CREATE INDEX idx_legal_hold_documents_document ON legal_hold_documents (document_id);

CREATE TABLE notifications (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         REFERENCES tenants(id) ON DELETE CASCADE,
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type           VARCHAR(40)  NOT NULL,
    title          VARCHAR(255) NOT NULL,
    body           VARCHAR(1000),
    link           VARCHAR(512),
    in_app         BOOLEAN      NOT NULL DEFAULT true,
    digest_pending BOOLEAN      NOT NULL DEFAULT false,
    read_at        TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC) WHERE in_app;
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE in_app AND read_at IS NULL;
CREATE INDEX idx_notifications_digest ON notifications (user_id) WHERE digest_pending;
CREATE INDEX idx_notifications_created ON notifications (created_at);

CREATE TABLE notification_preferences (
    user_id      UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    in_app       BOOLEAN NOT NULL DEFAULT true,
    email        BOOLEAN NOT NULL DEFAULT true,
    daily_digest BOOLEAN NOT NULL DEFAULT false
);

-- A lease, so a daily job runs on one pod per schedule without holding a connection open.
CREATE TABLE job_locks (
    name         VARCHAR(64) PRIMARY KEY,
    locked_until TIMESTAMP WITH TIME ZONE NOT NULL
);
