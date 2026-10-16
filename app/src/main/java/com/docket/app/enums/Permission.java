
package com.docket.app.enums;

import lombok.Getter;

// The master list of every action someone can perform in an organization.
// Each entry has a short text code (like "tenant.read") that gets sent to
// the frontend so the website knows which buttons and sections to show.
@Getter
public enum Permission {

    // --- Organization-level permissions ---
    // Can you view the organization?
    TENANT_READ("tenant.read"),
    // Can you change the organization's settings (name, etc.)?
    TENANT_UPDATE("tenant.update"),
    // Can you hand over ownership of the organization to someone else?
    TENANT_TRANSFER_OWNERSHIP("tenant.transfer_ownership"),

    // --- People/member permissions ---
    // Can you see who's in the organization?
    MEMBERS_READ("members.read"),
    // Can you invite new people to the organization?
    MEMBERS_INVITE("members.invite"),
    // Can you change someone's role (e.g. promote Member to Manager)?
    MEMBERS_UPDATE_ROLE("members.update_role"),
    // Can you remove someone from the organization?
    MEMBERS_REMOVE("members.remove"),

    // --- Document permissions ---
    // Can you view documents?
    DOCUMENTS_READ("documents.read"),
    // Can you create new documents?
    DOCUMENTS_CREATE("documents.create"),
    // Can you edit existing documents?
    DOCUMENTS_UPDATE("documents.update"),
    // Can you delete documents?
    DOCUMENTS_DELETE("documents.delete");

    // The short text code sent to the frontend (e.g. "members.invite")
    private final String code;

    Permission(String code) {
        this.code = code;
    }

}