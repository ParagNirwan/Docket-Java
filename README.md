# Docket

A multi-tenant document management platform with AI-powered search. Upload documents, organise them in a hierarchical folder system ("Trays"), and query their contents through a Retrieval-Augmented Generation (RAG) pipeline backed by Google Vertex AI.

## Tech stack

| Layer | Technology |
| :--- | :--- |
| **Frontend** | React 19, TypeScript 6, Vite 8, Tailwind CSS 4, React Router 7 |
| **Backend** | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway |
| **Database** | PostgreSQL 18 with pgvector extension |
| **AI / ML** | Google Vertex AI (Gemini 2.0 Flash, text-embedding-005), Apache Tika for text extraction |
| **Infrastructure** | Docker Compose (dev), GKE Autopilot (prod), Kustomize, GitHub Actions CI/CD |
| **Storage** | Local filesystem (dev), Google Cloud Storage (prod) |

## Project structure

```
├── app/                        # Spring Boot backend
│   ├── src/main/java/com/docket/app/
│   │   ├── controller/         # REST controllers (Auth, Tenant, Document, Folder, AI)
│   │   ├── dto/                # Request/response DTOs
│   │   ├── entity/             # JPA entities
│   │   ├── enums/              # Roles, Permissions, statuses
│   │   ├── repository/         # Spring Data repositories
│   │   ├── security/           # JWT filter, role-permission matrix, SecurityConfig
│   │   └── service/            # Business logic & AI pipeline
│   │       └── ai/             # Chunking, embedding, RAG query, text extraction
│   ├── src/main/resources/
│   │   ├── db/migration/       # Flyway migrations (V1–V6)
│   │   └── application*.properties
│   └── Dockerfile
├── frontend/                   # React SPA
│   ├── src/
│   │   ├── components/         # UI components (Sidebar, FolderBrowser, AiChat, etc.)
│   │   ├── pages/              # Route pages (Login, Register, Workspace, Settings)
│   │   ├── context/            # Auth context provider
│   │   ├── services/           # API client modules
│   │   └── lib/                # Utilities (JWT, permissions, validation)
│   └── Dockerfile
├── k8s/                        # Kubernetes manifests (Kustomize base + dev/prod overlays)
├── .github/workflows/          # CI (build & test) and CD (GKE deploy) pipelines
├── compose.yaml                # Docker Compose for local development
├── compose.prod.yaml           # Production compose overrides
└── .env.example                # Required environment variables
```

## Quick start (local development)

PostgreSQL and the frontend run in Docker. The Spring Boot backend runs locally so code changes take effect without rebuilding an image.

### 1. Environment

Copy `.env.example` to `.env` and fill in the values:

```
POSTGRES_PASSWORD=docket_dev_password
JWT_SECRET=<base64-encoded secret>
```

### 2. Start Docker services

Pick one:

| Method | Command |
| :--- | :--- |
| VS Code | `Ctrl + Shift + B` (runs "Start Docker Services" task) |
| Windows Explorer | Double-click `start.bat` |
| Terminal | `npm start` or `docker compose up --build --remove-orphans` |

### 3. Start the backend

```powershell
.\app\mvnw.cmd -f .\app\pom.xml spring-boot:run
```

The backend connects to Dockerised PostgreSQL on `localhost:5433`.

### Running services

| Service | URL | Notes |
| :--- | :--- | :--- |
| **Frontend** | http://localhost:5173 | React + Vite with HMR |
| **Backend API** | http://localhost:8080 | Frontend proxy targets `host.docker.internal:8080` |
| **Actuator** | http://localhost:8080/actuator/health | Health check |
| **PostgreSQL** | `localhost:5433` (db: `docketdb`) | User: `docket` |

### Stop services

```bash
docker compose down --remove-orphans
```

Stop the local backend separately with `Ctrl+C`.

---

## Core features

### Multi-tenancy

Users register an account and create **tenants** (organisations). Each tenant is isolated — its data, folders, and documents are scoped by tenant ID at every layer. Tenant membership is checked on every request; JWT claims are not trusted for authorisation.

