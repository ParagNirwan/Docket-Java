-- V10: Append-only audit log (Issue #73)
--
-- tenant_id is NULL for account-level events such as logins, which don't belong
-- to one organization. There are deliberately no foreign keys: an audit record
-- must outlive the user or organization it describes.

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    actor_user_id UUID,
    actor_type VARCHAR(16) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id UUID,
    metadata JSONB,
    ip VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_events_tenant_created ON audit_events (tenant_id, created_at DESC);
CREATE INDEX idx_audit_events_tenant_target ON audit_events (tenant_id, target_id);
CREATE INDEX idx_audit_events_created ON audit_events (created_at);

-- Rows can never be changed. They can only be deleted by the retention job, which
-- must opt in for its transaction, and never when they are under 365 days old.
CREATE FUNCTION audit_events_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF current_setting('docket.audit_purge', true) = 'on'
                AND OLD.created_at < now() - INTERVAL '365 days' THEN
            RETURN OLD;
        END IF;
    END IF;
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_change
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_guard();

CREATE TRIGGER audit_events_no_truncate
    BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_events_guard();
