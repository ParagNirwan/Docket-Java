package com.docket.app.security;

import com.docket.app.entity.ServiceAccount;
import com.docket.app.service.JwtService;
import com.docket.app.service.ServiceAccountService;
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
import io.jsonwebtoken.JwtException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ServiceAccountAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ServiceAccountService serviceAccountService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String clientId = jwtService.extractClientId(jwt);
                if (clientId != null) {
                    ServiceAccount serviceAccount = serviceAccountService.findByClientId(clientId);

                    if (serviceAccount != null && jwtService.isServiceAccountTokenValid(jwt, serviceAccount)) {
                        // Create authorities from scopes
                        List<SimpleGrantedAuthority> authorities = serviceAccount.getScopes().stream()
                                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.toUpperCase()))
                                .toList();

                        // Add tenant ID as a claim for authorization
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        serviceAccount,
                                        null,
                                        authorities
                                );

                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}