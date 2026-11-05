package com.docket.app.controller;

import com.docket.app.dto.*;
import com.docket.app.service.TwoFactorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/2fa")
@RequiredArgsConstructor
public class TwoFactorController {
    private final TwoFactorService twoFactorService;

    @GetMapping("/status")
    public ResponseEntity<TwoFactorStatusResponseDTO> status(Authentication authentication) {
        return ResponseEntity.ok(twoFactorService.getStatus(authentication));
    }

    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponseDTO> setup(Authentication authentication) {
        return ResponseEntity.ok(twoFactorService.setup(authentication));
    }

    @PostMapping("/enable")
    public ResponseEntity<TwoFactorEnableResponseDTO> enable(
            @Valid @RequestBody TwoFactorEnableRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(twoFactorService.enable(request.code(), authentication));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(
            @Valid @RequestBody TwoFactorDisableRequestDTO request,
            Authentication authentication
    ) {
        twoFactorService.disable(request.password(), authentication);
        return ResponseEntity.noContent().build();
    }
}

