package com.docket.app.service;

import com.docket.app.entity.ApiKey;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.Permission;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.ApiKeyRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private AuditService auditService;

    private ApiKeyService apiKeyService;

    private Tenant tenant;
    private User user;
    private Authentication authentication;
    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setSlug("test-tenant");
        tenant.setName("Test Tenant");
        tenant.setStatus(TenantStatus.ACTIVE);

        user = new User("Test User", "test@example.com", "hashed");
        user.setId(userId);

        authentication = new UsernamePasswordAuthenticationToken("test@example.com", null, List.of());

        apiKeyService = new ApiKeyService(
                tenantRepository,
                apiKeyRepository,
                userRepository,
                tenantAuthorizationService,
                auditService
        );
    }

    @Test
    void findValidKey_updatesLastUsedAt() {
        String rawKey = "dkt_live_test123_test456";
        String hashedKey = apiKeyService.hash(rawKey);

        ApiKey key = new ApiKey();
        key.setId(UUID.randomUUID());
        key.setTenantId(tenantId);
        key.setKeyHash(hashedKey);
        key.setScopes(List.of("documents.read"));
        key.setCreatedAt(Instant.now().minusSeconds(3600));
        key.setLastUsedAt(null);

        when(apiKeyRepository.findByKeyHash(hashedKey)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKey result = apiKeyService.findValidKey(rawKey, tenantId);

        assertThat(result.getLastUsedAt()).isNotNull();
        verify(apiKeyRepository).save(argThat(saved ->
                saved.getLastUsedAt() != null &&
                saved.getLastUsedAt().isAfter(key.getCreatedAt())
        ));
    }

    @Test
    void findValidKey_throwsWhenRevoked() {
        String rawKey = "dkt_live_test123_test456";
        String hashedKey = apiKeyService.hash(rawKey);

        ApiKey key = new ApiKey();
        key.setKeyHash(hashedKey);
        key.setRevokedAt(Instant.now());

        when(apiKeyRepository.findByKeyHash(hashedKey)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> apiKeyService.findValidKey(rawKey, tenantId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer valid");
    }

    @Test
    void findValidKey_throwsWhenExpired() {
        String rawKey = "dkt_live_test123_test456";
        String hashedKey = apiKeyService.hash(rawKey);

        ApiKey key = new ApiKey();
        key.setKeyHash(hashedKey);
        key.setExpiresAt(Instant.now().minusSeconds(3600));

        when(apiKeyRepository.findByKeyHash(hashedKey)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> apiKeyService.findValidKey(rawKey, tenantId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer valid");
    }

    @Test
    void findValidKey_throwsWhenTenantMismatch() {
        String rawKey = "dkt_live_test123_test456";
        String hashedKey = apiKeyService.hash(rawKey);

        ApiKey key = new ApiKey();
        key.setKeyHash(hashedKey);
        key.setTenantId(UUID.randomUUID()); // Different tenant

        when(apiKeyRepository.findByKeyHash(hashedKey)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> apiKeyService.findValidKey(rawKey, tenantId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match tenant");
    }
}