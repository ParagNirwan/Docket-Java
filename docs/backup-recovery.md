# Production Backup & Recovery

This document describes the backup and recovery procedures for Docket's production infrastructure. It covers PostgreSQL (Cloud SQL) point-in-time recovery, GCS bucket versioning and lifecycle for document storage, and the quarterly restore test process to verify RTO/RPO targets.

**Audience:** Platform engineers, SREs, incident responders  
**Review cadence:** Quarterly (aligned with restore test) or after any infrastructure change

---

## 1. Architecture Overview

| Component | Technology | Backup Mechanism | RPO Target | RTO Target |
|-----------|------------|------------------|------------|------------|
| **Primary Database** | Cloud SQL PostgreSQL 16 (pgvector) | Automated backups + PITR (Point-in-Time Recovery) | ≤ 5 min | ≤ 30 min |
| **Document Storage** | GCS bucket `docket-java-uploads` | Object versioning + lifecycle rules | 0 (versioned) | ≤ 15 min |
| **Application Config** | K8s ConfigMaps / SecretManager | GitOps (infra repo) + Secret Manager versioning | N/A | ≤ 10 min |

**Key principle:** Database and object storage are independent failure domains. Recovery procedures treat them separately but coordinate timing for full-system restore.

---

## 2. PostgreSQL (Cloud SQL) — PITR Procedure

### 2.1 Backup Configuration (Current State)

Cloud SQL automated backups are enabled on the `docket-db` instance with:

- **Backup window:** 03:00–04:00 UTC (configurable via `gcloud sql instances patch`)
- **Retention:** 30 days of automated backups + 1 year of on-demand backups
- **PITR:** Enabled (transaction log retention = 7 days by default)
- **Region:** `europe-west3` (same as GKE cluster)

```bash
# Verify current backup configuration
gcloud sql instances describe docket-db \
  --project=docket-java \
  --format="value(settings.backupConfiguration)"
```

Expected output includes:
```
enabled: true
startTime: "03:00"
pointInTimeRecoveryEnabled: true
backupRetentionSettings:
  retainedBackups: 30
transactionLogRetentionDays: "7"
```

### 2.2 Recovery Scenarios

| Scenario | Procedure | Estimated RTO |
|----------|-----------|---------------|
| **Full instance failure** (region outage) | Create new instance from latest backup + PITR to target timestamp | 20–30 min |
| **Accidental DROP/TRUNCATE** | Clone instance to recovery point before incident | 10–15 min |
| **Corrupted data** (bad deploy) | PITR to pre-deploy timestamp | 10–15 min |
| **Single table recovery** | Export from clone → import to production | 15–25 min |

### 2.3 Step-by-Step: Full Instance Recovery (PITR)

**Prerequisites:**
- `gcloud` authenticated with `roles/cloudsql.admin` on `docket-java`
- Target recovery timestamp identified (from incident timeline)
- Staging namespace available in GKE (`docket-staging`)

