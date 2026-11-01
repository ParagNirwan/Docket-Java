package com.docket.app.controller;

import com.docket.app.dto.SessionDTO;
import com.docket.app.service.JwtService;
import com.docket.app.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// "Where am I logged in?" and "log that device out."
@RestController
@RequestMapping("/api/me/sessions")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessionService;
    private final JwtService jwtService;

    @GetMapping
    public ResponseEntity<List<SessionDTO>> listSessions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            Authentication authentication
    ) {
        // The JWT filter has already validated this header.
        UUID currentSessionId = jwtService.extractSessionId(authorization.substring("Bearer ".length()));
        return ResponseEntity.ok(sessionService.listSessions(authentication.getName(), currentSessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId, Authentication authentication) {
        sessionService.revokeSession(authentication.getName(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> revokeAllSessions(Authentication authentication) {
        sessionService.revokeAllSessions(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
