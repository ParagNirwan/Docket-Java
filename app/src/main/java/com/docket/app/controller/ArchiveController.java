package com.docket.app.controller;

import com.docket.app.dto.ArchivePreviewDTO;
import com.docket.app.dto.ArchiveRequestDTO;
import com.docket.app.dto.BulkArchiveRequestDTO;
import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.service.ArchiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}/documents")
@RequiredArgsConstructor
public class ArchiveController {
    private final ArchiveService archiveService;

    @GetMapping("/{docId}/archive-preview")
    public ResponseEntity<ArchivePreviewDTO> preview(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @RequestParam(required = false) UUID folderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(archiveService.preview(slug, docId, folderId, authentication));
    }

    @PostMapping("/{docId}/archive")
    public ResponseEntity<DocumentResponseDTO> archive(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @RequestBody(required = false) ArchiveRequestDTO request,
            Authentication authentication
    ) {
        UUID folderId = request == null ? null : request.folderId();
        return ResponseEntity.ok(archiveService.archive(slug, docId, folderId, authentication));
    }

    @PostMapping("/archive")
    public ResponseEntity<List<DocumentResponseDTO>> archiveAll(
            @PathVariable String slug,
            @Valid @RequestBody BulkArchiveRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(archiveService.archiveAll(slug, request.documentIds(), request.folderId(), authentication));
    }

    @PostMapping("/{docId}/unarchive")
    public ResponseEntity<DocumentResponseDTO> unarchive(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(archiveService.unarchive(slug, docId, authentication));
    }
}
