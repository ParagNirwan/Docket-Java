package com.docket.app.service;

import com.docket.app.entity.ApiKey;
import com.docket.app.entity.ServiceAccount;
import com.docket.app.enums.ApiScope;
import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import com.docket.app.enums.TenantStatus;
import com.docket.app.repository.TenantMembershipRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// API keys and service accounts have no membership: tenant + scopes decide everything.
@ExtendWith(MockitoExtension.class)
class TenantAuthorizationMachinePrincipalTest {
    @Mock private UserRepository userRepository;
    @Mock private TenantMembershipRepository tenantMembershipRepository;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks
    private TenantAuthorizationService service;

    private final UUID tenantId = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken keyAuth(UUID keyTenant, List<String> scopes) {
        ApiKey key = new ApiKey();
        key.setTenantId(keyTenant);
        key.setScopes(scopes);
        return new UsernamePasswordAuthenticationToken(key, null, List.of());
    }

    @Test
    void apiKeyWithReadScopeCanReadDocumentsInItsTenant() {
        when(tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)).thenReturn(true);

        service.requirePermission(keyAuth(tenantId, List.of("documents:read")), tenantId, Permission.DOCUMENTS_READ);

        verifyNoInteractions(userRepository, tenantMembershipRepository);
    }

    @Test
    void apiKeyWithoutTheScopeIsForbidden() {
        when(tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.requirePermission(keyAuth(tenantId, List.of("documents:read")), tenantId, Permission.DOCUMENTS_DELETE));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void apiKeyCannotReachAnotherTenant() {
        UUID otherTenant = UUID.randomUUID();

        assertThrows(ResponseStatusException.class, () ->
                service.requirePermission(keyAuth(tenantId, List.of("documents:read")), otherTenant, Permission.DOCUMENTS_READ));
        assertThrows(ResponseStatusException.class, () ->
                service.getMembershipRole(keyAuth(tenantId, List.of("documents:read")), otherTenant));
    }

    @Test
    void serviceAccountsGetMemberVisibilityNotAdmin() {
        when(tenantRepository.existsByIdAndStatus(tenantId, TenantStatus.ACTIVE)).thenReturn(true);
        ServiceAccount account = new ServiceAccount();
        account.setTenantId(tenantId);
        account.setScopes(List.of("documents:read"));

        Roles role = service.getMembershipRole(new UsernamePasswordAuthenticationToken(account, null, List.of()), tenantId);

        assertEquals(Roles.MEMBER, role);
    }

    @Test
    void scopeNormalizationRejectsUnknownScopes() {
        assertEquals(List.of("documents:read"), ApiScope.normalize(List.of(" Documents:Read ", "documents:read")));
        assertThrows(ResponseStatusException.class, () -> ApiScope.normalize(List.of("admin")));
        assertTrue(ApiScope.grants(List.of("documents:write"), Permission.DOCUMENTS_CREATE));
        assertFalse(ApiScope.grants(List.of("documents:write"), Permission.DOCUMENTS_DELETE));
    }
}
