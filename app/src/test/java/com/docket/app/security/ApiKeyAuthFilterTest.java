package com.docket.app.security;

import com.docket.app.entity.ApiKey;
import com.docket.app.service.ApiKeyService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private ApiKeyService apiKeyService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthFilter filter;
    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(apiKeyService);
        SecurityContextHolder.clearContext();

        apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID());
        apiKey.setTenantId(UUID.randomUUID());
        apiKey.setKeyHash("hashed");
        apiKey.setScopes(List.of("documents_read"));
    }

    @Test
    void noApiKeyHeader_continuesFilterChain() throws ServletException, IOException {
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void validXApiKey_setsAuthentication() throws ServletException, IOException {
        String rawKey = "dkt_live_test123_test456";
        when(request.getHeader("X-API-Key")).thenReturn(rawKey);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(apiKeyService.findValidKey(rawKey, null)).thenReturn(apiKey);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assert auth != null;
        assert auth.getPrincipal() == apiKey;
        assert auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_DOCUMENTS_READ"));
    }

    @Test
    void validBearerApiKey_setsAuthentication() throws ServletException, IOException {
        String rawKey = "dkt_live_test123_test456";
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + rawKey);
        when(apiKeyService.findValidKey(rawKey, null)).thenReturn(apiKey);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assert auth != null;
        assert auth.getPrincipal() == apiKey;
    }

    @Test
    void invalidApiKey_clearsContext() throws ServletException, IOException {
        String rawKey = "invalid_key";
        when(request.getHeader("X-API-Key")).thenReturn(rawKey);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(apiKeyService.findValidKey(rawKey, null)).thenThrow(new RuntimeException("Invalid"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assert SecurityContextHolder.getContext().getAuthentication() == null;
    }

    @Test
    void nonApiKeyBearerToken_continuesFilterChain() throws ServletException, IOException {
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJIUzI1NiJ9...");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(apiKeyService);
    }
}