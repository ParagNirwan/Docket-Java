package com.docket.app.controller;
import com.docket.app.dto.ScanResultRequestDTO;
import com.docket.app.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
@RestController @RequestMapping("/api/internal/scans") @RequiredArgsConstructor
public class ScannerCallbackController {
  private final DocumentService documents;
  @Value("${docket.scanner.callback-token:}") private String token;
  @PostMapping("/{documentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
  void complete(@PathVariable UUID documentId, @RequestHeader(value="X-Docket-Scanner-Token", required=false) String supplied, @Valid @RequestBody ScanResultRequestDTO body) {
    if (token.isBlank() || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    documents.completeScan(documentId, body.status());
  }
  @PostMapping("/versions/{versionId}") @ResponseStatus(HttpStatus.NO_CONTENT)
  void completeVersion(@PathVariable UUID versionId, @RequestHeader(value="X-Docket-Scanner-Token", required=false) String supplied, @Valid @RequestBody ScanResultRequestDTO body) {
    if (token.isBlank() || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    documents.completeVersionScan(versionId, body.status());
  }
}
