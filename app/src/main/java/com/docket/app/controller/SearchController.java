package com.docket.app.controller;

import com.docket.app.dto.SearchResultDTO;
import com.docket.app.service.DocumentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SearchController {
    private final DocumentSearchService documentSearchService;

    // GET /api/tenants/{slug}/search?q=&type=&tray=&from=&to=&page=0&size=20
    @GetMapping("/api/tenants/{slug}/search")
    public ResponseEntity<Page<SearchResultDTO>> search(
            @PathVariable String slug,
            @RequestParam String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID tray,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        var filter = new DocumentSearchService.Filter(q, type, tray, from, to);
        return ResponseEntity.ok(documentSearchService.search(slug, filter, page, size, authentication));
    }
}
