package com.docket.app.controller;

import com.docket.app.dto.CreatedServiceAccountDTO;
import com.docket.app.dto.ServiceAccountDTO;
import com.docket.app.dto.CreateServiceAccountRequestDTO;
import com.docket.app.service.ServiceAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}")
@RequiredArgsConstructor
public class ServiceAccountController {
    private final ServiceAccountService serviceAccountService;

    @GetMapping("/service-accounts")
    public ResponseEntity<List<ServiceAccountDTO>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(serviceAccountService.list(slug, authentication));
    }

    @PostMapping("/service-accounts")
    public ResponseEntity<CreatedServiceAccountDTO> create(
            @PathVariable String slug,
            @Valid @RequestBody CreateServiceAccountRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceAccountService.create(slug, request, authentication));
    }

    @DeleteMapping("/service-accounts/{accountId}")
    public ResponseEntity<ServiceAccountDTO> revoke(
            @PathVariable String slug,
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(serviceAccountService.revoke(slug, accountId, authentication));
    }
}