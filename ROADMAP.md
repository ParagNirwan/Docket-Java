# Roadmap

## Phase 0: Production Readiness (P1 — Must Do First)

These are critical for production deployment. Blockers for going live.

### Observability & Reliability
- **#112** Document and verify production backup recovery (P1, governance, infra)
  - Document PITR (Point-in-Time Recovery) procedure for PostgreSQL
  - Quarterly restore test to staging; verify RTO/RPO
  - Document GCS bucket versioning + lifecycle for document storage

### AI Search Quality
- **#114** Build a repeatable AI search quality evaluation suite (P2, ai, ai-search)
  - Golden dataset: 50–100 Q&A pairs per tenant archetype (legal, finance, ops)
  - Metrics: citation precision/recall, answer correctness (LLM-as-judge), latency
  - CI gate: regression detection on retrieval + generation quality

### Security Hardening
- **#124** Refactor Public API: Service account authentication, key last-used tracking, and standard security filter (P2, platform, security)
  - Service account JWT (distinct from user JWT), scoped permissions
  - API key last-used timestamp + rotation reminders
  - Shared `SecurityFilterChain` for `/api/public/**` and `/api/internal/**`

---

## Phase 1: Enterprise Foundations (P2 — Core Platform)

### Notifications & Comms (Epic #97)
- **#123** Build Frontend UI for Phase 8 Enterprise features (P2, frontend)
  - SSO configuration (Google/Microsoft), domain verification, invite links
  - Developer settings: API keys, webhooks, service accounts
  - Insights dashboard: storage, AI usage, audit log summary
- Notifications: email digests, in-app notification center, Slack/webhook integration

### Billing & Subscription (Epic #97)
- **#89** Billing and subscription plans (P3, platform)
  - Stripe integration: checkout, customer portal, subscription webhooks
  - Plans: Free / Pro / Enterprise with AI query limits, storage quotas, retention days
  - Owner-only billing management page

### Governance & Security (Epic #95)
- **#95** Governance & security hardening (epic)
  - Audit log: immutable, queryable, export (CSV/JSON)
  - Data residency: per-tenant region selection (EU/US)
  - Encryption: envelope encryption for document storage keys
  - Vulnerability scanning: Trivy in CI, Dependabot alerts

### Organization Members, Invites & Roles (Epic #93)
- **#60** Domain-based auto-join (verified email domains) (P3, members)
  - Verify domain ownership via TXT record
  - Auto-join on email verification for verified domains
- Custom roles: granular permissions beyond Owner/Admin/Member/Viewer
- Invite links: reusable, expiring, role-scoped

### Archive Locks & Retention Policies (Epic #92)
- **#54** GCS bucket-level retention / object holds for tamper-proof locks (P3, retention, infra)
  - Mirror app-level retention locks and legal holds to Cloud Storage via per-object `temporaryHold`
  - No-op implementation in `LocalFileSystemStorageService`
- Retention policy UI: create/edit/delete, dry-run preview, compliance reports

### Document Management (Epic #94)
- Version comparison: side-by-side diff for text/PDF
- Bulk operations: move, tag, retention assign, download zip

---

## Phase 2: AI Beyond Search (Epic #96)

### Document Intelligence
- **#121** Automate background AI classification, key-field extraction, and tag generation on document indexing (P2, ai, documents)
  - Classify: contract, invoice, policy, correspondence, etc.
  - Extract: parties, dates, amounts, clauses, entities
  - Auto-tag: suggested tags from content + existing taxonomy
- **#84** AI: compare two documents or versions (P3, ai)
  - Redline diff with semantic change summary
  - Highlight added/removed/changed clauses

### AI Search Quality (Epic #91)
- **#114** AI search quality evaluation suite (see Phase 0)
- Hybrid retrieval tuning: per-tenant distance thresholds, rerank weight
- Conversation search: multi-turn context, follow-up handling
- Summarization quality: map-reduce for long docs, citation coverage

---

## Phase 3: Platform Scale & Polish (P3 — Growth)

### Developer Experience
- **#115** Align developer docs and root scripts with CI (P2, platform, documentation)
  - `README.md` matches CI pipeline (build, test, deploy steps)
  - `start.ps1` / `start.bat` / `npm start` all work identically
  - Document local dev vs. prod config differences
- Public API docs: OpenAPI spec, SDK examples (TypeScript, Python)

### Infrastructure
- GKE Autopilot tuning: HPA configs, pod disruption budgets
- Cost optimization: Cloud SQL instance sizing, GCS lifecycle rules
- Disaster recovery: cross-region read replica, failover runbook

---

## Quick Reference: Issue Priority Map

| Priority | Issues | Phase |
|----------|--------|-------|
| **P1** | #112 | Phase 0 |
| **P2** | #124, #123, #121, #115, #114, #97, #96, #95, #93, #92, #94 | Phase 1–2 |
| **P3** | #89, #84, #60, #54, #46 | Phase 3 |

---

## Notes

- **Epic issues** (#91–#97) are tracking umbrellas; work happens in their child issues.
- **Security fixes applied:** refresh token grace window ↓, SSO `azp` check, HKDF webhook keys, access token out of localStorage.