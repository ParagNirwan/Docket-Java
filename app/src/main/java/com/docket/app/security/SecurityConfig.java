package com.docket.app.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final ServiceAccountAuthFilter serviceAccountAuthFilter;

    @Value("${docket.cors.allowed-origins}")
    private List<String> allowedOrigins;

    /**
     * Filter chain for public API endpoints (/api/public/**).
     * Authenticates via API Key (X-API-Key header or Bearer token) or Service Account JWT.
     * This chain runs FIRST (highest priority) to handle public API requests.
     */
    @Bean
    @Order(1)
    SecurityFilterChain publicApiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/public/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(serviceAccountAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Filter chain for internal API endpoints (/api/internal/**).
     * These are pre-authorized via shared secret in ScannerCallbackController.
     * No additional authentication filters needed.
     */
    @Bean
    @Order(2)
    SecurityFilterChain internalApiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/internal/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll()) // Auth handled by controller
                .build();
    }

    /**
     * Main application filter chain for all other endpoints.
     * Authenticates via user JWT (Bearer token).
     * This chain runs LAST (lowest priority) as the catch-all.
     */
    @Bean
    @Order(3)
    SecurityFilterChain mainFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**", "/actuator/health/**", "/actuator/prometheus", "/error").permitAll()
                        // Someone opening an invite link may not have an account yet.
                        .requestMatchers(HttpMethod.GET, "/api/invitations/*").permitAll()
                        .anyRequest().authenticated())
                // Without this Spring answers 403, and the frontend needs a 401 to
                // know it should refresh its token rather than show "no permission".
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // An exact list, not a wildcard: browsers attach the refresh cookie to
        // credentialed requests, so any allowed origin could mint access tokens.
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

