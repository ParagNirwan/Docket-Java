# Cost optimization

Run `PROJECT=docket-java ./scripts/cost-report.sh` monthly. It's read-only: it shows the
Cloud SQL tier, Google's Recommender output for idle or over-provisioned instances, and the bucket's size
and lifecycle.

## Cloud SQL sizing

Each backend pod opens up to 10 connections (HikariCP's default; set `spring.datasource.hikari.maximum-pool-size`
to change it), and the backend HPA runs 2–4 pods in production, so plan for about 40 connections.

| Stage | Tier | Why |
| :--- | :--- | :--- |
| Dev / staging | `db-g1-small` (shared core) or Enterprise `db-custom-1-3840` | Low, bursty load; shared core is fine without an SLA |
| Production, < 50 orgs | Enterprise `db-custom-2-7680` (2 vCPU, 7.5 GB) | pgvector HNSW indexes need memory to stay cached |
| Production, growing | Scale vCPU when sustained CPU > 60% or memory > 80% for a week | Follow the Recommender rather than guessing |

- **Memory matters more than CPU.** Vector search is fast only while the HNSW index fits in
  `shared_buffers`/page cache. A rough size is `document_chunks` rows × dimensions × 4 bytes × ~1.5.
  Check it with `SELECT pg_size_pretty(pg_relation_size('idx_chunks_embedding'))`.
- **Storage:** keep `storageAutoResize` on, with SSD. Disks can grow but never shrink, so start small.
- **High availability** (`REGIONAL`) doubles the instance cost. Use it in production only. Cross-region DR
  ([disaster-recovery.md](disaster-recovery.md)) adds one more replica of the same tier.
- **Committed use discounts:** once the production tier has been stable for 3 months, a 1-year CUD
  saves about 25%.
- Resize during a maintenance window: `gcloud sql instances patch docket-db --tier=db-custom-2-7680`
  restarts the instance (about a minute with HA).

## GCS

`scripts/gcs-lifecycle.json` already:
- deletes non-current object versions after 90 days (versioning exists for recovery, not archiving);
- aborts incomplete multipart uploads after 7 days;
- moves live objects to **Coldline** after 365 days.

There's deliberately no Nearline step at 30 or 90 days. Previews, downloads and the embedding re-index
job (`EmbeddingReindexJob`) read older files, and Nearline and Coldline charge per GB retrieved
with a minimum storage duration, so moving recently used files early can cost more than it saves.
Revisit this if `gcloud storage du` shows most bytes are older than a year and rarely read.

Objects under a Compliance retention lock carry a temporary hold. Lifecycle can still change
their storage class but never deletes them.

## Compute (GKE Autopilot)

Autopilot bills pod requests, not nodes, so requests are the cost lever:
- **Backend:** `1536Mi`/`250m` requests (JVM heap plus Tika parsing headroom). Lower the memory request only
  after checking `container/memory/used_bytes` p99 over a week. OOM kills during PDF parsing are
  the failure mode.
- **Frontend:** nginx at `64Mi`/`100m` is already minimal.
- **HPAs** (backend 2–4 in production, frontend 2–4) scale down after 5 minutes of low CPU. The minimum of 2
  backend pods is for availability, not load.

## AI

- Vertex AI is the main variable cost. Plan limits (`docs/billing.md`) cap AI questions per
  organization each month when billing is on.
- Summaries, classification and change summaries each cost one or more Gemini calls per document.
  Organizations can switch summaries and classification off under **Settings → AI**.
- The `ai-evaluation` CI job calls Vertex on every PR. Restrict it to `main` if that becomes significant.
