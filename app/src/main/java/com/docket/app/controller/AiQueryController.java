package com.docket.app.controller;

import com.docket.app.dto.AiQueryRequestDTO;
import com.docket.app.dto.AiQueryResponseDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.service.TenantAuthorizationService;
import com.docket.app.service.ai.AiQueryService;
import com.docket.app.service.ai.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("gcp")
public class AiQueryController {

    private final AiQueryService aiQueryService;
    private final RateLimiterService rateLimiterService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/ai/query")
    public ResponseEntity<AiQueryResponseDTO> query(
            @PathVariable String slug,
            @Valid @RequestBody AiQueryRequestDTO request,
            Authentication authentication
    ) {
        Tenant tenant = authorize(slug, authentication);

        AiQueryResponseDTO response = aiQueryService.query(
                authentication, tenant.getId(), request.getQuestion(), request.getDocumentId(),
                request.getQuestion(), List.of(), request.getMode(), request.getScope());

        return ResponseEntity.ok(response);
    }

    // The same answer as /AI/query, as server-sent events while it is written. See SseStream.
    @PostMapping("/ai/query/stream")
    public void queryStream(
            @PathVariable String slug,
            @Valid @RequestBody AiQueryRequestDTO request,
            Authentication authentication,
            HttpServletResponse response
    ) {
        Tenant tenant = authorize(slug, authentication);

        SseStream.run(response, objectMapper, listener -> aiQueryService.stream(
                authentication, tenant.getId(), request.getQuestion(), request.getDocumentId(),
                request.getQuestion(), List.of(), request.getMode(), request.getScope(), listener));
    }

    private Tenant authorize(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!rateLimiterService.isAllowed(user.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Maximum 10 queries per minute. Please wait and try again.");
        }

        return tenant;
    }
}