```bash
# 1. Identify the recovery target timestamp (UTC)
# Example: "2026-11-15 14:23:00" — just before the bad deployment
RECOVERY_TIMESTAMP="2026-11-15T14:23:00Z"

# 2. Create a clone instance with PITR to that timestamp
# This creates a new Cloud SQL instance; original is untouched
gcloud sql instances clone docket-db docket-db-recovery-$(date +%s) \
  --project=docket-java \
  --point-in-time="${RECOVERY_TIMESTAMP}" \
  --region=europe-west3

# 3. Wait for clone to become RUNNABLE (poll every 30s)
CLONE_NAME="docket-db-recovery-$(date +%s)"
while true; do
  STATE=$(gcloud sql instances describe "${CLONE_NAME}" --project=docket-java --format="value(state)")
  [ "${STATE}" = "RUNNABLE" ] && break
  echo "Waiting for clone... (state: ${STATE})"
  sleep 30
done

# 4. Verify data integrity on the clone
# Connect via Cloud SQL Proxy or authorized network
# Run sanity queries: row counts, critical table checksums
# Example:
# psql "host=/cloudsql/docket-java:europe-west3:${CLONE_NAME} user=docket dbname=docketdb" \
#   -c "SELECT count(*) FROM documents; SELECT count(*) FROM tenants;"

# 5. Update staging deployment to point to clone
# Edit k8s staging overlay or use kubectl patch
kubectl patch secret spring-datasource-password \
  -n docket-staging \
  -p="$(gcloud sql instances describe ${CLONE_NAME} --project=docket-java --format='value(ipAddresses[0].ipAddress)')"
# Note: In practice, update the SecretProviderClass or ConfigMap with new host

# 6. Run staging smoke tests
# - Health endpoints
# - Critical user flows (login, document upload, search)
# - Verify audit log continuity

# 7. If staging validates: promote clone to production
# Option A: Rename instances (requires brief downtime)
# Option B: Update production SecretProviderClass to new host (preferred, zero-downtime)
#   Update k8s/base/secret-provider-class.yaml with new instance connection name
#   Apply via kustomize: kubectl apply -k k8s/prod

# 8. Clean up old instance after verification period (48h)
# gcloud sql instances delete docket-db --project=docket-java
```

### 2.4 Step-by-Step: Single Table Recovery

```bash
# 1. Clone to recovery point (same as above)
# 2. Export specific table from clone
gcloud sql export csv docket-db-recovery-<ts> \
  gs://docket-java-backups/table-recovery/documents_$(date +%s).csv \
  --database=docketdb \
  --query="SELECT * FROM documents WHERE tenant_id = 'target-tenant'" \
  --project=docket-java

# 3. Import to production (truncate + load, or upsert)
# Use a staging table to avoid locks, then swap
psql "host=/cloudsql/docket-java:europe-west3:docket-db user=docket dbname=docketdb" <<'SQL'
CREATE TEMP TABLE documents_recovery (LIKE documents INCLUDING ALL);
\copy documents_recovery FROM PROGRAM 'gsutil cat gs://docket-java-backups/table-recovery/documents_<ts>.csv' WITH CSV HEADER;
-- Validate row counts, constraints
-- Then upsert or replace
SQL
```

### 2.5 Verification Checklist (Post-Recovery)

- [ ] Row counts match pre-incident baseline (per tenant)
- [ ] Foreign key constraints valid (`SELECT * FROM pg_constraint WHERE conrelid = 'documents'::regclass`)
- [ ] Indexes present and usable (`\di documents_*`)
- [ ] pgvector extension functional (`SELECT * FROM document_chunks LIMIT 1`)
- [ ] Application health checks pass (`/actuator/health/readiness`)
- [ ] Audit log continuity verified (no gaps in `audit_events`)

---

## 3. GCS Bucket — Versioning & Lifecycle

### 3.1 Current Configuration

Bucket: `docket-java-uploads` (region: `europe-west3`)

```bash
# Verify versioning and lifecycle
gsutil versioning get gs://docket-java-uploads
gsutil lifecycle get gs://docket-java-uploads
```

**Required state:**
- **Versioning:** `Enabled` — every overwrite/delete creates a new version
- **Lifecycle rules:**
  1. **Delete noncurrent versions after 90 days** (compliance with retention policies)
  2. **Abort incomplete multipart uploads after 7 days**
  3. **Transition to Coldline after 365 days** (cost optimization)

### 3.2 Lifecycle Rule Definition (Terraform-equivalent)

```json
{
  "lifecycle": {
    "rule": [
      {
        "action": { "type": "Delete" },
        "condition": {
          "age": 90,
          "isLive": false,
          "matchesStorageClass": ["STANDARD", "NEARLINE", "COLDLINE"]
        }
      },
      {
        "action": { "type": "AbortIncompleteMultipartUpload" },
        "condition": { "age": 7 }
      },
      {
        "action": { "type": "SetStorageClass", "storageClass": "COLDLINE" },
        "condition": { "age": 365, "isLive": true }
      }
    ]
  }
}
```

