package com.docket.app.controller;

import com.docket.app.dto.NotificationDTO;
import com.docket.app.dto.NotificationPreferencesDTO;
import com.docket.app.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

// Always the signed-in person's own notifications, across every organization.
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public ResponseEntity<Page<NotificationDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationService.list(page, size, authentication));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(authentication)));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication authentication) {
        notificationService.markRead(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notification-preferences")
    public ResponseEntity<NotificationPreferencesDTO> getPreferences(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getPreferences(authentication));
    }

    @PutMapping("/notification-preferences")
    public ResponseEntity<NotificationPreferencesDTO> updatePreferences(
            @RequestBody NotificationPreferencesDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationService.updatePreferences(request, authentication));
    }
}
