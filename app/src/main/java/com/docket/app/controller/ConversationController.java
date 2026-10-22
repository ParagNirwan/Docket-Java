package com.docket.app.controller;

import com.docket.app.dto.*;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.service.ConversationService;
import com.docket.app.service.TenantAuthorizationService;
import com.docket.app.service.ai.RateLimiterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{slug}/ai/conversations")
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("gcp")
public class ConversationController {

    private final ConversationService conversationService;
    private final RateLimiterService rateLimiterService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<ConversationDTO>> list(
            @PathVariable String slug,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        return ResponseEntity.ok(
                conversationService.listConversations(tenant.getId(), user.getId()));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDTO> get(
            @PathVariable String slug,
            @PathVariable java.util.UUID conversationId,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        return ResponseEntity.ok(
                conversationService.getConversation(conversationId, tenant.getId(), user.getId()));
    }

    @PostMapping
    public ResponseEntity<ConversationDTO> create(
            @PathVariable String slug,
            @Valid @RequestBody CreateConversationRequestDTO request,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);
        requireRateLimit(user);

        ConversationDTO conversation = conversationService.createConversation(
                tenant.getId(), user.getId(), request.getQuestion());

        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ConversationMessageDTO> sendMessage(
            @PathVariable String slug,
            @PathVariable java.util.UUID conversationId,
            @Valid @RequestBody SendMessageRequestDTO request,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);
        requireRateLimit(user);

        ConversationMessageDTO aiMessage = conversationService.sendMessage(
                conversationId, tenant.getId(), user.getId(), request.getQuestion());

        return ResponseEntity.ok(aiMessage);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            @PathVariable java.util.UUID conversationId,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        conversationService.deleteConversation(conversationId, tenant.getId(), user.getId());
        return ResponseEntity.noContent().build();
    }

    private Tenant resolveTenant(String slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private User resolveUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireRateLimit(User user) {
        if (!rateLimiterService.isAllowed(user.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Maximum 10 queries per minute. Please wait and try again.");
        }
    }
}
