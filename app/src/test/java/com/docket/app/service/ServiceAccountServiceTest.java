package com.docket.app.service;

import com.docket.app.dto.CreateServiceAccountRequestDTO;
import com.docket.app.entity.ServiceAccount;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.Permission;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.ServiceAccountRepository;
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
class ServiceAccountServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ServiceAccountRepository serviceAccountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantAuthorizationService tenantAuthorizationService;
    @Mock
    private AuditService auditService;

    private ServiceAccountService serviceAccountService;

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

        serviceAccountService = new ServiceAccountService(
                tenantRepository,
                serviceAccountRepository,
                userRepository,
                tenantAuthorizationService,
                auditService
        );
    }

    @Test
    void findValidAccount_updatesLastUsedAt() {
        String clientId = "dkt_sa_test123";
        String clientSecret = "dkt_sa_secret123";
        String hashedSecret = serviceAccountService.hash(clientSecret);

        ServiceAccount account = new ServiceAccount();
        account.setId(UUID.randomUUID());
        account.setTenantId(tenantId);
        account.setClientId(clientId);
        account.setClientSecretHash(hashedSecret);
        account.setScopes(List.of("documents.read"));
        account.setCreatedAt(Instant.now().minusSeconds(3600));
        account.setLastUsedAt(null);

        when(serviceAccountRepository.findByClientId(clientId)).thenReturn(Optional.of(account));
        when(serviceAccountRepository.save(any(ServiceAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceAccount result = serviceAccountService.findValidAccount(clientId, clientSecret, tenantId);

        assertThat(result.getLastUsedAt()).isNotNull();
        verify(serviceAccountRepository).save(argThat(saved ->
                saved.getLastUsedAt() != null &&
                saved.getLastUsedAt().isAfter(account.getCreatedAt())
        ));
    }

    @Test
    void findValidAccount_throwsWhenRevoked() {
        String clientId = "dkt_sa_test123";
        String clientSecret = "dkt_sa_secret123";
        String hashedSecret = serviceAccountService.hash(clientSecret);

        ServiceAccount account = new ServiceAccount();
        account.setClientId(clientId);
        account.setClientSecretHash(hashedSecret);
        account.setRevokedAt(Instant.now());

        when(serviceAccountRepository.findByClientId(clientId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> serviceAccountService.findValidAccount(clientId, clientSecret, tenantId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer valid");
    }

    @Test
    void findValidAccount_throwsWhenExpired() {
        String clientId = "dkt_sa_test123";
        String clientSecret = "dkt_sa_secret123";
        String hashedSecret = serviceAccountService.hash(clientSecret);

        ServiceAccount account = new ServiceAccount();
        account.setClientId(clientId);
        account.setClientSecretHash(hashedSecret);
        account.setExpiresAt(Instant.now().minusSeconds(3600));

        when(serviceAccountRepository.findByClientId(clientId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> serviceAccountService.findValidAccount(clientId, clientSecret, tenantId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer valid");
    }

    @Test
    void findValidAccount_throwsWhenTenantMismatch() {
        String clientId = "dkt_sa_test123";
        String clientSecret = "dkt_sa_secret123";
        String hashedSecret = serviceAccountService.hash(clientSecret);

        ServiceAccount account = new ServiceAccount();
        account.setClientId(clientId);
        account.setClientSecretHash(hashedSecret);
        account.setTenantId(UUID.randomUUID()); // Different tenant

        when(serviceAccountRepository.findByClientId(clientId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> serviceAccountService.findValidAccount(clientId, clientSecret, tenantId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match tenant");
    }
}