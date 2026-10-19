package com.docket.app.controller;

import com.docket.app.entity.Folder;
import com.docket.app.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}/folders")
@RequiredArgsConstructor
public class FolderController {
    private final FolderService folderService;

    @GetMapping("/{folderId}/children")
    public ResponseEntity<List<Folder>> getChildren(
            @PathVariable String slug,
            @PathVariable UUID folderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(folderService.getChildren(slug, folderId, authentication));
    }

    @PostMapping("/{folderId}/children")
    public ResponseEntity<Folder> createSubFolder(
            @PathVariable String slug,
            @PathVariable UUID folderId,
            @jakarta.validation.Valid @RequestBody com.docket.app.dto.CreateFolderRequestDTO request,
            Authentication authentication
    ) {
        Folder created = folderService.createSubFolder(slug, folderId, request, authentication);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
    }
}
