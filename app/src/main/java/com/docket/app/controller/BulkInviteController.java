package com.docket.app.controller;

import com.docket.app.dto.BulkInviteRequestDTO;
import com.docket.app.dto.BulkInviteResultDTO;
import com.docket.app.dto.BulkInviteRowDTO;
import com.docket.app.service.BulkInviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{slug}/invitations/bulk")
@RequiredArgsConstructor
public class BulkInviteController {
    private final BulkInviteService bulkInviteService;

    // POST /api/tenants/{slug}/invitations/bulk/preview
    // Takes the CSV text itself and says what would happen to each line. Nothing is sent.
    @PostMapping(value = "/preview", consumes = {MediaType.TEXT_PLAIN_VALUE, "text/csv"})
    public ResponseEntity<List<BulkInviteRowDTO>> preview(
            @PathVariable String slug,
            @RequestBody String csv,
            Authentication authentication
    ) {
        return ResponseEntity.ok(bulkInviteService.preview(slug, csv, authentication));
    }

    // POST /api/tenants/{slug}/invitations/bulk
    @PostMapping
    public ResponseEntity<BulkInviteResultDTO> send(
            @PathVariable String slug,
            @Valid @RequestBody BulkInviteRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(bulkInviteService.send(slug, request, authentication));
    }
}