**Apply/update:**
```bash
cat > /tmp/lifecycle.json <<'EOF'
{ "rule": [ ... ] }
EOF
gsutil lifecycle set /tmp/lifecycle.json gs://docket-java-uploads
```

### 3.3 Recovery Procedures

| Scenario | Procedure | RTO |
|----------|-----------|-----|
| **Accidental object delete** | `gsutil cp gs://docket-java-uploads/<object>#<version> gs://docket-java-uploads/<object>` | < 5 min |
| **Accidental object overwrite** | List versions → copy desired version to live | < 5 min |
| **Bucket-wide corruption** | Restore from versioned objects (scripted) | 15–30 min |
| **Malware-infected object** | Delete live version; previous clean version becomes live | < 5 min |

**Single object restore example:**
```bash
# 1. List all versions of an object
gsutil ls -a gs://docket-java-uploads/tenants/abc123/documents/doc-456.pdf

# Output:
# gs://docket-java-uploads/tenants/abc123/documents/doc-456.pdf#1731686400000000  (live)
# gs://docket-java-uploads/tenants/abc123/documents/doc-456.pdf#1731600000000000  (previous)

# 2. Restore previous version
gsutil cp gs://docket-java-uploads/tenants/abc123/documents/doc-456.pdf#1731600000000000 \
    gs://docket-java-uploads/tenants/abc123/documents/doc-456.pdf
```

**Bulk restore script (bucket-wide):**
```bash
#!/usr/bin/env bash
# restore-bucket.sh — restore all objects to a point in time
# Usage: restore-bucket.sh <target-timestamp-iso8601>

TARGET_TS="$1"
BUCKET="docket-java-uploads"

# For each object, find the version closest to but not after TARGET_TS
gsutil ls -r -a "gs://${BUCKET}/**" | while read -r VERSIONED_OBJECT; do
  # Extract object path and generation
  OBJ_PATH=$(echo "${VERSIONED_OBJECT}" | cut -d'#' -f1)
  GENERATION=$(echo "${VERSIONED_OBJECT}" | cut -d'#' -f2)
  
  # Convert generation to timestamp (generation = microseconds since epoch)
  GEN_TS=$(date -d "@$((GENERATION / 1000000))" -u +"%Y-%m-%dT%H:%M:%SZ")
  
  if [[ "${GEN_TS}" < "${TARGET_TS}" ]]; then
    # This version is at or before target; check if it's the latest such version
    # (In practice, use gsutil ls -l to get all versions sorted, then pick)
    echo "Candidate: ${OBJ_PATH}#${GENERATION} (${GEN_TS})"
  fi
done
# Note: Production script should use gsutil -m for parallelism and proper sorting
```

---

## 4. Quarterly Restore Test

### 4.1 Purpose

Validate that:
1. **RTO/RPO targets are met** — measure actual recovery time
2. **Procedures work end-to-end** — including DNS, secrets, networking
3. **Data integrity holds** — no silent corruption
4. **Team readiness** — runbook is current, access works

### 4.2 Schedule & Ownership

| Quarter | Date (approx) | Owner | Reviewer |
|---------|---------------|-------|----------|
| Q1 | Jan 15 | Platform Lead | SRE Lead |
| Q2 | Apr 15 | SRE Lead | Platform Lead |
| Q3 | Jul 15 | Platform Lead | SRE Lead |
| Q4 | Oct 15 | SRE Lead | Platform Lead |

**Blockers:** Test must complete before any production deploy that week.

### 4.3 Test Scope

**Full-system restore to staging namespace (`docket-staging`):**

