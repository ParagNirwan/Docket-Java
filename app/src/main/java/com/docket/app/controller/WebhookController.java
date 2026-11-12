package com.docket.app.controller;

import com.docket.app.dto.CreateWebhookRequestDTO;
import com.docket.app.dto.WebhookEndpointDTO;
import com.docket.app.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookService webhookService;

    @GetMapping("/webhooks")
    public ResponseEntity<List<WebhookEndpointDTO>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(webhookService.list(slug, authentication));
    }

    @PostMapping("/webhooks")
    public ResponseEntity<WebhookEndpointDTO> create(
            @PathVariable String slug,
            @Valid @RequestBody CreateWebhookRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(webhookService.create(slug, request, authentication));
    }

    @PatchMapping("/webhooks/{endpointId}")
    public ResponseEntity<WebhookEndpointDTO> update(
            @PathVariable String slug,
            @PathVariable UUID endpointId,
            @RequestParam boolean active,
            Authentication authentication
    ) {
        return ResponseEntity.ok(webhookService.update(slug, endpointId, active, authentication));
    }

    @DeleteMapping("/webhooks/{endpointId}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            @PathVariable UUID endpointId,
            Authentication authentication
    ) {
        webhookService.delete(slug, endpointId, authentication);
        return ResponseEntity.noContent().build();
    }
}
