package com.docket.app.service;

import com.docket.app.dto.TenantUsageDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.TenantUsage;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.NotificationType;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.TenantUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {
    private static final String SLUG = "acme";
    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantUsageRepository tenantUsageRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private StorageQuotaService storageQuotaService;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug(SLUG);
    }

    @Test
    void assertQuota_passesWhenNoQuotaSet() {
        tenant.setStorageQuotaBytes(null);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertDoesNotThrow(() -> storageQuotaService.assertQuota(tenantId, 1024 * 1024));
        verifyNoInteractions(tenantUsageRepository);
    }

    @Test
    void assertQuota_throwsWhenExceededAndSendsNotification() {
        tenant.setStorageQuotaBytes(100L * 1024 * 1024); // 100 MB
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantUsageRepository.findByTenantId(tenantId)).thenReturn(Optional.of(
                new TenantUsage(tenantId, 95L * 1024 * 1024, 10L, Instant.now())
        ));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storageQuotaService.assertQuota(tenantId, 10L * 1024 * 1024)); // total 105 MB > 100 MB

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatusCode());
        verify(notificationService).notifyPermissionHolders(eq(tenantId), eq(Permission.TENANT_UPDATE),
                eq(NotificationType.STORAGE_QUOTA_EXCEEDED), any(), any(), any());
    }

    @Test
    void assertQuota_warnsAt80Percent() {
        tenant.setStorageQuotaBytes(100L * 1024 * 1024);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantUsageRepository.findByTenantId(tenantId)).thenReturn(Optional.of(
                new TenantUsage(tenantId, 70L * 1024 * 1024, 10L, Instant.now())
        ));

        assertDoesNotThrow(() -> storageQuotaService.assertQuota(tenantId, 15L * 1024 * 1024)); // 70MB -> 85MB (crosses 80%)

        verify(notificationService).notifyPermissionHolders(eq(tenantId), eq(Permission.TENANT_UPDATE),
                eq(NotificationType.STORAGE_QUOTA_WARNING), any(), any(), any());
    }

    @Test
    void recordUsage_adjustsExistingOrCreatesNew() {
        when(tenantUsageRepository.adjustUsage(tenantId, 1024L, 1L)).thenReturn(1);

        storageQuotaService.recordUsage(tenantId, 1024L, 1L);

        verify(tenantUsageRepository).adjustUsage(tenantId, 1024L, 1L);
        verify(tenantUsageRepository, never()).save(any());
    }

    @Test
    void updateQuota_updatesAndAudits() {
        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        when(tenantUsageRepository.findByTenantId(tenantId)).thenReturn(Optional.of(
                new TenantUsage(tenantId, 1000L, 5L, Instant.now())
        ));

        TenantUsageDTO result = storageQuotaService.updateQuota(SLUG, 500L * 1024 * 1024, authentication);

        assertEquals(500L * 1024 * 1024, result.storageQuotaBytes());
        verify(tenantRepository).save(tenant);
        verify(auditService).record(eq(authentication), eq(tenantId), eq(AuditAction.STORAGE_QUOTA_UPDATED), eq("TENANT"), eq(tenantId), any());
    }
}
