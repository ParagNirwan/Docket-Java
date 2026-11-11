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
│       └── db/migration/       # Flyway schema versions (V1–V20)
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
*Minimum required: `POSTGRES_PASSWORD` and `JWT_SECRET`.*

### 2. Launching the Platform
The easiest way to start is using the provided npm scripts:

| Action | Command | Description |
| :--- | :--- | :--- |
| **Full Start** | `npm start` | Builds and starts all services (DB, Backend, Frontend, Mailpit) |
| **Quick Up** | `npm run up` | Starts services without rebuilding images |
| **Stop** | `npm run down` | Stops and removes containers |
| **Logs** | `npm run logs` | Tail logs for all services |

### 3. Accessing Services
| Service | URL | Note |
| :--- | :--- | :--- |
| **Frontend** | `http://localhost:80` | Main Application UI |
| **Backend API** | `http://localhost:8080` | REST API |
| **Mailpit** | `http://localhost:8025` | Local email catcher (view outgoing emails here) |

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

## AI Search

AI search is available when the Spring `gcp` profile is active. Documents are extracted, split into heading-aware chunks (approximately 500 tokens), embedded, and indexed for hybrid vector and keyword retrieval. Table rows are kept intact; smaller tables stay together, while larger tables are split on row boundaries with their header repeated.

Workspace results limit the number of chunks contributed by one document, merge adjacent chunks, and suppress near-duplicate sources. In conversation search, choose **Ask** for a focused question or **Summarize** for a broader summary. Long document summaries use map-reduce over the indexed excerpts. Answers include citations that open the source document at the cited page when available. Rate the answer with thumbs up/down; negative feedback can optionally include a reason.

Retrieval, embedding, generation, and end-to-end query latency are recorded as Micrometer metrics, along with candidate counts, retrieval distances, and estimated input/output token counts. Retrieval thresholds and hybrid search are configurable with `AI_RETRIEVAL_MAX_DISTANCE`, `AI_RETRIEVAL_FALLBACK_MAX_DISTANCE`, and `AI_RETRIEVAL_HYBRID`.

See [docs/ai-search.md](docs/ai-search.md) for Google Cloud setup and indexing details.

---

## 🗺️ Roadmap
For detailed future plans and pending features, please refer to [ROADMAP.md](ROADMAP.md).
