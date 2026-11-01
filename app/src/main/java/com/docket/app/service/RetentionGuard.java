package com.docket.app.service;

import com.docket.app.entity.Document;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

// The one check every destructive path goes through. Replacing a document's content
// (#64) must call this too. In order of strength:
//   Legal hold: nobody, ever, until it's released.
//   Compliance: nobody until retainUntil.
//   Governance: an Owner, with a reason that goes in the audit log.
//   Archived:   read-only, but can be deleted once nothing else protects it.
@Service
@RequiredArgsConstructor
public class RetentionGuard {
    public enum Action { DELETE, PURGE, MOVE, RENAME }

    public record Override(boolean requested, String reason) {
        public static final Override NONE = new Override(false, null);
    }

    private static final Set<Action> BLOCKED_WHEN_ARCHIVED = Set.of(Action.MOVE, Action.RENAME);
    private static final int MAX_REASON_LENGTH = 500;

    private final TenantAuthorizationService tenantAuthorizationService;
    private final AuditService auditService;

    // Returns true if this went ahead on an override, so callers moving or deleting
    // the document can drop the lock rather than leave it pointing at nothing.
    public boolean assertMutable(Document document, Action action, Override override, Authentication authentication) {
        if (document.isLegalHold()) {
            throw DocumentLockedException.of(document, "This document is under legal hold");
        }
        if (document.getArchivedAt() != null && BLOCKED_WHEN_ARCHIVED.contains(action)) {
            throw DocumentLockedException.of(document, "This document is archived. Unarchive it first");
        }
        if (!document.isRetentionLocked(Instant.now())) {
            return false;
        }
        if (document.getLockMode() != LockMode.GOVERNANCE) {
            throw DocumentLockedException.of(document, "This document is locked until its retention period ends");
        }
        if (!override.requested()) {
            throw DocumentLockedException.of(document, "This document is locked. An Owner can override this with a reason");
        }

        tenantAuthorizationService.requirePermission(authentication, document.getTenantId(), Permission.RETENTION_OVERRIDE);
        String reason = override.reason() == null ? "" : override.reason().trim();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason of up to 500 characters is required");
        }
        auditService.record(authentication, document.getTenantId(), AuditAction.LOCK_OVERRIDDEN, "DOCUMENT", document.getId(),
                Map.of("action", action.name(), "reason", reason, "retainUntil", document.getRetainUntil().toString(),
                        "fileName", String.valueOf(document.getOriginalFileName())));
        return true;
    }
}
