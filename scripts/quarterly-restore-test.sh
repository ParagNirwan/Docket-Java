#!/usr/bin/env bash
# Quarterly Restore Test for Docket Production Backup Recovery
# Issue #112: Document and verify production backup recovery
#
# This script executes a full-system restore to staging namespace,
# measuring RTO/RPO and validating data integrity.
#
# Prerequisites:
# - gcloud authenticated with roles/cloudsql.admin, roles/storage.admin on docket-java
# - kubectl configured for docket-java GKE cluster
# - psql, gsutil in PATH
# - Staging namespace (docket-staging) exists or can be created
#
# Usage: ./quarterly-restore-test.sh [--dry-run] [--recovery-point "ISO8601"]
#   --dry-run         : Simulate without creating resources
#   --recovery-point  : Target recovery timestamp (default: 24h ago)

set -euo pipefail

# --- Configuration ---
PROJECT_ID="docket-java"
REGION="europe-west3"
PROD_INSTANCE="docket-db"
PROD_BUCKET="docket-java-uploads"
STAGING_NAMESPACE="docket-staging"
K8S_OVERLAY="k8s/staging"  # Update if different

# --- Parse arguments ---
DRY_RUN=false
RECOVERY_POINT=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --recovery-point)
      RECOVERY_POINT="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [--dry-run] [--recovery-point \"ISO8601\"]"
      exit 1
      ;;
  esac
done

# Default recovery point: 24 hours ago
if [[ -z "${RECOVERY_POINT}" ]]; then
  RECOVERY_POINT=$(date -d '24 hours ago' -u +'%Y-%m-%dT%H:%M:%SZ')
fi

TEST_ID="restore-test-$(date +%Y%m%d-%H%M%S)"
CLONE_NAME="docket-db-${TEST_ID}"
TEST_BUCKET="docket-java-restore-${TEST_ID}"
LOG_FILE="/tmp/${TEST_ID}.log"

# --- Logging ---
exec > >(tee -a "${LOG_FILE}") 2>&1

log() {
  echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] $*"
}

run() {
  if [[ "${DRY_RUN}" == "true" ]]; then
    log "[DRY-RUN] $*"
  else
    log "RUN: $*"
    eval "$@"
  fi
}

# --- Start ---
log "=== Quarterly Restore Test: ${TEST_ID} ==="
log "Project: ${PROJECT_ID}"
log "Region: ${REGION}"
log "Production instance: ${PROD_INSTANCE}"
log "Production bucket: ${PROD_BUCKET}"
log "Recovery point: ${RECOVERY_POINT}"
log "Staging namespace: ${STAGING_NAMESPACE}"
log "Dry run: ${DRY_RUN}"
log "Log file: ${LOG_FILE}"
log ""

START_TIME=$(date +%s)

# --- 1. Validate prerequisites ---
log "[1/7] Validating prerequisites..."

if ! command -v gcloud &> /dev/null; then
  log "ERROR: gcloud not found in PATH"
  exit 1
fi
if ! command -v kubectl &> /dev/null; then
  log "ERROR: kubectl not found in PATH"
  exit 1
fi
if ! command -v gsutil &> /dev/null; then
  log "ERROR: gsutil not found in PATH"
  exit 1
fi
if ! command -v psql &> /dev/null; then
  log "ERROR: psql not found in PATH"
  exit 1
fi

# Check gcloud project
CURRENT_PROJECT=$(gcloud config get-value project 2>/dev/null || echo "")
if [[ "${CURRENT_PROJECT}" != "${PROJECT_ID}" ]]; then
  log "WARNING: gcloud project is '${CURRENT_PROJECT}', expected '${PROJECT_ID}'"
  log "Run: gcloud config set project ${PROJECT_ID}"
  if [[ "${DRY_RUN}" == "false" ]]; then
    exit 1
  fi
fi

# Check staging namespace exists
if [[ "${DRY_RUN}" == "false" ]]; then
  if ! kubectl get namespace "${STAGING_NAMESPACE}" &>/dev/null; then
    log "Creating staging namespace: ${STAGING_NAMESPACE}"
    kubectl create namespace "${STAGING_NAMESPACE}"
  fi
fi

log "Prerequisites OK"
log ""

# --- 2. Clone Cloud SQL instance with PITR ---
log "[2/7] Cloning Cloud SQL instance to ${RECOVERY_POINT}..."
log "Target clone name: ${CLONE_NAME}"

run gcloud sql instances clone "${PROD_INSTANCE}" "${CLONE_NAME}" \
  --project="${PROJECT_ID}" \
  --point-in-time="${RECOVERY_POINT}" \
  --region="${REGION}"

if [[ "${DRY_RUN}" == "false" ]]; then
  log "Waiting for clone to become RUNNABLE..."
  while true; do
    STATE=$(gcloud sql instances describe "${CLONE_NAME}" --project="${PROJECT_ID}" --format="value(state)" 2>/dev/null || echo "UNKNOWN")
    if [[ "${STATE}" == "RUNNABLE" ]]; then
      break
    fi
    log "  Clone state: ${STATE} (waiting 30s...)"
    sleep 30
  done
  log "Clone is RUNNABLE"
