package com.docket.app.controller;

import com.docket.app.dto.TrashDocumentDTO;
import com.docket.app.service.RetentionGuard;
import com.docket.app.service.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class TrashController {
    private final TrashService trashService;

    @GetMapping("/trash")
    public ResponseEntity<Page<TrashDocumentDTO>> list(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(trashService.list(slug, page, size, authentication));
    }

    // Removes everything that isn't locked, and says how many were left.
    @DeleteMapping("/trash")
    public ResponseEntity<TrashService.EmptyTrashResult> empty(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(trashService.emptyTrash(slug, authentication));
    }

    @PostMapping("/documents/{docId}/restore")
    public ResponseEntity<Void> restore(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        trashService.restore(slug, docId, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/documents/{docId}/purge")
    public ResponseEntity<Void> purge(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @RequestParam(defaultValue = "false") boolean override,
            @RequestParam(required = false) String reason,
            Authentication authentication
    ) {
        trashService.purge(slug, docId, new RetentionGuard.Override(override, reason), authentication);
        return ResponseEntity.noContent().build();
    }
}
