package com.docket.app.enums;

// Everything the audit log records. Later features add their own actions here
// when they ship (restore, purge, invite, legal hold, share links and so on).
public enum AuditAction {
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    ACCOUNT_LOCKED,
    SESSION_REVOKED,
    REFRESH_TOKEN_REUSED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET,
    EMAIL_VERIFIED,
    DOCUMENT_UPLOADED,
    DOCUMENT_VIEWED,
    DOCUMENT_DOWNLOADED,
    DOCUMENT_DELETED,
    TRAY_RENAMED,
    OWNERSHIP_TRANSFERRED,
    AUDIT_LOG_EXPORTED
}
