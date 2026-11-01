package com.docket.app.service;

import com.docket.app.entity.Document;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.LockMode;
import com.docket.app.enums.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RetentionGuardTest {
    private static final RetentionGuard.Override WITH_REASON = new RetentionGuard.Override(true, "Filed in error");

    private final Authentication owner = new UsernamePasswordAuthenticationToken("ada@example.com", "x", List.of());

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private RetentionGuard guard;

    private Document document;

    @BeforeEach
    void setUp() {
        document = new Document();
        document.setId(UUID.randomUUID());
        document.setTenantId(UUID.randomUUID());
        document.setOriginalFileName("a.pdf");
    }

    private void lockedAs(LockMode mode) {
        document.setRetainUntil(Instant.now().plus(Duration.ofDays(10)));
        document.setLockMode(mode);
    }

    @ParameterizedTest
    @EnumSource(RetentionGuard.Action.class)
    void unlockedDocument_allowsEverything(RetentionGuard.Action action) {
        assertFalse(guard.assertMutable(document, action, RetentionGuard.Override.NONE, owner));
    }

    @ParameterizedTest
    @EnumSource(RetentionGuard.Action.class)
    void expiredLock_allowsEverything(RetentionGuard.Action action) {
        document.setRetainUntil(Instant.now().minus(Duration.ofDays(1)));
        document.setLockMode(LockMode.COMPLIANCE);

        assertFalse(guard.assertMutable(document, action, RetentionGuard.Override.NONE, owner));
    }

    @ParameterizedTest
    @EnumSource(RetentionGuard.Action.class)
    void governance_blocksWithoutAnOverride(RetentionGuard.Action action) {
        lockedAs(LockMode.GOVERNANCE);

        assertThrows(DocumentLockedException.class, () -> guard.assertMutable(document, action, RetentionGuard.Override.NONE, owner));
    }

    @ParameterizedTest
    @EnumSource(RetentionGuard.Action.class)
    void governance_ownerCanOverrideWithAReasonAndItsAudited(RetentionGuard.Action action) {
        lockedAs(LockMode.GOVERNANCE);

        assertTrue(guard.assertMutable(document, action, WITH_REASON, owner));

        verify(auditService).record(eq(owner), eq(document.getTenantId()), eq(AuditAction.LOCK_OVERRIDDEN), eq("DOCUMENT"), eq(document.getId()), any());
    }

    @ParameterizedTest
    @EnumSource(RetentionGuard.Action.class)
    void governance_someoneWithoutThePermissionCantOverride(RetentionGuard.Action action) {
        lockedAs(LockMode.GOVERNANCE);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(tenantAuthorizationService).requirePermission(owner, document.getTenantId(), Permission.RETENTION_OVERRIDE);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> guard.assertMutable(document, action, WITH_REASON, owner));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        verifyNoInteractions(auditService);
    }

    @Test
    void governance_overrideNeedsAReason() {
        lockedAs(LockMode.GOVERNANCE);

        for (String reason : new String[]{null, "", "   ", "x".repeat(501)}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> guard.assertMutable(
                    document, RetentionGuard.Action.DELETE, new RetentionGuard.Override(true, reason), owner));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
        verifyNoInteractions(auditService);
    }

    @ParameterizedTest
    @EnumSource(RetentionGuard.Action.class)
    void compliance_nobodyCanOverride(RetentionGuard.Action action) {
        lockedAs(LockMode.COMPLIANCE);

        assertThrows(DocumentLockedException.class, () -> guard.assertMutable(document, action, WITH_REASON, owner));

        verify(tenantAuthorizationService, never()).requirePermission(any(), any(), any());
        verifyNoInteractions(auditService);
    }

    @ParameterizedTest
    @EnumSource(RetentionGuard.Action.class)
    void legalHold_beatsAnOverrideAndAnExpiredDate(RetentionGuard.Action action) {
        document.setLegalHold(true);
        document.setLockMode(LockMode.GOVERNANCE);
        document.setRetainUntil(Instant.now().minus(Duration.ofDays(1)));

        DocumentLockedException e = assertThrows(DocumentLockedException.class, () -> guard.assertMutable(document, action, WITH_REASON, owner));

        assertEquals(HttpStatus.LOCKED, e.getStatusCode());
        assertTrue(e.isLegalHold());
        verifyNoInteractions(auditService);
    }

    @Test
    void archived_isReadOnlyButCanBeDeleted() {
        document.setArchivedAt(Instant.now());

        assertThrows(DocumentLockedException.class, () -> guard.assertMutable(document, RetentionGuard.Action.RENAME, RetentionGuard.Override.NONE, owner));
        assertThrows(DocumentLockedException.class, () -> guard.assertMutable(document, RetentionGuard.Action.MOVE, RetentionGuard.Override.NONE, owner));
        assertFalse(guard.assertMutable(document, RetentionGuard.Action.DELETE, RetentionGuard.Override.NONE, owner));
    }
}
