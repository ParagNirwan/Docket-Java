package com.docket.app.security;

import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;

import java.util.Map;
import java.util.Set;

// The rulebook that decides which role gets which permissions.
// Think of it like a chart: rows are roles, columns are permissions, and
// each cell is either "yes" or "no."
public class RolePermissions {

    // The complete mapping of every role to the permissions it grants.
    private static final Map<Roles, Set<Permission>> GRANTS = Map.of(

            // Owner — gets every single permission. They created the org and can do anything.
            Roles.OWNER, Set.of(
                    Permission.TENANT_READ,
                    Permission.TENANT_UPDATE,
                    Permission.TENANT_TRANSFER_OWNERSHIP,
                    Permission.MEMBERS_READ,
                    Permission.MEMBERS_INVITE,
                    Permission.MEMBERS_UPDATE_ROLE,
                    Permission.MEMBERS_REMOVE,
                    Permission.DOCUMENTS_READ,
                    Permission.DOCUMENTS_CREATE,
                    Permission.DOCUMENTS_UPDATE,
                    Permission.DOCUMENTS_DELETE,
                    Permission.TRAYS_CREATE,
                    Permission.TRAYS_UPDATE,
                    Permission.TRAYS_DELETE,
                    Permission.FOLDERS_CREATE,
                    Permission.FOLDERS_DELETE,
                    Permission.FOLDERS_UPDATE,
                    Permission.AUDIT_READ
            ),

            // Admin — can do almost everything, EXCEPT transfer ownership.
            Roles.ADMIN, Set.of(
                    Permission.TENANT_READ,
                    Permission.TENANT_UPDATE,
                    Permission.MEMBERS_READ,
                    Permission.MEMBERS_INVITE,
                    Permission.MEMBERS_UPDATE_ROLE,
                    Permission.MEMBERS_REMOVE,
                    Permission.DOCUMENTS_READ,
                    Permission.DOCUMENTS_CREATE,
                    Permission.DOCUMENTS_UPDATE,
                    Permission.DOCUMENTS_DELETE,
                    Permission.TRAYS_CREATE,
                    Permission.TRAYS_UPDATE,
                    Permission.TRAYS_DELETE,
                    Permission.FOLDERS_CREATE,
                    Permission.FOLDERS_DELETE,
                    Permission.FOLDERS_UPDATE,
                    Permission.AUDIT_READ
            ),

            // Manager — can view things, invite people, work with documents, and create/delete sub-folders.
            Roles.MANAGER, Set.of(
                    Permission.TENANT_READ,
                    Permission.MEMBERS_READ,
                    Permission.MEMBERS_INVITE,
                    Permission.DOCUMENTS_READ,
                    Permission.DOCUMENTS_CREATE,
                    Permission.DOCUMENTS_UPDATE,
                    Permission.FOLDERS_CREATE,
                    Permission.FOLDERS_DELETE,
                    Permission.FOLDERS_UPDATE
            ),

            // Member — the most basic role. Can only view the org and read documents.
            Roles.MEMBER, Set.of(
                    Permission.TENANT_READ,
                    Permission.DOCUMENTS_READ
            )
    );

    // Private constructor — this class is just a collection of helper methods,
    // you never need to create an instance of it.
    private RolePermissions() {
    }

    // "Does this role have this permission?" — a simple yes/no lookup in the rulebook.
    public static boolean allows(Roles role, Permission permission) {
        return role != null
                && permission != null
                && GRANTS.getOrDefault(role, Set.of()).contains(permission);
    }

    // "Give me the full list of everything this role can do."
    // Used when we need to send the complete permission list to the frontend.
    public static Set<Permission> forRole(Roles role) {
        if (role == null) {
            return Set.of();
        }
        return Set.copyOf(GRANTS.getOrDefault(role, Set.of()));
    }

    // "Can this person assign someone else to a given role?"
    // Rules: Nobody can make someone an Owner (that's a special transfer).
    //        Owners can assign any other role.
    //        Admins can only set people to Manager or Member.
    //        Managers can only invite Members (they can't change an existing member's role).
    //        Members can't assign anything.
    public static boolean canAssignRole(Roles actorRole, Roles targetRole) {
        if (actorRole == null || targetRole == null || targetRole == Roles.OWNER) {
            return false;
        }

        return switch (actorRole) {
            case OWNER -> true;
            case ADMIN -> targetRole == Roles.MANAGER || targetRole == Roles.MEMBER;
            case MANAGER -> targetRole == Roles.MEMBER;
            case MEMBER -> false;
        };
    }
}
