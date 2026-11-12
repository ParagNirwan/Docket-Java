package com.docket.app.controller;

import com.docket.app.dto.AcceptedInvitationDTO;
import com.docket.app.dto.CreateInviteLinkRequestDTO;
import com.docket.app.dto.InviteLinkDTO;
import com.docket.app.dto.InviteLinkPreviewDTO;
import com.docket.app.dto.InviteLinkSettingsDTO;
import com.docket.app.security.ClientRequestInfo;
import com.docket.app.service.AuthRateLimiter;
import com.docket.app.service.InviteLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

// Managing an organization's invite links, and the public side of opening one.
@RestController
@RequiredArgsConstructor
public class InviteLinkController {
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final InviteLinkService inviteLinkService;
    private final AuthRateLimiter authRateLimiter;
    private final ClientRequestInfo clientRequestInfo;

    @PostMapping("/api/tenants/{slug}/invite-links")
    public ResponseEntity<InviteLinkDTO> create(
            @PathVariable String slug,
            @Valid @RequestBody CreateInviteLinkRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteLinkService.create(slug, request, authentication));
    }

    @GetMapping("/api/tenants/{slug}/invite-links")
    public ResponseEntity<List<InviteLinkDTO>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(inviteLinkService.list(slug, authentication));
    }

    @DeleteMapping("/api/tenants/{slug}/invite-links/{linkId}")
    public ResponseEntity<Void> revoke(@PathVariable String slug, @PathVariable UUID linkId, Authentication authentication) {
        inviteLinkService.revoke(slug, linkId, authentication);
        return ResponseEntity.noContent().build();
    }

    // PUT /api/tenants/{slug}/invite-links/settings
    // Switches invite links on or off for the whole organization.
    @PutMapping("/api/tenants/{slug}/invite-links/settings")
    public ResponseEntity<InviteLinkSettingsDTO> updateSettings(
            @PathVariable String slug,
            @RequestBody InviteLinkSettingsDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inviteLinkService.updateSettings(slug, request, authentication));
    }

    // GET /api/invite-links/{token}
    // Public, so that people without an account can see what they're joining.
    @GetMapping("/api/invite-links/{token}")
    public ResponseEntity<InviteLinkPreviewDTO> preview(@PathVariable String token) {
        limitByIp("invite-link-preview", 30);
        return ResponseEntity.ok(inviteLinkService.preview(token));
    }

    // POST /api/invite-links/{token}/join
    @PostMapping("/api/invite-links/{token}/join")
    public ResponseEntity<AcceptedInvitationDTO> join(@PathVariable String token, Authentication authentication) {
        limitByIp("invite-link-join", 20);
        return ResponseEntity.ok(inviteLinkService.join(token, authentication));
    }

    private void limitByIp(String endpoint, int limitPerMinute) {
        authRateLimiter.check(endpoint + ":ip:" + clientRequestInfo.ip(), limitPerMinute, ONE_MINUTE);
    }
}
