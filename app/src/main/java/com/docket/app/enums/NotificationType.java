package com.docket.app.enums;

// Sharing (#71) and quota warnings (#74) add their own types when they ship.
public enum NotificationType {
    INVITED,
    MEMBER_JOINED,
    ROLE_CHANGED,
    INDEXING_FAILED,
    RETENTION_EXPIRING,
    RETENTION_EXPIRED,
    LEGAL_HOLD_APPLIED,
    LEGAL_HOLD_RELEASED
}
