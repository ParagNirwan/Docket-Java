package com.docket.app.controller;

import com.docket.app.dto.AiFeedbackRequestDTO;
import com.docket.app.dto.ConversationDTO;
import com.docket.app.dto.ConversationMessageDTO;
import com.docket.app.dto.CreateConversationRequestDTO;
import com.docket.app.dto.SavedAnswerDTO;
import com.docket.app.dto.SendMessageRequestDTO;
import com.docket.app.dto.UpdateConversationVisibilityRequestDTO;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.Permission;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.service.ConversationService;
import com.docket.app.service.TenantAuthorizationService;
import com.docket.app.service.AuditService;
import com.docket.app.service.ai.AiRateLimiter;
import com.docket.app.service.ai.RateLimitExceededException;
import com.docket.app.enums.AuditAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}/ai/conversations")
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("gcp")
public class ConversationController {

    private final ConversationService conversationService;
    private final AiRateLimiter aiRateLimiter;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.docket.app.service.billing.PlanLimitService planLimitService;
    private final AuditService auditService;

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

    @GetMapping("/shared")
    public ResponseEntity<List<ConversationDTO>> listShared(
            @PathVariable String slug,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);

        return ResponseEntity.ok(
                conversationService.listSharedConversations(authentication, tenant.getId()));
    }

    @GetMapping("/saved-answers")
    public ResponseEntity<List<SavedAnswerDTO>> listSavedAnswers(
            @PathVariable String slug,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        return ResponseEntity.ok(
                conversationService.listSavedAnswers(authentication, tenant.getId(), user.getId()));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDTO> get(
            @PathVariable String slug,
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        return ResponseEntity.ok(
                conversationService.getConversation(authentication, conversationId, tenant.getId(), user.getId()));
    }

    @PatchMapping("/{conversationId}/visibility")
    public ResponseEntity<ConversationDTO> updateVisibility(
            @PathVariable String slug,
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationVisibilityRequestDTO request,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        return ResponseEntity.ok(
                conversationService.updateVisibility(conversationId, tenant.getId(), user.getId(), request.getVisibility()));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/pin")
    public ResponseEntity<ConversationMessageDTO> pinMessage(
            @PathVariable String slug,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        return ResponseEntity.ok(
                conversationService.pinMessage(authentication, conversationId, messageId, tenant.getId(), user.getId()));
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}/pin")
    public ResponseEntity<ConversationMessageDTO> unpinMessage(
            @PathVariable String slug,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        return ResponseEntity.ok(
                conversationService.unpinMessage(authentication, conversationId, messageId, tenant.getId(), user.getId()));
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
        requireRateLimit(user, tenant, authentication);

        ConversationDTO conversation = conversationService.createConversation(
                authentication, tenant.getId(), user.getId(), request.getQuestion(), request.getMode(),
                request.getScope(), request.getVisibility(), null);

        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    // Starts a conversation and streams its first answer. The "done" event carries the
    // stored conversation, the same as the response of the endpoint above.
    @PostMapping("/stream")
    public void createStream(
            @PathVariable String slug,
            @Valid @RequestBody CreateConversationRequestDTO request,
            Authentication authentication,
            HttpServletResponse response
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);
        requireRateLimit(user, tenant, authentication);

        SseStream.run(response, objectMapper, listener -> conversationService.createConversation(
                authentication, tenant.getId(), user.getId(), request.getQuestion(), request.getMode(),
                request.getScope(), request.getVisibility(), listener));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ConversationMessageDTO> sendMessage(
            @PathVariable String slug,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequestDTO request,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);
        requireRateLimit(user, tenant, authentication);

        ConversationMessageDTO aiMessage = conversationService.sendMessage(
                authentication, conversationId, tenant.getId(), user.getId(), request.getQuestion(),
                request.getMode(), request.getScope(), null);

        return ResponseEntity.ok(aiMessage);
    }

    // Streams the answer to a follow-up. The "done" event carries the stored AI message.
    @PostMapping("/{conversationId}/messages/stream")
    public void sendMessageStream(
            @PathVariable String slug,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequestDTO request,
            Authentication authentication,
            HttpServletResponse response
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);
        requireRateLimit(user, tenant, authentication);

        SseStream.run(response, objectMapper, listener -> conversationService.sendMessage(
                authentication, conversationId, tenant.getId(), user.getId(), request.getQuestion(),
                request.getMode(), request.getScope(), listener));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/feedback")
    public ResponseEntity<Void> addFeedback(
            @PathVariable String slug,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody AiFeedbackRequestDTO request,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);
        conversationService.addFeedback(conversationId, messageId, tenant.getId(), user.getId(),
                request.getRating(), request.getReason(), request.getComment());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {
        Tenant tenant = resolveTenant(slug);
        tenantAuthorizationService.requirePermission(
                authentication, tenant.getId(), Permission.DOCUMENTS_READ);
        User user = resolveUser(authentication);

        conversationService.deleteConversation(authentication, conversationId, tenant.getId(), user.getId());
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

    private void requireRateLimit(User user, Tenant tenant, Authentication authentication) {
        try {
            aiRateLimiter.checkAllowed(user.getId().toString(), tenant.getId());
        } catch (RateLimitExceededException e) {
            auditService.record(authentication, tenant.getId(), AuditAction.AI_QUERY_RATE_LIMITED,
                    "ai_query", null, Map.of("retry_after_seconds", e.retryAfterSeconds()));
            ResponseStatusException ex = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Maximum 10 queries per minute. Please wait and try again.", e);
            ex.getHeaders().add("Retry-After", String.valueOf(e.retryAfterSeconds()));
            throw ex;
        }
        // Monthly plan allowance (no-op when billing is off). 402 when used up.
        planLimitService.countAiQuery(tenant.getId());
    }
}
