package com.docket.app.controller;

import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.dto.LegalHoldDTO;
import com.docket.app.dto.LegalHoldRequestDTO;
import com.docket.app.dto.LegalHoldTargetsDTO;
import com.docket.app.service.LegalHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}/legal-holds")
@RequiredArgsConstructor
public class LegalHoldController {
    private final LegalHoldService legalHoldService;

    @GetMapping
    public ResponseEntity<List<LegalHoldDTO>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(legalHoldService.list(slug, authentication));
    }

    @PostMapping
    public ResponseEntity<LegalHoldDTO> create(
            @PathVariable String slug,
            @Valid @RequestBody LegalHoldRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(legalHoldService.create(slug, request, authentication));
    }

    @GetMapping("/{holdId}/documents")
    public ResponseEntity<Page<DocumentResponseDTO>> listDocuments(
            @PathVariable String slug,
            @PathVariable UUID holdId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(legalHoldService.listDocuments(slug, holdId, page, size, authentication));
    }

    @PostMapping("/{holdId}/documents")
    public ResponseEntity<LegalHoldDTO> addDocuments(
            @PathVariable String slug,
            @PathVariable UUID holdId,
            @RequestBody LegalHoldTargetsDTO targets,
            Authentication authentication
    ) {
        return ResponseEntity.ok(legalHoldService.addDocuments(slug, holdId, targets, authentication));
    }

    @PostMapping("/{holdId}/release")
    public ResponseEntity<LegalHoldDTO> release(
            @PathVariable String slug,
            @PathVariable UUID holdId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(legalHoldService.release(slug, holdId, authentication));
    }
}