1. **Database:** Clone production Cloud SQL to recovery point (T-24h)
2. **Storage:** Restore GCS bucket objects to same point-in-time (using versioning)
3. **Config:** Deploy staging with production ConfigMaps/Secrets (redacted)
4. **Networking:** Verify staging ingress, Cloud SQL Proxy, GCS access
5. **Application:** Run automated smoke test suite
6. **Measure:** Record RTO (start to smoke-test pass), verify RPO (data loss window)

### 4.4 Execution Runbook

```bash
#!/usr/bin/env bash
# quarterly-restore-test.sh
# Run from a workstation with gcloud, kubectl, psql, gsutil authenticated

set -euo pipefail

TEST_ID="restore-test-$(date +%Y%m%d-%H%M)"
RECOVERY_POINT="$(date -d '24 hours ago' -u +'%Y-%m-%dT%H:%M:%SZ')"
NAMESPACE="docket-staging"
LOG_FILE="/tmp/${TEST_ID}.log"

exec > >(tee -a "${LOG_FILE}") 2>&1

echo "=== Quarterly Restore Test: ${TEST_ID} ==="
echo "Recovery point: ${RECOVERY_POINT}"
echo "Namespace: ${NAMESPACE}"
echo "Log: ${LOG_FILE}"
echo

START_TIME=$(date +%s)

# --- 1. Database clone ---
echo "[1/6] Cloning Cloud SQL instance to ${RECOVERY_POINT}..."
CLONE_NAME="docket-db-${TEST_ID}"
gcloud sql instances clone docket-db "${CLONE_NAME}" \
  --project=docket-java \
  --point-in-time="${RECOVERY_POINT}" \
  --region=europe-west3

# Wait for RUNNABLE
while true; do
  STATE=$(gcloud sql instances describe "${CLONE_NAME}" --project=docket-java --format="value(state)")
  [ "${STATE}" = "RUNNABLE" ] && break
  echo "  Waiting for clone... (${STATE})"
  sleep 30
done
DB_READY_TIME=$(date +%s)
echo "  Database ready in $((DB_READY_TIME - START_TIME))s"

# --- 2. GCS version restore (simulate by copying versions) ---
echo "[2/6] Restoring GCS bucket to ${RECOVERY_POINT}..."
# Create a test bucket with restored versions
TEST_BUCKET="docket-java-restore-${TEST_ID}"
gsutil mb -l europe-west3 -p docket-java "gs://${TEST_BUCKET}"
gsutil versioning set on "gs://${TEST_BUCKET}"

# Copy each object's version at/before recovery point
# (Production script uses parallel gsutil -m and proper version selection)
gsutil -m ls -r -a "gs://docket-java-uploads/**" | while read -r VERSIONED; do
  OBJ=$(echo "${VERSIONED}" | cut -d'#' -f1)
  GEN=$(echo "${VERSIONED}" | cut -d'#' -f2)
  GEN_TS=$(date -d "@$((GEN / 1000000))" -u +'%Y-%m-%dT%H:%M:%SZ')
  if [[ "${GEN_TS}" < "${RECOVERY_POINT}" ]]; then
    gsutil cp "${VERSIONED}" "gs://${TEST_BUCKET}/${OBJ#gs://docket-java-uploads/}"
  fi
done
GCS_READY_TIME=$(date +%s)
echo "  GCS ready in $((GCS_READY_TIME - DB_READY_TIME))s"

# --- 3. Deploy staging with restored resources ---
echo "[3/6] Deploying staging environment..."
# Update k8s staging overlay with:
# - Cloud SQL instance: ${CLONE_NAME}
# - GCS bucket: ${TEST_BUCKET}
# - Secrets: use staging SecretProviderClass (or copy prod secrets)
kubectl apply -k k8s/staging

# Wait for backend pod ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=backend -n "${NAMESPACE}" --timeout=300s
DEPLOY_READY_TIME=$(date +%s)
echo "  Deployment ready in $((DEPLOY_READY_TIME - GCS_READY_TIME))s"

# --- 4. Smoke tests ---
echo "[4/6] Running smoke tests..."
SMOKE_START=$(date +%s)

# Health checks
kubectl exec -n "${NAMESPACE}" deploy/backend -- curl -sf localhost:8080/actuator/health/readiness
kubectl exec -n "${NAMESPACE}" deploy/backend -- curl -sf localhost:8080/actuator/health/liveness

# Database connectivity + basic queries
kubectl exec -n "${NAMESPACE}" deploy/backend -- psql \
  "host=/cloudsql/docket-java:europe-west3:${CLONE_NAME} user=docket dbname=docketdb" \
  -c "SELECT count(*) FROM tenants; SELECT count(*) FROM documents;"

# Document upload/download round-trip
# (Use test script that exercises StorageService)

# Search index sanity
kubectl exec -n "${NAMESPACE}" deploy/backend -- curl -sf \
  -H "Authorization: Bearer <test-token>" \
  "http://localhost:8080/api/search?q=test&size=1"

SMOKE_END=$(date +%s)
echo "  Smoke tests passed in $((SMOKE_END - SMOKE_START))s"

# --- 5. Measure RTO/RPO ---
TOTAL_RTO=$((SMOKE_END - START_TIME))
echo
echo "=== RESULTS ==="
echo "Total RTO: ${TOTAL_RTO}s ($((TOTAL_RTO / 60))m $((TOTAL_RTO % 60))s)"
echo "Target RTO: ≤ 1800s (30 min)"
echo "RPO (recovery point): ${RECOVERY_POINT} (24h ago)"
echo "Target RPO: ≤ 300s (5 min) — actual depends on backup schedule"
echo

# --- 6. Cleanup ---
echo "[5/6] Cleaning up test resources..."
gcloud sql instances delete "${CLONE_NAME}" --project=docket-java --quiet
gsutil -m rm -r "gs://${TEST_BUCKET}"
kubectl delete namespace "${NAMESPACE}" --ignore-not-found=true

echo "[6/6] Test complete. Results logged to ${LOG_FILE}"
```

