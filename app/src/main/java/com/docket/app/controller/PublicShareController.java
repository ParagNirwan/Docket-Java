package com.docket.app.controller;

import com.docket.app.dto.PublicShareInfoDTO;
import com.docket.app.dto.ShareDownloadRequestDTO;
import com.docket.app.service.DocumentService;
import com.docket.app.service.ShareLinkService;
import com.docket.app.security.ClientRequestInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/shares")
@RequiredArgsConstructor
public class PublicShareController {
    private final ShareLinkService shareLinkService;
    private final ClientRequestInfo clientRequestInfo;

    @GetMapping("/{token}")
    public ResponseEntity<PublicShareInfoDTO> getInfo(@PathVariable String token) {
        return ResponseEntity.ok(shareLinkService.getPublicShareInfo(token));
    }

    @PostMapping("/{token}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String token,
            @RequestBody(required = false) ShareDownloadRequestDTO request
    ) {
        String password = request != null ? request.password() : null;
        DocumentService.DocumentDownload download = shareLinkService.downloadSharedDocument(token, password, clientRequestInfo.ip());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.document().getOriginalFileName().replace("\"", "_") + "\"")
                .contentType(MediaType.parseMediaType(download.document().getContentType()))
                .contentLength(download.document().getFileSize())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }
}
