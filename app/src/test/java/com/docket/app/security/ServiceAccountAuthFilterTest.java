package com.docket.app.security;

import com.docket.app.entity.ServiceAccount;
import com.docket.app.service.JwtService;
import com.docket.app.service.ServiceAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceAccountAuthFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private ServiceAccountService serviceAccountService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private ServiceAccountAuthFilter filter;
    private ServiceAccount serviceAccount;
    private String validSaToken = "valid.sa.token";
    private String invalidToken = "invalid.token.here";
    private String userToken = "user.token.here";

    @BeforeEach
    void setUp() {
        filter = new ServiceAccountAuthFilter(jwtService, serviceAccountService);
        SecurityContextHolder.clearContext();

        serviceAccount = new ServiceAccount();
        serviceAccount.setId(UUID.randomUUID());
        serviceAccount.setTenantId(UUID.randomUUID());
        serviceAccount.setClientId("dkt_sa_test123");
        serviceAccount.setClientSecretHash("hashed");
        serviceAccount.setScopes(List.of("documents.read"));
        serviceAccount.setCreatedAt(java.time.Instant.now());
    }

    @Test
    void noAuthHeader_continuesFilterChain() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, serviceAccountService);
    }

    @Test
    void nonBearerToken_continuesFilterChain() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, serviceAccountService);
    }

    @Test
    void validServiceAccountToken_setsAuthentication() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validSaToken);
        when(jwtService.extractClientId(validSaToken)).thenReturn(serviceAccount.getClientId());
        when(serviceAccountService.findByClientId(serviceAccount.getClientId())).thenReturn(serviceAccount);
        when(jwtService.isServiceAccountTokenValid(validSaToken, serviceAccount)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assert auth != null;
        assert auth.getPrincipal() == serviceAccount;
        // Scope "documents.read" becomes "SCOPE_DOCUMENTS.READ" after toUpperCase()
        assert auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_DOCUMENTS.READ"));
    }

    @Test
    void invalidToken_clearsContext() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);
        when(jwtService.extractClientId(invalidToken)).thenReturn("dkt_sa_test123");
        when(serviceAccountService.findByClientId("dkt_sa_test123")).thenReturn(serviceAccount);
        when(jwtService.isServiceAccountTokenValid(invalidToken, serviceAccount)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assert SecurityContextHolder.getContext().getAuthentication() == null;
    }

    @Test
    void userTokenRejectedAsServiceAccount() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + userToken);
        when(jwtService.extractClientId(userToken)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assert SecurityContextHolder.getContext().getAuthentication() == null;
    }
}