### Folder hierarchy (Trays)

Documents live inside a hierarchical folder system. **Trays** are root-level folders shown in the sidebar; sub-folders nest inside them. The `folders` table uses a self-referential `parent_folder_id` to represent the tree.

### Document management

- Upload files up to 50 MB (multipart)
- Paginated listing per folder
- Download with XSS-safe `Content-Disposition` headers
- Deleting moves a document to the Trash, where it can be restored
- Rename, move and copy documents and folders from the `⋮` menu, a folder picker, or by dragging
- Moving a folder takes everything inside it along. Who can see something always follows the tray it's in, so a move into a restricted tray hides it straight away

### Trash

The Trash tray is admin-only and shows deleted documents. They can be restored to their folder, or to the Inbox if the folder is gone, and come back into search and AI answers. Each organization chooses how long they stay (30 days by default). After that a daily job removes the row and the stored file, unless the document is locked.

Deleting a folder or tray sends its documents to the Trash. The default trays can't be deleted or renamed.

### Retention and legal hold

A retention policy on a tray or folder says how long its documents must be kept. Settings → Retention manages them. Dates only ever move later, and the strictest policy over a document wins. See [docs/retention.md](docs/retention.md).

| Protection | Delete, move or rename | Who can go past it |
| :--- | :--- | :--- |
| **Legal hold** | Blocked until released | Nobody. Only the Owner can release it |
| **Compliance lock** | Blocked until the date | Nobody. The policy can only be extended |
| **Governance lock** | Blocked until the date | The Owner, with a reason that's audited |
| **Archived** | Rename and move are blocked, delete is allowed | Unarchive first, once nothing else protects it |

Every path goes through `RetentionGuard`, and a database trigger refuses to delete a locked row even by cascade.

### Notifications

The bell in the header shows notifications for invites, new members, role changes, failed indexing, retention expiry and legal holds. Each person chooses in-app, email or a daily digest.

### Search

The search box in the header (`Ctrl+K` or `⌘K`) finds documents by file name, including misspelled names, and by the text inside them, with the match highlighted. Filter by type, tray and date. Results respect tray visibility, and leave out deleted documents and the Trash tray.

### Members and invites

Settings in the workspace sidebar lists who's in the organization. People are invited by email with a role, and the invite link works for existing accounts and new sign-ups alike. Links are single-use, expire after 7 days, and are only stored as a hash.

What someone can do to another person depends on both their roles: see `RolePermissions.canAssignRole`.

| Role | Can invite as | Can change or remove |
| :--- | :--- | :--- |
| **Owner** | Admin, Manager, Member | Admin, Manager, Member |
| **Admin** | Manager, Member | Manager, Member |
| **Manager** | Member | No one |

Nobody can change their own role or remove themselves. Anyone except the Owner can leave. The Owner hands over the organization with their password in Settings → Danger zone, and both people are emailed.

### AI-powered search (RAG)

Available when the `gcp` Spring profile is active. The pipeline:

1. **Upload** — file is stored and a `DocumentUploadedEvent` is published.
2. **Text extraction** — Apache Tika extracts text from PDF, DOCX, etc., keeping page numbers and headings. Cloud Vision OCR reads images and scanned PDFs.
3. **Chunking** — text is split into overlapping segments (2 000 chars, 200 overlap).
4. **Embedding** — each chunk is embedded with Vertex AI `text-embedding-005` (768-dim vectors), with its file name, folder and heading as context.
5. **Storage** — vectors are stored in pgvector `document_chunks` table, alongside a full-text index.
6. **Query** — follow-up questions are rewritten to stand alone, vector and keyword search results are merged, unrelated chunks are dropped, and Gemini generates an answer. Only the sources it cites are returned.

See [docs/ai-search.md](docs/ai-search.md) for details, Google Cloud setup and re-indexing.

Conversational AI sessions are persisted (create, list, send follow-up, delete). Rate limiting caps queries at 10 per user per minute.