### 4.5 Success Criteria

| Metric | Target | Pass Condition |
|--------|--------|----------------|
| **RTO (full restore)** | ≤ 30 min | `TOTAL_RTO ≤ 1800` |
| **RPO (data loss)** | ≤ 5 min | Automated backup window + PITR log retention |
| **Data integrity** | 100% | Row counts match, FK constraints valid, search works |
| **Smoke tests** | 100% pass | All health checks, DB queries, upload/download, search |

**If any criterion fails:**
1. Document failure in incident tracker
2. Root cause analysis within 48h
3. Update runbook and re-test within 2 weeks

### 4.6 Test Report Template

```markdown
# Quarterly Restore Test Report — Q{X} 202{Y}

**Date:** 2026-XX-XX  
**Owner:** @platform-lead  
**Reviewer:** @sre-lead  
**Test ID:** restore-test-2026XXXX-XXXXXX

## Results

| Metric | Target | Actual | Pass/Fail |
|--------|--------|--------|-----------|
| RTO (full restore) | ≤ 30 min | XX min YYs | ✅/❌ |
| RPO (data loss window) | ≤ 5 min | ≤ 5 min (PITR) | ✅/❌ |
| Data integrity | 100% | Verified | ✅/❌ |
| Smoke tests | 100% pass | N/N passed | ✅/❌ |

## Observations
- [ ] Database clone time: XXs
- [ ] GCS restore time: XXs
- [ ] K8s deploy time: XXs
- [ ] Smoke test time: XXs

## Issues Found
1. **Issue:** Description
   **Impact:** 
   **Action:** 

## Action Items
- [ ] Update runbook section X
- [ ] Fix flaky smoke test Y
- [ ] Investigate clone time regression

## Sign-off
**Owner:** _________________ **Date:** __________  
**Reviewer:** _________________ **Date:** __________
```

