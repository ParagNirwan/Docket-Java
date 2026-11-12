package com.docket.app.controller;

import com.docket.app.dto.DuplicateDocumentGroupDTO;
import com.docket.app.dto.DocumentResponseDTO;
import com.docket.app.dto.FavoriteResultDTO;
import com.docket.app.service.DocumentDuplicateService;
import com.docket.app.service.DocumentFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class DocumentPersonalizationController {
    private final DocumentFavoriteService documentFavoriteService;
    private final DocumentDuplicateService documentDuplicateService;

    @PutMapping("/documents/{docId}/favorite")
    public ResponseEntity<FavoriteResultDTO> toggleFavorite(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentFavoriteService.toggleFavorite(slug, docId, authentication));
    }

    @DeleteMapping("/documents/{docId}/favorite")
    public ResponseEntity<FavoriteResultDTO> removeFavorite(
            @PathVariable String slug,
            @PathVariable UUID docId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentFavoriteService.removeFavorite(slug, docId, authentication));
    }

    @GetMapping("/documents/favorites")
    public ResponseEntity<List<DocumentResponseDTO>> listFavorites(
            @PathVariable String slug,
            Pageable pageable,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentFavoriteService.listFavorites(slug, authentication, pageable));
    }

    @GetMapping("/documents/recent")
    public ResponseEntity<List<DocumentResponseDTO>> recentDocuments(
            @PathVariable String slug,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentFavoriteService.recentDocuments(slug, authentication, limit));
    }

    @GetMapping("/documents/duplicates")
    public ResponseEntity<List<DuplicateDocumentGroupDTO>> duplicates(
            @PathVariable String slug,
            Pageable pageable,
            Authentication authentication
    ) {
        return ResponseEntity.ok(documentDuplicateService.findDuplicates(slug, authentication, pageable));
    }
}
