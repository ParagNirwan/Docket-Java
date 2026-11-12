package com.docket.app.dto;

// One line of a bulk invite, both in the preview and in the result.
// status: OK or SENT when it's fine, otherwise the reason it was skipped:
// INVALID_EMAIL, INVALID_ROLE, ROLE_NOT_ALLOWED, DUPLICATE, ALREADY_MEMBER, ALREADY_INVITED or ERROR.
public record BulkInviteRowDTO(int line, String email, String role, String name, String status, String message) {
}