---

## API overview

### Authentication

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | `/api/auth/register` | Create account |
| POST | `/api/auth/login` | Authenticate, receive JWT |

### Tenants & users

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | `/api/tenants/create` | Create a tenant (caller becomes OWNER) |
| GET | `/api/users/list-tenants` | List current user's active tenants |
| GET | `/api/tenants/by-slug/{slug}` | Tenant metadata |
| GET | `/api/tenants/by-slug/{slug}/permissions` | Current user's permissions |
| POST | `/api/tenants/{slug}/transfer-ownership` | Hand over the organization, confirmed by password |

### Notifications

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/api/me/notifications` | Your notifications (paged) |
| GET | `/api/me/notifications/unread-count` | How many are unread |
| POST | `/api/me/notifications/{id}/read` | Mark one read |
| POST | `/api/me/notifications/read-all` | Mark all read |
| GET, PUT | `/api/me/notification-preferences` | In-app, email and daily digest |

### Members

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/api/tenants/{slug}/members` | List members |
| PATCH | `/api/tenants/{slug}/members/{id}` | Change a member's role |
| DELETE | `/api/tenants/{slug}/members/{id}` | Remove a member |
| POST | `/api/tenants/{slug}/members/leave` | Leave the organization |

### Invitations

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | `/api/tenants/{slug}/invitations` | Invite someone by email |
| GET | `/api/tenants/{slug}/invitations` | List pending invitations |
| POST | `/api/tenants/{slug}/invitations/{id}/resend` | Send a new link. The old one stops working |
| DELETE | `/api/tenants/{slug}/invitations/{id}` | Revoke |
| GET | `/api/invitations/{token}` | Public: what the invite is for |
| POST | `/api/invitations/{token}/accept` | Join, if signed in with the invited address |

### Folders

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/api/tenants/{slug}/folders/{id}/children` | List sub-folders |
| POST | `/api/tenants/{slug}/folders/{id}/children` | Create sub-folder |
| GET | `/api/tenants/{slug}/folders/{id}/breadcrumb` | Path from the tray down to this folder |
| PATCH | `/api/tenants/{slug}/folders/{id}` | Rename and/or move |
| DELETE | `/api/tenants/{slug}/folders/{id}` | Delete. Documents go to the Trash. 423 if any are locked |
| DELETE | `/api/tenants/{slug}/trays/{id}` | The same for a tray. Default trays can't be deleted |

### Documents

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/api/tenants/{slug}/folders/{id}/documents` | List documents (paginated) |
| POST | `/api/tenants/{slug}/folders/{id}/documents` | Upload file |
| GET | `/api/tenants/{slug}/documents/{id}` | Document metadata |
| GET | `/api/tenants/{slug}/documents/{id}/download` | Download file |
| PATCH | `/api/tenants/{slug}/documents/{id}` | Rename and/or move. A name that's taken is a 409, and the extension can't change |
| POST | `/api/tenants/{slug}/documents/{id}/copy` | Copy. A name that's taken gets a suffix, like `report (1).pdf` |
| DELETE | `/api/tenants/{slug}/documents/{id}` | Move to the Trash. An Owner can add `?override=true&reason=` to go past a Governance lock, here and on PATCH |
| POST | `/api/tenants/{slug}/documents/{id}/archive` | Move to the Archive tray, read-only |
| POST | `/api/tenants/{slug}/documents/archive` | Archive up to 100, all or nothing |
| GET | `/api/tenants/{slug}/documents/{id}/archive-preview` | What the lock date would be |
| POST | `/api/tenants/{slug}/documents/{id}/unarchive` | Back to where it came from, if not locked |
| GET | `/api/tenants/{slug}/documents/{id}/indexing-status` | AI indexing status |
| POST | `/api/tenants/{slug}/documents/{id}/reindex` | Re-trigger AI processing |
| GET | `/api/tenants/{slug}/search?q=&type=&tray=&from=&to=` | Keyword and file name search (paged) |

