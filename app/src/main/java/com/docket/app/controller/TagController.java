package com.docket.app.controller;

import com.docket.app.dto.CreateTagRequestDTO;
import com.docket.app.dto.TagDTO;
import com.docket.app.dto.UpdateMetadataRequestDTO;
import com.docket.app.dto.UpdateTagsRequestDTO;
import com.docket.app.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    @GetMapping("/tags")
    public List<TagDTO> getTags(@PathVariable String slug, Authentication authentication) {
        return tagService.getTags(slug, authentication);
    }

    @PostMapping("/tags")
    public ResponseEntity<TagDTO> createTag(
            @PathVariable String slug,
            @Valid @RequestBody CreateTagRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(slug, request, authentication));
    }

    @DeleteMapping("/tags/{tagId}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable String slug,
            @PathVariable UUID tagId,
            Authentication authentication
    ) {
        tagService.deleteTag(slug, tagId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/{docId}/tags/{tagId}")
    public List<TagDTO> addTag(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @PathVariable UUID tagId,
            Authentication authentication
    ) {
        return tagService.addTagToDocument(slug, docId, tagId, authentication);
    }

    @DeleteMapping("/documents/{docId}/tags/{tagId}")
    public List<TagDTO> removeTag(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @PathVariable UUID tagId,
            Authentication authentication
    ) {
        return tagService.removeTagFromDocument(slug, docId, tagId, authentication);
    }

    @PutMapping("/documents/{docId}/tags")
    public List<TagDTO> setTags(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @RequestBody UpdateTagsRequestDTO request,
            Authentication authentication
    ) {
        return tagService.setDocumentTags(slug, docId, request.tagIds(), authentication);
    }

    @PutMapping("/documents/{docId}/metadata")
    public Map<String, Object> updateMetadata(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @RequestBody UpdateMetadataRequestDTO request,
            Authentication authentication
    ) {
        return tagService.updateDocumentMetadata(slug, docId, request.metadata(), authentication);
    }
}

