# Docket

A multi-tenant document vault and management platform with AI-powered search. Docket allows organizations to upload documents, organize them in a hierarchical folder system ("Trays"), and query their contents through a Retrieval-Augmented Generation (RAG) pipeline backed by Google Vertex AI.

## 🏛️ Core Capabilities

- **Multi-Tenant Architecture**: Complete isolation between organizations, with granular membership and ownership management.
- **AI-Powered Search (RAG)**: Hybrid vector and keyword retrieval, structure-aware chunking, diverse sources, clickable citations, and explicit ask/summary modes.
- **AI Answer Feedback**: Thumbs-up/down feedback with optional reasons, stored per tenant alongside retrieval and generation metrics.
- **Document Lifecycle Management**: 
  - **Retention Policies**: Define how long documents are kept.
  - **Legal Holds**: Prevent deletion of critical documents regardless of retention policy.
  - **Archive & Trash**: Tiered deletion workflow with auto-purge jobs.
- **Enterprise Compliance**:
  - **Append-only Audit Log**: Immutable record of every action taken within a tenant.
  - **OCR Pipeline**: Automatic text extraction from scanned PDFs and images via Google Vision AI.
- **Secure Authentication**: JWT-based auth with refresh tokens, session revocation, and rate limiting.

## 🛠️ Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS, React Router |
| **Backend** | Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway |
| **Database** | PostgreSQL 18 with `pgvector` extension |
| **AI / ML** | Google Vertex AI (Gemini, text-embedding-005), Apache Tika |
| **Infrastructure** | Docker Compose, GKE Autopilot, Kustomize, GitHub Actions |
| **Storage** | Local filesystem (dev), Google Cloud Storage (prod) |

## 📁 Project Structure

```
├── app/                        # Spring Boot backend
│   ├── src/main/java/com/docket/app/
│   │   ├── controller/         # REST endpoints
│   │   ├── dto/                # Data Transfer Objects
│   │   ├── entity/             # JPA Entities
│   │   ├── repository/         # Spring Data Repositories
│   │   ├── security/           # JWT, Auth & Role Logic
│   │   └── service/            # Business Logic & AI RAG Pipeline
│   └── src/main/resources/
│       └── db/migration/       # Flyway schema versions (V1–V25)
├── frontend/                   # React SPA
│   ├── src/
│   │   ├── components/         # UI Components
│   │   ├── pages/              # Route Pages
│   │   └── services/           # API Client
├── k8s/                        # Kubernetes Manifests (Kustomize)
├── .github/workflows/          # CI/CD Pipelines
├── compose.yaml                # Local development environment
└── .env.example                # Environment template
```

## 🚀 Quick Start (Local Development)

### 1. Environment Setup
Copy `.env.example` to `.env` and configure your secrets:
```bash
cp .env.example .env
```
*`compose.yaml` ships local-only fallbacks for `POSTGRES_PASSWORD` and `JWT_SECRET`, so the stack starts without a `.env`. Set real values before exposing it anywhere.*

### 2. Launching the Platform
`npm start`, `.\start.ps1` and `start.bat` are equivalent: each runs `docker compose up --build --remove-orphans`.

| Action | Command | Description |
| :--- | :--- | :--- |
| **Full Start** | `npm start` / `.\start.ps1` / `start.bat` | Builds and starts all services (DB, Backend, Frontend, Mailpit) |
| **Quick Up** | `npm run up` | Starts services without rebuilding images |
| **Stop** | `npm run down` | Stops and removes containers |
| **Logs** | `npm run logs` | Tail logs for all services |

### 3. Accessing Services
| Service | URL | Note |
| :--- | :--- | :--- |
| **Frontend** | `http://localhost:80` | Main Application UI |
| **Backend API** | `http://localhost:8080` | REST API |
| **Mailpit** | `http://localhost:8025` | Local email catcher (view outgoing emails here) |
| **PostgreSQL** | `localhost:5433` | Mapped to 5433 to avoid clashing with a host Postgres |

### 4. Running CI Checks Locally
These are the exact commands the `CI Pipeline` workflow ([.github/workflows/ci.yml](.github/workflows/ci.yml)) runs on every push and PR to `main` and `dev`:

| Job | Directory | Commands | Toolchain |
| :--- | :--- | :--- | :--- |
| Backend - Build & Test | `app/` | `./mvnw clean verify` (CI uses `mvn`) | Java 21 (Temurin) |
| Frontend - Lint, Build & Test | `frontend/` | `npm ci && npm run lint && npm run build && npm test` | Node 20 |
| AI Search Quality Evaluation | `app/` | `mvn clean package -DskipTests`, Flyway migrate, then the jar with `--docket.ai.evaluation.enabled=true` | Needs `pgvector/pgvector:pg18` and GCP credentials |

