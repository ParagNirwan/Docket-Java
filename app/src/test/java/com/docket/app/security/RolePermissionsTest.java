package com.docket.app.security;

import com.docket.app.enums.Permission;
import com.docket.app.enums.Roles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionsTest {
    private static final Set<Permission> ADMIN_PERMISSIONS = Set.of(
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
            Permission.FOLDERS_UPDATE);

    @Test
    void ownerAndAdminHaveAdministrativeAndFeaturePermissions() {
        assertEquals(ADMIN_PERMISSIONS, RolePermissions.forRole(Roles.ADMIN));
        assertEquals(
                Set.of(
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
                        Permission.FOLDERS_UPDATE),
                RolePermissions.forRole(Roles.OWNER));
    }

    @Test
    void managerCanReadAndWorkWithDocumentsButCannotChangeTenantOrRoles() {
        assertEquals(Set.of(
                Permission.TENANT_READ,
                Permission.MEMBERS_READ,
                Permission.MEMBERS_INVITE,
                Permission.DOCUMENTS_READ,
                Permission.DOCUMENTS_CREATE,
                Permission.DOCUMENTS_UPDATE,
                Permission.FOLDERS_CREATE,
                Permission.FOLDERS_DELETE,
                Permission.FOLDERS_UPDATE), RolePermissions.forRole(Roles.MANAGER));
        assertFalse(RolePermissions.allows(Roles.MANAGER, Permission.TENANT_UPDATE));
        assertFalse(RolePermissions.allows(Roles.MANAGER, Permission.MEMBERS_UPDATE_ROLE));
        assertFalse(RolePermissions.allows(Roles.MANAGER, Permission.DOCUMENTS_DELETE));
    }

    @Test
    void memberHasReadOnlyAccess() {
        assertEquals(Set.of(
                Permission.TENANT_READ,
                Permission.DOCUMENTS_READ), RolePermissions.forRole(Roles.MEMBER));
        assertFalse(RolePermissions.allows(Roles.MEMBER, Permission.DOCUMENTS_CREATE));
        assertFalse(RolePermissions.allows(Roles.MEMBER, Permission.MEMBERS_READ));
        assertFalse(RolePermissions.allows(Roles.MEMBER, Permission.TENANT_UPDATE));
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    void everyPermissionIsGrantedToAtLeastOneRole(Permission permission) {
        assertTrue(
                Arrays.stream(Roles.values()).anyMatch(role -> RolePermissions.allows(role, permission)),
                permission + " is not granted to any role"
        );
    }

    @Test
    void unmappedRoleOrPermissionFailsClosed() {
        assertEquals(Set.of(), RolePermissions.forRole(null));
        assertFalse(RolePermissions.allows(null, Permission.TENANT_READ));
        assertFalse(RolePermissions.allows(Roles.OWNER, null));
    }

    @Test
    void roleAssignmentsCannotGrantOwnerOrElevateTheActor() {
        assertTrue(RolePermissions.canAssignRole(Roles.OWNER, Roles.ADMIN));
        assertTrue(RolePermissions.canAssignRole(Roles.OWNER, Roles.MEMBER));
        assertFalse(RolePermissions.canAssignRole(Roles.OWNER, Roles.OWNER));
        assertTrue(RolePermissions.canAssignRole(Roles.ADMIN, Roles.MANAGER));
        assertTrue(RolePermissions.canAssignRole(Roles.ADMIN, Roles.MEMBER));
        assertFalse(RolePermissions.canAssignRole(Roles.ADMIN, Roles.ADMIN));
        assertFalse(RolePermissions.canAssignRole(Roles.MANAGER, Roles.MEMBER));
        assertFalse(RolePermissions.canAssignRole(null, Roles.MEMBER));
    }
}
