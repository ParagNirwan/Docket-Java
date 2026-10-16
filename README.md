# Docket

Full-stack application with PostgreSQL, Spring Boot (Java 21), and React + Vite (TypeScript).

## 🚀 Quick Start (Docker + Local Backend)

PostgreSQL and the frontend run in Docker. Run the Spring Boot backend locally so backend code changes take effect without rebuilding a Docker image.

Start the Docker services using one of these options:
1. **VS Code:** Press `Ctrl + Shift + B` (runs `🚀 Start Docker Services`).
2. **Windows Explorer:** Double-click [`start.bat`](./start.bat).
3. **NPM / Terminal:**
   ```bash
   npm start
   ```
   *(or `docker compose up --build --remove-orphans`)*

Then, in a separate PowerShell window from the repository root, start the backend:

```powershell
.\app\mvnw.cmd -f .\app\pom.xml spring-boot:run
```

The backend connects to the Dockerized PostgreSQL on `localhost:5433`. Ensure the root `.env` contains the database password and a valid `JWT_SECRET`.

## 🌐 Running Services

| Service | URL | Notes |
| :--- | :--- | :--- |
| **Frontend** | [http://localhost:5173](http://localhost:5173) | React + Vite with HMR hot reloading |
| **Backend API** | [http://localhost:8080](http://localhost:8080) | Run locally with the command above; frontend proxy targets `host.docker.internal:8080` |
| **Actuator Health** | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | Backend health endpoint |
| **PostgreSQL** | `localhost:5433` (db: `docketdb`) | User: `docket`, Pass: `docket_dev_password` |

---

## 🛑 Stop Services

```bash
docker compose down --remove-orphans
```
*(Or in VS Code: Run Task -> `🛑 Stop Docker Services`. Stop the locally running backend separately with `Ctrl+C`.)*

# Docket-Java

## Tenant authorization

All API routes except `/api/auth/**`, `/actuator/health`, and `/error` require authentication. Tenant authorization is checked against the authenticated user's current database membership; JWT tenant and role claims are not used to authorize tenant operations.

### Current endpoint policy

| Endpoint | Scope | Authorization |
| --- | --- | --- |
| `POST /api/auth/register` | Public account creation | Public |
| `POST /api/auth/login` | Public authentication | Public |
| `POST /api/tenants/create` | Account-level tenant creation | Any authenticated user; creator becomes the active `OWNER` |
| `GET /api/users/list-tenants` | Current user's tenant list | Authenticated user only; includes only active memberships in active tenants |
| `GET /api/tenants/by-slug/{slug}` | Tenant metadata | Active membership plus `tenant.read` |
| `GET /api/tenants/by-slug/{slug}/permissions` | Current user's tenant permissions | Active membership in an active tenant; values are computed server-side |

Membership denial and unavailable tenant both return `404` to avoid revealing tenant existence. An authenticated member who lacks a specific permission receives `403`; unauthenticated requests receive `401`. Duplicate active memberships for a user and tenant fail with `409` rather than selecting an ambiguous role. Membership and tenant status changes are checked on each request, so an old JWT does not retain tenant access.

### Default tenant role matrix

| Permission | OWNER | ADMIN | MANAGER | MEMBER |
| --- | --- | --- | --- | --- |
| `tenant.read` | Yes | Yes | Yes | Yes |
| `tenant.update` | Yes | Yes | No | No |
| `tenant.transfer_ownership` | Yes | No | No | No |
| `members.read` | Yes | Yes | Yes | No |
| `members.invite` | Yes | Yes | Yes | No |
| `members.update_role` | Yes | Yes | No | No |
| `members.remove` | Yes | Yes | No | No |
| `documents.read` | Yes | Yes | Yes | Yes |
| `documents.create` | Yes | Yes | Yes | No |
| `documents.update` | Yes | Yes | Yes | No |
| `documents.delete` | Yes | Yes | No | No |

The policy is implemented in `app/src/main/java/com/docket/app/security/RolePermissions.java`; permissions are stable API codes declared by `Permission`. Unmapped/null roles fail closed. Tenant creation is an account-level operation, not a tenant permission, because the user has no membership in that tenant until creation completes.

Migration `V2__add_tenant_status_and_membership_indexes.sql` backfills existing tenants as `ACTIVE`, makes tenant status required, and adds indexes for membership authorization and tenant listing. Existing memberships with null roles remain null and receive no permissions.

### Scope and extension rules

The current application has no document CRUD or member-management API controllers. The corresponding permission codes and default grants are reserved for those future features; they do not protect data endpoints that do not yet exist. “Partners”, “Trash”, and email sections in the workspace are currently mock frontend content. The UI hides email tools until a backend permission and API policy are defined.

When adding a tenant-owned endpoint:

1. Require the tenant identifier/slug from the route and resolve it server-side.
2. Call `TenantAuthorizationService.requirePermission` before reading or mutating tenant data; scope repository queries to that tenant as well.
3. Add the permission to `Permission`, assign explicit role grants in `RolePermissions`, and add allow/deny tests.
4. Return only the minimum data needed and add cross-tenant denial tests for IDs, paths, and query parameters.
5. Gate the corresponding frontend navigation/action with the server-returned permission code. UI visibility is not a substitute for backend enforcement.

There are no APIs for assigning roles, removing members, transferring ownership, or custom permission administration yet. `RolePermissions.canAssignRole` encodes the default grant ceiling (only owners may grant `ADMIN`; no role may assign `OWNER` through ordinary role editing). Any future ownership transfer must separately require `tenant.transfer_ownership`. Do not expose those actions until the rules have endpoint-level enforcement and tests. Existing memberships with a null role receive no permissions and must be remediated rather than assigned an implicit elevated role.
