package com.docket.app.controller;

import com.docket.app.entity.Tenant;
import com.docket.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The "front desk" for user-related requests from the website.
// All endpoints live under /api/users.
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // GET /api/users/list-tenants
    // "Which organizations do I belong to?"
    // Uses the logged-in person's email to find all their active orgs.
    // This powers the "All tenants" list on the frontend.
    @GetMapping("/list-tenants")
    public ResponseEntity<List<Tenant>> listTenants(Authentication authentication) {
        List<Tenant> tenants = userService.listTenantsForUser(authentication.getName());
        return ResponseEntity.ok(tenants);
    }
}