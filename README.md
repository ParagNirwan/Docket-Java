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
- Soft-delete (recoverable)

### AI-powered search (RAG)

Available when the `gcp` Spring profile is active. The pipeline:

1. **Upload** — file is stored and a `DocumentUploadedEvent` is published.
2. **Text extraction** — Apache Tika extracts raw text from PDF, DOCX, etc.
3. **Chunking** — text is split into overlapping segments (2 000 chars, 200 overlap).
4. **Embedding** — each chunk is embedded with Vertex AI `text-embedding-005` (768-dim vectors).
5. **Storage** — vectors are stored in pgvector `document_chunks` table.
6. **Query** — user question is embedded, top-K similar chunks are retrieved via cosine similarity, and Gemini 2.0 Flash generates an answer grounded in those chunks with source citations.

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

### Folders

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/api/tenants/{slug}/folders/{id}/children` | List sub-folders |
| POST | `/api/tenants/{slug}/folders/{id}/children` | Create sub-folder |

### Documents

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/api/tenants/{slug}/folders/{id}/documents` | List documents (paginated) |
| POST | `/api/tenants/{slug}/folders/{id}/documents` | Upload file |
| GET | `/api/tenants/{slug}/documents/{id}` | Document metadata |
| GET | `/api/tenants/{slug}/documents/{id}/download` | Download file |
| DELETE | `/api/tenants/{slug}/documents/{id}` | Soft-delete |
| GET | `/api/tenants/{slug}/documents/{id}/indexing-status` | AI indexing status |
| POST | `/api/tenants/{slug}/documents/{id}/reindex` | Re-trigger AI processing |

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

---

## Environment variables

| Variable | Required | Default | Description |
| :--- | :---: | :--- | :--- |
| `POSTGRES_PASSWORD` | Yes | `docket_dev_password` | Database password |
| `JWT_SECRET` | Yes | — | Base64-encoded HMAC key for JWT signing |
| `JWT_EXPIRATION` | No | `86400000` (24h) | JWT token lifetime in ms |
| `GCP_PROJECT_ID` | Prod | — | Google Cloud project (for Vertex AI / GCS) |
| `GCP_LOCATION` | No | `europe-west3` | GCP region |
| `VITE_API_URL` | No | `/api` | Frontend API base path |