### Trash

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/api/tenants/{slug}/trash` | Deleted documents (paged) |
| POST | `/api/tenants/{slug}/documents/{id}/restore` | Restore |
| DELETE | `/api/tenants/{slug}/documents/{id}/purge` | Remove for good |
| DELETE | `/api/tenants/{slug}/trash` | Empty the Trash. Says how many locked documents stayed |
| GET, PUT | `/api/tenants/{slug}/trash-settings` | How long documents stay |

### Retention and legal holds

A locked document answers 423 with `{ message, retainUntil, lockMode, legalHold, archived }`.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET, POST | `/api/tenants/{slug}/retention-policies` | List and create |
| GET | `/api/tenants/{slug}/retention-policies/summary` | Locked and expiring counts |
| GET, PUT, DELETE | `/api/tenants/{slug}/retention-policies/{id}` | A Compliance policy can only be extended (409 otherwise) |
| GET, POST | `/api/tenants/{slug}/legal-holds` | List, and apply to documents, folders or a search |
| GET, POST | `/api/tenants/{slug}/legal-holds/{id}/documents` | What it covers, and add more |
| POST | `/api/tenants/{slug}/legal-holds/{id}/release` | Owner only |

### AI (requires `gcp` profile)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | `/api/tenants/{slug}/ai/query` | Single-shot RAG query |
| GET | `/api/tenants/{slug}/ai/conversations` | List conversations |
| GET | `/api/tenants/{slug}/ai/conversations/{id}` | Get conversation with messages |
| POST | `/api/tenants/{slug}/ai/conversations` | Start conversation |
| POST | `/api/tenants/{slug}/ai/conversations/{id}/messages` | Send follow-up |
| DELETE | `/api/tenants/{slug}/ai/conversations/{id}` | Delete conversation |

---

## Authorisation

All routes except auth and health require a valid JWT. Tenant access is verified against live database membership, not JWT claims.

### Role–permission matrix

| Permission | OWNER | ADMIN | MANAGER | MEMBER |
| :--- | :---: | :---: | :---: | :---: |
| `tenant.read` | ✓ | ✓ | ✓ | ✓ |
| `tenant.update` | ✓ | ✓ | | |
| `tenant.transfer_ownership` | ✓ | | | |
| `members.read` | ✓ | ✓ | ✓ | |
| `members.invite` | ✓ | ✓ | ✓ | |
| `members.update_role` | ✓ | ✓ | | |
| `members.remove` | ✓ | ✓ | | |
| `documents.read` | ✓ | ✓ | ✓ | ✓ |
| `documents.create` | ✓ | ✓ | ✓ | |
| `documents.update` | ✓ | ✓ | ✓ | |
| `documents.delete` | ✓ | ✓ | | |
| `retention.read` | ✓ | ✓ | | |
| `retention.manage` | ✓ | ✓ | | |
| `retention.override` | ✓ | | | |

Implemented in `RolePermissions.java`. Unmapped or null roles receive no permissions (fail closed). The frontend gates UI elements with server-returned permission codes, but all enforcement happens server-side.

### Adding a new tenant-scoped endpoint

1. Resolve the tenant from the route slug server-side.
2. Call `TenantAuthorizationService.requirePermission` before any data access.
3. Add the permission to the `Permission` enum and assign role grants in `RolePermissions`.
4. Scope repository queries to the tenant; return minimum data.
5. Write allow/deny and cross-tenant denial tests.
6. Gate the corresponding frontend action with the server-returned permission code.

---

## CI/CD

### CI (`ci.yml`)

Runs on push/PR to `main`:
- **Backend** — `mvn clean verify` (Java 21, Temurin)
- **Frontend** — `npm ci` → lint (oxlint) → build → test (Vitest)

### CD (`cd.yml`)

Runs on push to `main`:
1. Builds Docker images for backend and frontend.
2. Pushes to Google Artifact Registry (`europe-west3-docker.pkg.dev/docket-java/docket`).
3. Deploys to GKE Autopilot via Kustomize (`k8s/prod/`), with automatic rollback on failure.

---

## Database migrations

Managed by Flyway. Migrations live in `app/src/main/resources/db/migration/`:

| Migration | Purpose |
| :--- | :--- |
| V1 | Users, tenants, tenant memberships |
| V2 | Tenant status, membership indexes |
| V3 | Folders / Trays hierarchy |
| V4 | Documents table |
| V5 | pgvector extension, document chunks with embeddings |
| V6 | AI conversations and messages |
| V7 | One active owner per organization |
| V8 | Sub-folders record their tray |
| V9 | Email outbox |
| V10 | Append-only audit log |
| V11 | Refresh tokens and token versions |
| V12 | Auth rate limit counters |
| V13 | Account tokens and email verification |
| V14 | Chunk locations, full-text search, embedding versions, rewritten queries |
| V15 | Invitations, membership join dates, one active membership per person, file name search index |
| V16 | Retention policies, legal holds, trash and archive columns, delete trigger, notifications, job leases |

---

## Environment variables

| Variable | Required | Default | Description |
| :--- | :---: | :--- | :--- |
| `POSTGRES_PASSWORD` | Yes | `docket_dev_password` | Database password |
| `JWT_SECRET` | Yes | — | Base64-encoded HMAC key for JWT signing |
| `JWT_ACCESS_TOKEN_TTL_MS` | No | `900000` (15 min) | Access token lifetime in ms |
| `AUTH_COOKIE_SECURE` | No | `false`, `true` in prod | Send the refresh cookie over HTTPS only |
| `TRUSTED_PROXY_HOPS` | No | `0` | Proxies in front of the backend that append to `X-Forwarded-For` |
| `CORS_ALLOWED_ORIGINS` | No | Local dev URLs, `APP_BASE_URL` in prod | Exact origins allowed to call the API |
| `APP_BASE_URL` | Prod | `http://localhost:5173` | Public frontend URL, used for links in emails |
| `EMAIL_PROVIDER` | No | `logging` | `logging` (local only), `smtp`, `resend` or `disabled`. See [docs/email.md](docs/email.md) |
| `EMAIL_FROM` | Prod | `Docket <no-reply@docket.local>` | Sender of outgoing email |
| `RESEND_API_KEY` | With `resend` | — | Resend API key |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | With `smtp` | `localhost` / `1025` | SMTP server |
| `AUDIT_RETENTION_DAYS` | No | `365` | How long audit events are kept. Minimum 365 |
| `INVITATION_TTL_DAYS` | No | `7` | How long an invite link works |
| `INVITATION_LIMIT_PER_HOUR` | No | `50` | Invites one organization can send per hour |
| `RETENTION_EXPIRY_CRON` | No | `0 30 2 * * *` | When the retention expiry job runs |
| `TRASH_PURGE_CRON` | No | `0 0 4 * * *` | When old documents are removed from the Trash |
| `NOTIFICATION_DIGEST_CRON` | No | `0 0 7 * * *` | When daily digests go out |
| `GCP_PROJECT_ID` | Prod | — | Google Cloud project (for Vertex AI / GCS) |
| `GCP_LOCATION` | No | `europe-west3` | GCP region |
| `AI_RETRIEVAL_MAX_DISTANCE` | No | `0.45` | Chunks further than this are dropped. See [docs/ai-search.md](docs/ai-search.md) |
| `AI_RETRIEVAL_HYBRID` | No | `true` | Combine keyword search with vector search |
| `AI_REINDEX_ENABLED` | No | `true` | Bring existing documents up to the current embedding version |
| `AI_OCR_ENDPOINT` | No | `vision.googleapis.com:443` | Use `eu-vision.googleapis.com:443` to keep OCR in the EU |
| `AI_OCR_MAX_PAGES` | No | `200` | Longest scanned PDF to send to OCR |
| `VITE_API_URL` | No | `/api` | Frontend API base path |