Backend tests include Testcontainers-based Flyway tests, so Docker must be running for `mvn verify`. The AI evaluation job needs Vertex AI access and is normally left to CI.

### 5. Deployment (CD)
The `CD Pipeline` ([.github/workflows/cd.yml](.github/workflows/cd.yml)) runs only on pushes to `main`. It builds both images, pushes them to Artifact Registry (`europe-west3`), renders `k8s/prod` with Kustomize, runs `.github/scripts/preflight.sh` to confirm secrets exist, applies to GKE Autopilot, and rolls back if either rollout fails.

---

## 🔀 Local vs. Production Configuration

| Concern | Local (`compose.yaml`) | Production (`prod` profile / GKE) |
| :--- | :--- | :--- |
| Spring profile | default | `prod` (+ `gcp` for Vertex AI / GCS) |
| Secrets | Fallback defaults in `compose.yaml` | Required, with no defaults; startup fails if `JWT_SECRET`, `APP_BASE_URL` or `EMAIL_FROM` is missing |
| Schema | Flyway | Flyway; `ddl-auto=none` |
| Email | `smtp` → Mailpit | `resend` or `disabled` |
| Storage | Local `uploads` volume | GCS bucket (`docket-java-uploads`) |
| Cookies | Insecure allowed (`http://localhost`) | `AUTH_COOKIE_SECURE=true` |
| CORS | Same origin through nginx | `CORS_ALLOWED_ORIGINS` (defaults to `APP_BASE_URL`) |
| Logging | Spring defaults | Root `WARN`, `com.docket` `INFO` |

To test the production profile locally: `docker compose -f compose.yaml -f compose.prod.yaml up --build`. Set `JWT_SECRET`, `APP_BASE_URL` and `EMAIL_FROM` first.

---

## ⚙️ Configuration

### Key Environment Variables
| Variable | Required | Default | Description |
| :--- | :---: | :--- | :--- |
| `POSTGRES_PASSWORD` | Yes | `docket_dev_password` | Database password |
| `JWT_SECRET` | Yes | — | Base64 HMAC key for JWT signing |
| `EMAIL_PROVIDER` | No | `logging` | `logging` (local), `smtp` (Mailpit), `resend`, or `disabled` |
| `GCP_PROJECT_ID` | Prod | — | Google Cloud project for AI/Storage |
| `AI_RETRIEVAL_HYBRID`| No | `true` | Enable combined keyword + vector search |
| `AUDIT_RETENTION_DAYS`| No | `365` | Audit log retention period |

### Database Migrations
Schema is managed via Flyway. Recent milestones include:
- **V1-V6**: Core auth, multi-tenancy, and basic RAG pipeline.
- **V7-V11**: Ownership logic, email outbox, and audit logging.
- **V12-V16**: Rate limiting, account verification, reliable hybrid search, retention, trash, and notifications.
- **V17-V19**: Versioning, tags, permissions, and background job lease improvements.
- **V20**: Tenant-scoped AI answer feedback.
- **V21-V25**: AI document summaries, enterprise features (SSO, API keys, webhooks), tenant email-verification setting, favourites, and service accounts.

## AI Search

AI search is available when the Spring `gcp` profile is active. Documents are extracted, split into heading-aware chunks (approximately 500 tokens), embedded, and indexed for hybrid vector and keyword retrieval. Table rows are kept intact; smaller tables stay together, while larger tables are split on row boundaries with their header repeated.

Workspace results limit the number of chunks contributed by one document, merge adjacent chunks, and suppress near-duplicate sources. In conversation search, choose **Ask** for a focused question or **Summarize** for a broader summary. Long document summaries use map-reduce over the indexed excerpts. Answers include citations that open the source document at the cited page when available. Rate the answer with thumbs up/down; negative feedback can optionally include a reason.

Retrieval, embedding, generation, and end-to-end query latency are recorded as Micrometer metrics, along with candidate counts, retrieval distances, and estimated input/output token counts. Retrieval thresholds and hybrid search are configurable with `AI_RETRIEVAL_MAX_DISTANCE`, `AI_RETRIEVAL_FALLBACK_MAX_DISTANCE`, and `AI_RETRIEVAL_HYBRID`.

See [docs/ai-search.md](docs/ai-search.md) for Google Cloud setup and indexing details.

## Developer Platform & SSO

Organization admins manage API keys, service accounts, webhooks, verified domains, invite links and single sign-on under **Settings**. See [docs/developer-platform.md](docs/developer-platform.md) for scopes, webhook signature verification, DNS domain verification and Google/Microsoft SSO setup.

---

## 🗺️ Roadmap
For detailed future plans and pending features, please refer to [ROADMAP.md](ROADMAP.md).
