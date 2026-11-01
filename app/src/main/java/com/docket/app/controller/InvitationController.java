package com.docket.app.controller;

import com.docket.app.dto.AcceptedInvitationDTO;
import com.docket.app.dto.InvitationDTO;
import com.docket.app.dto.InvitationPreviewDTO;
import com.docket.app.dto.InviteMemberRequestDTO;
import com.docket.app.security.ClientRequestInfo;
import com.docket.app.service.AuthRateLimiter;
import com.docket.app.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

// Managing an organization's invites, and the public side of opening an invite link.
@RestController
@RequiredArgsConstructor
public class InvitationController {
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final InvitationService invitationService;
    private final AuthRateLimiter authRateLimiter;
    private final ClientRequestInfo clientRequestInfo;

    @PostMapping("/api/tenants/{slug}/invitations")
    public ResponseEntity<InvitationDTO> invite(
            @PathVariable String slug,
            @Valid @RequestBody InviteMemberRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.invite(slug, request, authentication));
    }

    @GetMapping("/api/tenants/{slug}/invitations")
    public ResponseEntity<List<InvitationDTO>> listPending(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(invitationService.listPending(slug, authentication));
    }

    @PostMapping("/api/tenants/{slug}/invitations/{invitationId}/resend")
    public ResponseEntity<InvitationDTO> resend(
            @PathVariable String slug,
            @PathVariable UUID invitationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(invitationService.resend(slug, invitationId, authentication));
    }

    @DeleteMapping("/api/tenants/{slug}/invitations/{invitationId}")
    public ResponseEntity<Void> revoke(
            @PathVariable String slug,
            @PathVariable UUID invitationId,
            Authentication authentication
    ) {
        invitationService.revoke(slug, invitationId, authentication);
        return ResponseEntity.noContent().build();
    }

    // GET /api/invitations/{token}
    // Public, so that people without an account can see what they've been invited to.
    @GetMapping("/api/invitations/{token}")
    public ResponseEntity<InvitationPreviewDTO> preview(@PathVariable String token) {
        limitByIp("invite-preview", 30);
        return ResponseEntity.ok(invitationService.preview(token));
    }

    // POST /api/invitations/{token}/accept
    @PostMapping("/api/invitations/{token}/accept")
    public ResponseEntity<AcceptedInvitationDTO> accept(@PathVariable String token, Authentication authentication) {
        limitByIp("invite-accept", 20);
        return ResponseEntity.ok(invitationService.accept(token, authentication));
    }

    private void limitByIp(String endpoint, int limitPerMinute) {
        authRateLimiter.check(endpoint + ":ip:" + clientRequestInfo.ip(), limitPerMinute, ONE_MINUTE);
    }
}
