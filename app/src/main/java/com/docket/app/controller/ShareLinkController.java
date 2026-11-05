package com.docket.app.controller;

import com.docket.app.dto.CreateShareLinkRequestDTO;
import com.docket.app.dto.ShareLinkDTO;
import com.docket.app.service.ShareLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}/documents/{docId}/shares")
@RequiredArgsConstructor
public class ShareLinkController {
    private final ShareLinkService shareLinkService;

    @PostMapping
    public ResponseEntity<ShareLinkDTO> create(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @RequestBody CreateShareLinkRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shareLinkService.createShareLink(slug, docId, request, authentication));
    }

    @GetMapping
    public List<ShareLinkDTO> list(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        return shareLinkService.listShareLinks(slug, docId, authentication);
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revoke(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @PathVariable UUID shareId,
            Authentication authentication
    ) {
        shareLinkService.revokeShareLink(slug, docId, shareId, authentication);
        return ResponseEntity.noContent().build();
    }
}

