// The frontend's copy of all possible permission codes — matching exactly
// what the backend sends. If you misspell a permission, TypeScript will
// catch the error at build time instead of silently failing at runtime.
export type PermissionCode =
  | "tenant.read"
  | "tenant.update"
  | "tenant.transfer_ownership"
  | "members.read"
  | "members.invite"
  | "members.update_role"
  | "members.remove"
  | "documents.read"
  | "documents.create"
  | "documents.update"
  | "documents.delete"
  | "trays.create"
  | "trays.update"
  | "trays.delete"
  | "folders.create"
  | "folders.update"
  | "folders.delete";

// "Does this person have this specific permission?"
// Used throughout the UI to decide whether to show or hide buttons/sections.
export function hasPermission(
  permissions: ReadonlySet<string>,
  permission: PermissionCode,
): boolean {
  return permissions.has(permission);
}
