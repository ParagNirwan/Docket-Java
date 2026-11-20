package com.docket.app.security;

import com.docket.app.entity.ApiKey;
import com.docket.app.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Check for API key in header
        String apiKeyHeader = request.getHeader("X-API-Key");

        // Also check Authorization header for Bearer token (API key format)
        String authHeader = request.getHeader("Authorization");
        String apiKeyFromAuth = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Check if it looks like an API key (starts with dkt_live_)
            if (token.startsWith("dkt_live_")) {
                apiKeyFromAuth = token;
            }
        }

        String rawKey = apiKeyHeader != null ? apiKeyHeader : apiKeyFromAuth;

        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                ApiKey apiKey = apiKeyService.findValidKey(rawKey, null);

                if (apiKey != null) {
                    // Create authorities from scopes
                    List<SimpleGrantedAuthority> authorities = apiKey.getScopes().stream()
                            .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.toUpperCase()))
                            .toList();

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    apiKey,
                                    null,
                                    authorities
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}