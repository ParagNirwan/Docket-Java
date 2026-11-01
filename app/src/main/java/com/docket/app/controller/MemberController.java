package com.docket.app.controller;

import com.docket.app.dto.MemberDTO;
import com.docket.app.dto.UpdateMemberRoleRequestDTO;
import com.docket.app.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{slug}/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<List<MemberDTO>> list(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(memberService.list(slug, authentication));
    }

    @PatchMapping("/{membershipId}")
    public ResponseEntity<Void> updateRole(
            @PathVariable String slug,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateMemberRoleRequestDTO request,
            Authentication authentication
    ) {
        memberService.updateRole(slug, membershipId, request.getRole(), authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{membershipId}")
    public ResponseEntity<Void> remove(
            @PathVariable String slug,
            @PathVariable UUID membershipId,
            Authentication authentication
    ) {
        memberService.remove(slug, membershipId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@PathVariable String slug, Authentication authentication) {
        memberService.leave(slug, authentication);
        return ResponseEntity.noContent().build();
    }
}
