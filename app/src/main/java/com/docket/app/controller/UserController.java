package com.docket.app.controller;

import com.docket.app.entity.Tenant;
import com.docket.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/list-tenants")
    public ResponseEntity<List<Tenant>> listTenants(Authentication authentication) {
        // authentication.getName() automatically retrieves the email stored in the JWT subject
        String email = authentication.getName();
        List<Tenant> tenants = userService.listTenantsForUser(email);
        return ResponseEntity.ok(tenants);
    }
}