---

## 5. Access & Permissions

| Role | Cloud SQL | GCS Bucket | K8s Staging | Secret Manager |
|------|-----------|------------|-------------|----------------|
| Platform Lead | `cloudsql.admin` | `storage.admin` | `admin` | `secretmanager.admin` |
| SRE Lead | `cloudsql.admin` | `storage.admin` | `admin` | `secretmanager.admin` |
| On-call Engineer | `cloudsql.instanceUser` | `storage.objectAdmin` | `edit` | `secretmanager.secretAccessor` |
| CI/CD Pipeline | `cloudsql.client` | `storage.objectCreator` | `edit` (deploy only) | `secretmanager.secretAccessor` |

**Break-glass procedure:** If primary accounts unavailable, project owners can grant temporary `cloudsql.admin`/`storage.admin` via IAM condition with 4-hour expiry.

---

## 6. Monitoring & Alerting

### 6.1 Backup Health Alerts (Configure in Cloud Monitoring)

| Alert | Condition | Severity | Runbook |
|-------|-----------|----------|---------|
| `CloudSQLBackupFailed` | Automated backup status ≠ SUCCESSFUL | Critical | Check Cloud SQL logs, retry manually |
| `CloudSQLPITRDisabled` | `pointInTimeRecoveryEnabled` = false | Critical | Re-enable PITR immediately |
| `GCSBackupBucketMissing` | `docket-java-uploads` versioning = Suspended | Warning | Re-enable versioning |
| `GCSBackupLifecycleStale` | Lifecycle rule not updated in 90 days | Warning | Review and update lifecycle |

### 6.2 Dashboard (Grafana/Cloud Monitoring)

Key panels:
- **Backup success rate** (7d, 30d)
- **PITR log retention** (days remaining)
- **GCS version count** (trend)
- **Last restore test date** + **RTO trend**

---

## 7. Runbook Quick Reference

### 7.1 Incident Response Flow

```
INCIDENT DETECTED
       │
       ▼
Classify: DB corruption? Object loss? Full outage?
       │
       ├─► DB: Clone + PITR (Section 2.3) → Verify → Promote
       │
       ├─► Object: gsutil version restore (Section 3.3) → Verify
       │
       └─► Full: Execute quarterly test runbook (Section 4.4) with production target
       │
       ▼
POST-INCIDENT: Update runbook, schedule ad-hoc restore test if gaps found
```

### 7.2 Useful Commands Cheat Sheet

```bash
# Cloud SQL
gcloud sql instances list --project=docket-java
gcloud sql instances describe docket-db --project=docket-java
gcloud sql backups list --instance=docket-db --project=docket-java
gcloud sql operations list --instance=docket-db --project=docket-java --filter="operationType=CLONE"

# GCS
gsutil versioning get gs://docket-java-uploads
gsutil lifecycle get gs://docket-java-uploads
gsutil ls -a gs://docket-java-uploads/path/to/object
gsutil cp gs://bucket/obj#GEN gs://bucket/obj

# K8s
kubectl get secret -n docket
kubectl describe secretproviderclass docket-secrets-provider -n docket
kubectl logs -n docket deploy/backend -c cloud-sql-proxy
```

---

## 8. Change Log

| Date | Author | Change |
|------|--------|--------|
| 2026-11-17 | Platform Team | Initial version for #112 |

---

## 9. Related Documents

- [Retention Policies](retention.md) — Application-level retention that interacts with GCS lifecycle
- [GCS Malware Scanning](gcs-malware-scanning.md) — Scanner that may quarantine objects
- [Email Setup](email.md) — Notification configuration for backup alerts
- [ROADMAP.md](../ROADMAP.md) — Issue #112 tracking

---

*This document is maintained by the Platform team. For questions or updates, contact #platform-oncall.*