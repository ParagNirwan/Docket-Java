package com.docket.app.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

// The events a webhook endpoint can subscribe to, and which audit actions raise them.
// Keep in step with WEBHOOK_EVENTS in the frontend's services/developerService.ts.
@Getter
public enum WebhookEvent {
    DOCUMENT_UPLOADED("document.uploaded", Set.of(AuditAction.DOCUMENT_UPLOADED, AuditAction.DOCUMENT_VERSION_UPLOADED)),
    DOCUMENT_UPDATED("document.updated", Set.of(AuditAction.DOCUMENT_RENAMED, AuditAction.DOCUMENT_MOVED,
            AuditAction.DOCUMENT_METADATA_UPDATED, AuditAction.DOCUMENT_TAGGED, AuditAction.DOCUMENT_UNTAGGED,
            AuditAction.DOCUMENT_VERSION_RESTORED)),
    DOCUMENT_DELETED("document.deleted", Set.of(AuditAction.DOCUMENT_DELETED, AuditAction.DOCUMENT_PURGED,
            AuditAction.DOCUMENT_AUTO_DELETED)),
    DOCUMENT_RESTORED("document.restored", Set.of(AuditAction.DOCUMENT_RESTORED, AuditAction.DOCUMENT_UNARCHIVED)),
    MEMBER_JOINED("member.joined", Set.of(AuditAction.MEMBER_JOINED_BY_DOMAIN)),
    MEMBER_REMOVED("member.removed", Set.of(AuditAction.MEMBER_REMOVED, AuditAction.MEMBER_LEFT)),
    MEMBER_ROLE_CHANGED("member.role_changed", Set.of(AuditAction.MEMBER_ROLE_CHANGED));

    private final String code;
    private final Set<AuditAction> actions;

    WebhookEvent(String code, Set<AuditAction> actions) {
        this.code = code;
        this.actions = actions;
    }

    public static Optional<WebhookEvent> forAction(AuditAction action) {
        return Arrays.stream(values()).filter(event -> event.actions.contains(action)).findFirst();
    }

    public static List<String> normalize(Collection<String> events) {
        if (events == null || events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one event");
        }
        List<String> allowed = Arrays.stream(values()).map(WebhookEvent::getCode).toList();
        List<String> normalized = events.stream()
                .map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        for (String event : normalized) {
            if (!allowed.contains(event)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown event: " + event + ". Allowed: " + allowed);
            }
        }
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one event");
        }
        return normalized;
    }
}
