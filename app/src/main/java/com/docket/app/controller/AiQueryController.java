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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class AiQueryController {

    private final AiQueryService aiQueryService;
    private final RateLimiterService rateLimiterService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @PostMapping("/ai/query")
    public ResponseEntity<AiQueryResponseDTO> query(
            @PathVariable String slug,
            @Valid @RequestBody AiQueryRequestDTO request,
            Authentication authentication
    ) {
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

        AiQueryResponseDTO response = aiQueryService.query(
                tenant.getId(), request.getQuestion(), request.getDocumentId());

        return ResponseEntity.ok(response);
    }
}