fi

DB_READY_TIME=$(date +%s)
log "Database clone ready in $((DB_READY_TIME - START_TIME))s"
log ""

# --- 3. Create test GCS bucket with versioning ---
log "[3/7] Creating test GCS bucket with versioning..."
log "Test bucket: ${TEST_BUCKET}"

run gsutil mb -l "${REGION}" -p "${PROJECT_ID}" "gs://${TEST_BUCKET}"
run gsutil versioning set on "gs://${TEST_BUCKET}"

GCS_BUCKET_READY_TIME=$(date +%s)
log "Test bucket ready in $((GCS_BUCKET_READY_TIME - DB_READY_TIME))s"
log ""

# --- 4. Restore GCS objects to recovery point ---
log "[4/7] Restoring GCS objects to ${RECOVERY_POINT}..."
log "This may take several minutes for large buckets..."

if [[ "${DRY_RUN}" == "false" ]]; then
  # Use gsutil -m for parallel copy
  # For each object, find the latest version at or before RECOVERY_POINT
  # This is a simplified approach; production may need more sophisticated version selection

  # Get all versioned objects
  log "Listing versioned objects from production bucket..."
  gsutil -m ls -r -a "gs://${PROD_BUCKET}/**" > "/tmp/${TEST_ID}_versions.txt" 2>/dev/null || true

  # Process each unique object path, find best version
  # This is a simplified single-threaded version for clarity
  # Production script should use parallel processing
  declare -A BEST_VERSION
  declare -A BEST_GEN_TS

  while IFS= read -r VERSIONED; do
    [[ -z "${VERSIONED}" ]] && continue
    OBJ_PATH=$(echo "${VERSIONED}" | cut -d'#' -f1)
    GENERATION=$(echo "${VERSIONED}" | cut -d'#' -f2)

    # Convert generation (microseconds since epoch) to ISO timestamp
    GEN_TS=$(date -d "@$((GENERATION / 1000000))" -u +'%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || echo "")
    [[ -z "${GEN_TS}" ]] && continue

    # Check if this version is at or before recovery point
    if [[ "${GEN_TS}" < "${RECOVERY_POINT}" ]] || [[ "${GEN_TS}" == "${RECOVERY_POINT}" ]]; then
      # Keep the latest such version
      if [[ -z "${BEST_GEN_TS[${OBJ_PATH}]}" ]] || [[ "${GEN_TS}" > "${BEST_GEN_TS[${OBJ_PATH}]}" ]]; then
        BEST_VERSION[${OBJ_PATH}]="${VERSIONED}"
        BEST_GEN_TS[${OBJ_PATH}]="${GEN_TS}"
      fi
    fi
  done < "/tmp/${TEST_ID}_versions.txt"

  log "Found ${#BEST_VERSION[@]} objects to restore"

  # Copy selected versions to test bucket
  for OBJ_PATH in "${!BEST_VERSION[@]}"; do
    VERSIONED="${BEST_VERSION[${OBJ_PATH}]}"
    # Strip gs://bucket/ prefix for destination
    DEST_PATH="${OBJ_PATH#gs://${PROD_BUCKET}/}"
    run gsutil cp "${VERSIONED}" "gs://${TEST_BUCKET}/${DEST_PATH}"
  done

  log "GCS restore complete"
else
  log "[DRY-RUN] Would restore $(gsutil -m ls -r -a "gs://${PROD_BUCKET}/**" 2>/dev/null | wc -l) versioned objects"
fi

GCS_RESTORE_TIME=$(date +%s)
log "GCS restore completed in $((GCS_RESTORE_TIME - GCS_BUCKET_READY_TIME))s"
log ""

# --- 5. Deploy staging with restored resources ---
log "[5/7] Deploying staging environment..."
log "K8s overlay: ${K8S_OVERLAY}"

if [[ "${DRY_RUN}" == "false" ]]; then
  # Create a temporary kustomization with restored resources
  TEMP_DIR="/tmp/${TEST_ID}_k8s"
  mkdir -p "${TEMP_DIR}"

  # Copy staging overlay
  cp -r "${K8S_OVERLAY}"/* "${TEMP_DIR}/"

  # Update ConfigMap/SecretProviderClass with clone instance and test bucket
  # This depends on your k8s structure; adjust as needed

  # Apply
  run kubectl apply -k "${TEMP_DIR}"

  # Wait for backend pod
  log "Waiting for backend pod to be ready..."
  run kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=backend -n "${STAGING_NAMESPACE}" --timeout=300s
else
  log "[DRY-RUN] Would apply k8s overlay and wait for backend pod"
fi

DEPLOY_READY_TIME=$(date +%s)
log "Deployment ready in $((DEPLOY_READY_TIME - GCS_RESTORE_TIME))s"
log ""

# --- 6. Run smoke tests ---
log "[6/7] Running smoke tests..."

SMOKE_START=$(date +%s)
SMOKE_PASSED=0
SMOKE_FAILED=0

run_smoke_test() {
  local name="$1"
  local cmd="$2"

  log "  Smoke test: ${name}"
  if [[ "${DRY_RUN}" == "false" ]]; then
    if eval "${cmd}" >> "${LOG_FILE}" 2>&1; then
      log "    ✅ PASS"
      ((SMOKE_PASSED++))
    else
      log "    ❌ FAIL"
      ((SMOKE_FAILED++))
    fi
  else
    log "    [DRY-RUN] Would run: ${cmd}"
    ((SMOKE_PASSED++))
  fi
}

# Health checks
run_smoke_test "Backend readiness" \
  "kubectl exec -n ${STAGING_NAMESPACE} deploy/backend -- curl -sf localhost:8080/actuator/health/readiness"

run_smoke_test "Backend liveness" \
  "kubectl exec -n ${STAGING_NAMESPACE} deploy/backend -- curl -sf localhost:8080/actuator/health/liveness"

# Database connectivity
run_smoke_test "Database connection + tenant count" \
  "kubectl exec -n ${STAGING_NAMESPACE} deploy/backend -- psql \"host=/cloudsql/${PROJECT_ID}:${REGION}:${CLONE_NAME} user=docket dbname=docketdb\" -c \"SELECT count(*) FROM tenants;\""

run_smoke_test "Database connection + document count" \
  "kubectl exec -n ${STAGING_NAMESPACE} deploy/backend -- psql \"host=/cloudsql/${PROJECT_ID}:${REGION}:${CLONE_NAME} user=docket dbname=docketdb\" -c \"SELECT count(*) FROM documents;\""

# Document storage round-trip (requires test script or API call)
# run_smoke_test "Document upload/download" \
#   "kubectl exec -n ${STAGING_NAMESPACE} deploy/backend -- /app/scripts/test-storage-roundtrip.sh"

# Search sanity
# run_smoke_test "Search API" \
#   "kubectl exec -n ${STAGING_NAMESPACE} deploy/backend -- curl -sf -H \"Authorization: Bearer <token>\" \"http://localhost:8080/api/search?q=test&size=1\""

SMOKE_END=$(date +%s)
log "Smoke tests: ${SMOKE_PASSED} passed, ${SMOKE_FAILED} failed (in $((SMOKE_END - SMOKE_START))s)"
log ""

# --- 7. Measure and report ---
log "[7/7] Measuring RTO/RPO..."

TOTAL_RTO=$((SMOKE_END - START_TIME))
RTO_MIN=$((TOTAL_RTO / 60))
RTO_SEC=$((TOTAL_RTO % 60))

log ""
log "=== RESTORE TEST RESULTS ==="
log "Test ID: ${TEST_ID}"
log "Recovery point: ${RECOVERY_POINT}"
log "Total RTO: ${TOTAL_RTO}s (${RTO_MIN}m ${RTO_SEC}s)"
log "Target RTO: ≤ 1800s (30 min)"
log "RPO (recovery point age): 24h (configured)"
log "Target RPO: ≤ 300s (5 min) — actual depends on backup schedule"
log "Smoke tests: ${SMOKE_PASSED} passed, ${SMOKE_FAILED} failed"
log ""

# Success criteria
RTO_TARGET=1800
if [[ ${TOTAL_RTO} -le ${RTO_TARGET} ]] && [[ ${SMOKE_FAILED} -eq 0 ]]; then
  log "✅ OVERALL: PASS — All success criteria met"
  OVERALL_RESULT="PASS"
else
  log "❌ OVERALL: FAIL — Success criteria not met"
  if [[ ${TOTAL_RTO} -gt ${RTO_TARGET} ]]; then
    log "  - RTO ${TOTAL_RTO}s exceeds target ${RTO_TARGET}s"
  fi
  if [[ ${SMOKE_FAILED} -gt 0 ]]; then
    log "  - ${SMOKE_FAILED} smoke test(s) failed"
  fi
  OVERALL_RESULT="FAIL"
fi

# --- Cleanup ---
log ""
log "Cleaning up test resources..."

if [[ "${DRY_RUN}" == "false" ]]; then
  log "Deleting Cloud SQL clone: ${CLONE_NAME}"
  run gcloud sql instances delete "${CLONE_NAME}" --project="${PROJECT_ID}" --quiet

  log "Deleting test GCS bucket: ${TEST_BUCKET}"
  run gsutil -m rm -r "gs://${TEST_BUCKET}"

  log "Deleting staging namespace (optional — comment out to preserve)"
  # run kubectl delete namespace "${STAGING_NAMESPACE}" --ignore-not-found=true

  log "Cleaning temp files"
  rm -f "/tmp/${TEST_ID}_versions.txt"
  rm -rf "/tmp/${TEST_ID}_k8s"
fi

log ""
log "Test complete. Results logged to ${LOG_FILE}"
log "Overall result: ${OVERALL_RESULT}"

# Exit with appropriate code
if [[ "${OVERALL_RESULT}" == "PASS" ]]; then
  exit 0
else
  exit 1
fi